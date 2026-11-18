package cl.syncpass.app.data.repository

import cl.syncpass.app.data.local.AppDatabase
import cl.syncpass.app.data.remote.ParticipanteApi
import cl.syncpass.app.data.remote.ParticipanteRequest
import java.util.UUID

data class ProvisioningReport(
    /** Participantes que la API confirmo con 200 y quedaron marcados. */
    val provisioned: Int,
    /** Participantes a los que se les aseguro UUID pero aun no se pueden aprovisionar. */
    val pending: Int
)

/**
 * Convierte cada participante local en un usuario del backend con rol
 * Asistente (POST api/participantes).
 *
 * Reglas de H3:
 * - el UUID se genera una sola vez y se persiste antes del POST, para que un
 *   reintento use el mismo identificador (idempotencia P-02);
 * - `provisioned = true` solo despues de una respuesta 2xx;
 * - los errores HTTP/red se propagan: el llamador conserva los pendientes.
 */
class ProvisioningRepository(
    private val database: AppDatabase,
    private val participanteApi: ParticipanteApi,
    private val newUuid: () -> String = { UUID.randomUUID().toString() }
) {
    private val participantDao = database.participantDao()

    suspend fun provisionPending(): ProvisioningReport {
        var provisioned = 0
        var pending = 0
        participantDao.pendingProvisioning().forEach { row ->
            val participant = row.participant
            val uuid = participant.usuarioUuid ?: newUuid().also { generated ->
                participantDao.update(participant.copy(usuarioUuid = generated))
            }

            val eventoId = row.eventRemoteId
            if (eventoId == null) {
                // Evento local sin equivalente remoto: no hay donde dar de alta.
                pending++
                return@forEach
            }

            participanteApi.registrar(
                ParticipanteRequest(
                    usuarioUuid = uuid,
                    eventoId = eventoId,
                    nombre = participant.name,
                    // El correo del participante no se envia: puede coincidir con
                    // un usuario existente y bloquear el lote (409 EMAIL_EN_USO).
                    email = null
                )
            )

            participantDao.update(
                participant.copy(usuarioUuid = uuid, provisioned = true)
            )
            provisioned++
        }
        return ProvisioningReport(provisioned = provisioned, pending = pending)
    }
}
