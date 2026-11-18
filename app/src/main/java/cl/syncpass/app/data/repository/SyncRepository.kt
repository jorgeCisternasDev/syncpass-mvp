package cl.syncpass.app.data.repository

import androidx.room.withTransaction
import cl.syncpass.app.data.local.AppDatabase
import cl.syncpass.app.data.local.AttendanceEntity
import cl.syncpass.app.data.local.SurveyEntity
import cl.syncpass.app.data.remote.AsistenciaRemota
import cl.syncpass.app.data.remote.EncuestaRemota
import cl.syncpass.app.data.remote.SincronizacionRequest
import cl.syncpass.app.data.remote.SyncApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

sealed interface SyncResult {
    data object NothingPending : SyncResult
    data class Success(val accepted: Int, val sent: Int) : SyncResult
    /** [retryable] decide si el worker reintenta (red/5xx) o falla de forma definitiva. */
    data class Failure(
        val message: String,
        val retryable: Boolean = true,
        val rejected: Int = 0
    ) : SyncResult
}

/**
 * Mapeo local -> contrato remoto.
 *
 * eventoId y usuarioId siguen siendo los ids locales del dispositivo. Desde H3
 * viaja ademas `usuarioUuid` (opcional) para que el backend atribuya la fila a
 * un usuario real; si es null, Gson omite el campo y la fila queda sin
 * atribucion, como permite el contrato C.
 */
fun AttendanceEntity.toRemote(usuarioUuid: String? = null): AsistenciaRemota = AsistenciaRemota(
    eventoId = eventId,
    usuarioId = participantId,
    usuarioUuid = usuarioUuid,
    timestamp = attendedAt,
    validado = true,
    latitud = latitude,
    longitud = longitude
)

fun SurveyEntity.toRemote(usuarioUuid: String? = null): EncuestaRemota = EncuestaRemota(
    eventoId = eventId,
    usuarioId = participantId,
    usuarioUuid = usuarioUuid,
    satisfaccion = satisfaction,
    utilidad = usefulness,
    comentario = comment.takeIf { it.isNotBlank() },
    fecha = submittedAt
)

