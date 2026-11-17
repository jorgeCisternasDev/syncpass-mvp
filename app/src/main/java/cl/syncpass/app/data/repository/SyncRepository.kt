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
    data class Failure(val message: String) : SyncResult
}

/**
 * Mapeo local -> contrato remoto.
 *
 * eventoId y usuarioId son los ids locales del dispositivo. El backend usa
 * (eventoId, usuarioId) como clave idempotente del lote: reenviar el mismo
 * registro no lo duplica.
 */
fun AttendanceEntity.toRemote(): AsistenciaRemota = AsistenciaRemota(
    eventoId = eventId,
    usuarioId = participantId,
    timestamp = attendedAt,
    validado = true,
    latitud = latitude,
    longitud = longitude
)

fun SurveyEntity.toRemote(): EncuestaRemota = EncuestaRemota(
    eventoId = eventId,
    usuarioId = participantId,
    satisfaccion = satisfaction,
    utilidad = usefulness,
    comentario = comment.takeIf { it.isNotBlank() },
    fecha = submittedAt
)

class SyncRepository(
    private val database: AppDatabase,
    private val syncApi: SyncApi
) {
    private val attendanceDao = database.attendanceDao()
    private val surveyDao = database.surveyDao()

    fun observePendingCount(): Flow<Int> = combine(
        attendanceDao.observePendingSyncCount(),
        surveyDao.observePendingSyncCount()
    ) { attendance, surveys -> attendance + surveys }

    /**
     * Envia solo lo pendiente y lo marca como enviado tras la confirmacion.
     * Si falla la red no marca nada: el proximo intento reenvia lo mismo y el
     * backend responde de forma idempotente.
     */
    suspend fun sync(): SyncResult {
        val asistencias = attendanceDao.pendingForSync()
        val encuestas = surveyDao.pendingForSync()
        if (asistencias.isEmpty() && encuestas.isEmpty()) return SyncResult.NothingPending

        return try {
            val response = syncApi.subirLote(
                SincronizacionRequest(
                    asistencias = asistencias.map { it.toRemote() },
                    encuestas = encuestas.map { it.toRemote() }
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
            SyncResult.Failure("El servidor respondio ${error.code()}. Los registros siguen pendientes.")
        } catch (error: IOException) {
            SyncResult.Failure("Sin conexion con el servidor. Los registros siguen pendientes.")
        } catch (error: Exception) {
            SyncResult.Failure("No se pudo sincronizar: ${error.message ?: "error desconocido"}")
        }
    }
}
