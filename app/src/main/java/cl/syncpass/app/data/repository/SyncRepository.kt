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
import retrofit2.HttpException
import java.io.IOException

sealed interface SyncResult {
    data object NothingPending : SyncResult
    data class Success(val accepted: Int, val sent: Int) : SyncResult
    /** [retryable] decide si el worker reintenta (red/5xx) o falla de forma definitiva. */
    data class Failure(val message: String, val retryable: Boolean = true) : SyncResult
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
            SyncResult.Failure(
                "El servidor respondio ${error.code()}. Los registros siguen pendientes.",
                retryable = esReintentable(error.code())
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

    private fun esReintentable(code: Int): Boolean = code in 500..599
}
