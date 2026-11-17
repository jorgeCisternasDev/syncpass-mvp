package cl.syncpass.app.data.repository

import android.database.sqlite.SQLiteConstraintException
import cl.syncpass.app.data.local.AttendanceDao
import cl.syncpass.app.data.local.EventDao
import cl.syncpass.app.data.local.ParticipantDao
import cl.syncpass.app.data.local.ParticipantEntity
import cl.syncpass.app.data.local.ParticipantStatusRow
import cl.syncpass.app.domain.DocumentRules
import kotlinx.coroutines.flow.Flow
import java.security.SecureRandom

sealed interface AddParticipantResult {
    data class Success(val participant: ParticipantEntity) : AddParticipantResult
    data class Invalid(val errors: Map<String, String>) : AddParticipantResult
    data class Conflict(val message: String) : AddParticipantResult
    data class Failure(val message: String) : AddParticipantResult
}

sealed interface DeleteParticipantResult {
    data object Success : DeleteParticipantResult
    data class Blocked(val message: String) : DeleteParticipantResult
    data class Failure(val message: String) : DeleteParticipantResult
}

class ParticipantRepository(
    private val eventDao: EventDao,
    private val participantDao: ParticipantDao,
    private val attendanceDao: AttendanceDao
) {
    private val random = SecureRandom()
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun observeByEvent(eventId: Long): Flow<List<ParticipantStatusRow>> =
        participantDao.observeByEvent(eventId)

    suspend fun getById(participantId: Long): ParticipantEntity? =
        participantDao.getById(participantId)

    suspend fun add(
        eventId: Long,
        name: String,
        document: String,
        email: String,
        isWalkIn: Boolean = false
    ): AddParticipantResult {
        val event = eventDao.getById(eventId)
            ?: return AddParticipantResult.Failure("El evento ya no existe.")

        val errors = linkedMapOf<String, String>()
        DocumentRules.validateName(name, "El nombre")?.let { errors["name"] = it }
        DocumentRules.validateDocument(document)?.let { errors["document"] = it }
        DocumentRules.validateEmail(email)?.let { errors["email"] = it }
        if (errors.isNotEmpty()) return AddParticipantResult.Invalid(errors)

        val cleanName = name.trim()
        val cleanDocument = document.trim()
        val normalized = DocumentRules.normalize(cleanDocument)
        val cleanEmail = email.trim().ifEmpty { null }

        if (event.capacity > 0) {
            val registered = participantDao.countByEvent(eventId)
            if (registered >= event.capacity) {
                return AddParticipantResult.Conflict(
                    "El evento alcanzó su aforo de ${event.capacity} personas."
                )
            }
        }

        if (participantDao.findByDocument(eventId, normalized) != null) {
            return AddParticipantResult.Conflict(
                "Ya existe un participante con el documento $cleanDocument en este evento."
            )
        }

        val existingCodes = participantDao.allCodes(eventId).toHashSet()
        repeat(8) {
            val code = generateCode(existingCodes)
            val entity = ParticipantEntity(
                eventId = eventId,
                code = code,
                name = cleanName,
                document = cleanDocument,
                documentNormalized = normalized,
                email = cleanEmail,
                isWalkIn = isWalkIn
            )
            try {
                val id = participantDao.insert(entity)
                return AddParticipantResult.Success(entity.copy(id = id))
            } catch (_: SQLiteConstraintException) {
                if (participantDao.findByDocument(eventId, normalized) != null) {
                    return AddParticipantResult.Conflict(
                        "Ya existe un participante con el documento $cleanDocument en este evento."
                    )
                }
            } catch (error: Exception) {
                return AddParticipantResult.Failure(
                    "No se pudo inscribir al participante: ${error.message ?: "error desconocido"}"
                )
            }
        }
        return AddParticipantResult.Failure("No se pudo generar un código único. Intenta nuevamente.")
    }

    private fun generateCode(existing: Set<String>): String {
        while (true) {
            val suffix = buildString {
                repeat(6) { append(alphabet[random.nextInt(alphabet.length)]) }
            }
            val code = "SP$suffix"
            if (code !in existing) return code
        }
    }

    suspend fun delete(participantId: Long): DeleteParticipantResult {
        val participant = participantDao.getById(participantId)
            ?: return DeleteParticipantResult.Failure("El participante ya no existe.")
        if (attendanceDao.getByParticipant(participantId) != null) {
            return DeleteParticipantResult.Blocked(
                "No se puede eliminar a ${participant.name}: ya tiene una acreditación registrada."
            )
        }
        return try {
            participantDao.delete(participant)
            DeleteParticipantResult.Success
        } catch (error: Exception) {
            DeleteParticipantResult.Failure(
                "No se pudo eliminar al participante: ${error.message ?: "error desconocido"}"
            )
        }
    }
}