class SyncRepository(
    private val database: AppDatabase,
    private val syncApi: SyncApi,
    private val provisioningRepository: ProvisioningRepository
) {
    private val attendanceDao = database.attendanceDao()
    private val surveyDao = database.surveyDao()
    private val participantDao = database.participantDao()

    fun observePendingCount(): Flow<Int> = combine(
        attendanceDao.observePendingSyncCount(),
        surveyDao.observePendingSyncCount()
    ) { attendance, surveys -> attendance + surveys }

    /** Registros que el servidor rechazo y ya no se reintentan. */
    fun observeRejectedCount(): Flow<Int> = combine(
        attendanceDao.observeRejectedCount(),
        surveyDao.observeRejectedCount()
    ) { attendance, surveys -> attendance + surveys }

    /** Primer motivo de rechazo, para mostrarlo en Ajustes. */
    fun observeRejectionMessage(): Flow<String?> = combine(
        attendanceDao.observeRejectionMessage(),
        surveyDao.observeRejectionMessage()
    ) { attendance, surveys -> attendance ?: surveys }

    /** Borra los rechazados. Las encuestas se borran antes por la clave foranea. */
    suspend fun discardRejected(): Int = database.withTransaction {
        val surveys = surveyDao.deleteRejected()
        val attendance = attendanceDao.deleteRejected()
        surveys + attendance
    }

    /**
     * Sincronizacion completa de lo pendiente: aprovisiona primero y despues
     * envia el lote.
     *
     * 1. Cada participante sin `provisioned` se da de alta en la API y el UUID
     *    local viaja en el lote.
     * 2. Si el aprovisionamiento falla, no se envia el lote y no se marca nada.
     * 3. Tras el 2xx del lote, se marca lo enviado; si falla, sigue pendiente.
     */
    suspend fun syncCompleto(): SyncResult {
        try {
            provisioningRepository.provisionPending()
        } catch (error: HttpException) {
            return SyncResult.Failure(
                "No se pudo aprovisionar participantes (${error.code()}). " +
                    "Los registros siguen pendientes.",
                retryable = esReintentable(error.code())
            )
        } catch (error: IOException) {
            return SyncResult.Failure(
                "Sin conexion para aprovisionar participantes. Los registros siguen pendientes."
            )
        } catch (error: Exception) {
            return SyncResult.Failure(
                "No se pudo aprovisionar participantes: " +
                    (error.message ?: "error desconocido"),
                retryable = false
            )
        }

        val asistencias = attendanceDao.pendingForSync()
        val encuestas = surveyDao.pendingForSync()
        if (asistencias.isEmpty() && encuestas.isEmpty()) return SyncResult.NothingPending

        val participantIds = (asistencias.map { it.participantId } +
            encuestas.map { it.participantId }).distinct()
        val uuids = participantDao.findByIds(participantIds)
            .associate { it.id to it.usuarioUuid }

        return try {
            val response = syncApi.subirLote(
                SincronizacionRequest(
                    asistencias = asistencias.map {
                        it.toRemote(uuids[it.participantId])
                    },
                    encuestas = encuestas.map {
                        it.toRemote(uuids[it.participantId])
                    }
                )
            )
            database.withTransaction {
                if (asistencias.isNotEmpty()) {
                    attendanceDao.markSynced(asistencias.map { it.id })
                }
                if (encuestas.isNotEmpty()) {
                    surveyDao.markSynced(encuestas.map { it.id })
                }
            }
            SyncResult.Success(
                accepted = response.aceptados,
                sent = asistencias.size + encuestas.size
            )
        } catch (error: HttpException) {
            val rechazados = if (error.code() == 400 || error.code() == 409) {
                marcarRechazados(error, asistencias, encuestas)
            } else 0
            SyncResult.Failure(
                if (rechazados > 0) {
                    "El servidor rechazo $rechazados registro(s). Revisa el motivo y descartalos."
                } else {
                    "El servidor respondio ${error.code()}. Los registros siguen pendientes."
                },
                retryable = esReintentable(error.code()),
                rejected = rechazados
            )
        } catch (error: IOException) {
            SyncResult.Failure("Sin conexion con el servidor. Los registros siguen pendientes.")
        } catch (error: Exception) {
            SyncResult.Failure(
                "No se pudo sincronizar: ${error.message ?: "error desconocido"}",
                retryable = false
            )
        }
    }

    /** El worker llama a este alias desde la UI y desde WorkManager. */
    suspend fun sync(): SyncResult = syncCompleto()

    /**
     * Marca como rechazados solo los items que el servidor identifico en
     * `data.errores[]`. Si no hay detalle, marca todo lo pendiente para evitar
     * un reintento infinito.
     */
    private suspend fun marcarRechazados(
        error: HttpException,
        asistencias: List<AttendanceEntity>,
        encuestas: List<SurveyEntity>
    ): Int {
        val cuerpo = runCatching { error.response()?.errorBody()?.string() }
            .getOrNull()
            .orEmpty()
        val errores = runCatching {
            JSONObject(cuerpo).getJSONObject("data").getJSONArray("errores")
        }.getOrNull()

        val asistenciaIds = linkedSetOf<Long>()
        val encuestaIds = linkedSetOf<Long>()
        var motivo = "El servidor rechazo el lote."

        if (errores != null) {
            for (i in 0 until errores.length()) {
                val detalle = errores.optJSONObject(i) ?: continue
                val indice = detalle.optInt("indice", -1)
                val tipo = detalle.optString("tipo", "")
                val mensaje = detalle.optString("mensaje", "").ifBlank { motivo }
                motivo = mensaje
                when (tipo) {
                    "ASISTENCIA" -> if (indice in asistencias.indices) {
                        asistenciaIds += asistencias[indice].id
                    }

                    "ENCUESTA" -> if (indice in encuestas.indices) {
                        encuestaIds += encuestas[indice].id
                    }
                }
            }
        }

        val total = asistencias.size + encuestas.size
        if (asistenciaIds.isEmpty() && encuestaIds.isEmpty()) {
            attendanceDao.markAllPendingRejected(motivo)
            surveyDao.markAllPendingRejected(motivo)
            return total
        }

        if (asistenciaIds.isNotEmpty()) {
            attendanceDao.markRejected(asistenciaIds.toList(), motivo)
        }
        if (encuestaIds.isNotEmpty()) {
            surveyDao.markRejected(encuestaIds.toList(), motivo)
        }
        return asistenciaIds.size + encuestaIds.size
    }

    private fun esReintentable(code: Int): Boolean = code in 500..599
}
