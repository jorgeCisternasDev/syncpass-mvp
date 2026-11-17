package cl.syncpass.app.data.repository

import cl.syncpass.app.data.local.AttendanceDao
import cl.syncpass.app.data.local.AttendanceEntity
import cl.syncpass.app.data.local.AttendanceMethod
import cl.syncpass.app.data.local.AttendanceRow
import cl.syncpass.app.data.local.EventDao
import cl.syncpass.app.data.local.ParticipantDao
import cl.syncpass.app.data.local.ParticipantEntity
import cl.syncpass.app.domain.AccreditationRules
import cl.syncpass.app.domain.AccreditationWindow
import cl.syncpass.app.domain.DocumentRules
import cl.syncpass.app.domain.QrCodec
import kotlinx.coroutines.flow.Flow
import java.util.Locale

sealed interface AccreditResult {
    data class Success(
        val participant: ParticipantEntity,
        val attendance: AttendanceEntity
    ) : AccreditResult

    data class Duplicate(
        val participant: ParticipantEntity,
        val attendedAt: Long
    ) : AccreditResult

    data class NeedsRegistration(
        val rawInput: String,
        val fromQr: Boolean
    ) : AccreditResult

    data class EventMismatch(val eventName: String?) : AccreditResult
    data class NotOpen(val opensAt: Long) : AccreditResult
    data class Closed(val closedAt: Long) : AccreditResult
    data class InvalidQr(val message: String) : AccreditResult
    data class InvalidInput(val message: String) : AccreditResult
    data class NotFound(val input: String) : AccreditResult
    data class Conflict(val message: String) : AccreditResult
    data class Failure(val message: String) : AccreditResult
}

class AccreditationRepository(
    private val eventDao: EventDao,
    private val participantDao: ParticipantDao,
    private val attendanceDao: AttendanceDao,
    private val participantRepository: ParticipantRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    fun observeAttendance(eventId: Long): Flow<List<AttendanceRow>> =
        attendanceDao.observeByEvent(eventId)

    suspend fun accredit(
        eventId: Long,
        rawInput: String,
        method: String,
        latitude: Double? = null,
        longitude: Double? = null
    ): AccreditResult {
        val event = eventDao.getById(eventId)
            ?: return AccreditResult.Failure("El evento ya no existe.")

        val now = nowProvider()
        when (val window = AccreditationRules.window(event, now)) {
            is AccreditationWindow.NotOpen -> return AccreditResult.NotOpen(window.opensAt)
            is AccreditationWindow.Closed -> return AccreditResult.Closed(window.closedAt)
            is AccreditationWindow.Open -> Unit
        }

        val input = rawInput.trim()
        if (input.isEmpty()) {
            return AccreditResult.InvalidInput("Escribe o escanea un código para acreditar.")
        }

        val payload = QrCodec.parse(input)
        if (payload == null && QrCodec.looksLikeQr(input)) {
            return AccreditResult.InvalidQr(
                "El código QR no es válido o no pertenece a SyncPass."
            )
        }
        if (payload != null && payload.eventId != eventId) {
            val otherEvent = eventDao.getById(payload.eventId)
            return AccreditResult.EventMismatch(otherEvent?.name)
        }

        val participant = if (payload != null) {
            participantDao.findByCode(eventId, payload.participantCode)
        } else {
            participantDao.findByCode(eventId, input.uppercase(Locale.ROOT))
                ?: participantDao.findByDocument(eventId, DocumentRules.normalize(input))
        }

        if (participant == null) {
            return if (event.allowWalkIn) {
                AccreditResult.NeedsRegistration(rawInput = input, fromQr = payload != null)
            } else {
                AccreditResult.NotFound(input)
            }
        }

        val existing = attendanceDao.getByParticipant(participant.id)
        if (existing != null) {
            return AccreditResult.Duplicate(participant, existing.attendedAt)
        }

        val attendance = AttendanceEntity(
            eventId = eventId,
            participantId = participant.id,
            attendedAt = now,
            method = method,
            latitude = latitude,
            longitude = longitude
        )
        return try {
            val id = attendanceDao.insert(attendance)
            AccreditResult.Success(participant, attendance.copy(id = id))
        } catch (_: Exception) {
            val duplicate = attendanceDao.getByParticipant(participant.id)
            if (duplicate != null) {
                AccreditResult.Duplicate(participant, duplicate.attendedAt)
            } else {
                AccreditResult.Failure("No se pudo registrar la acreditación. Intenta nuevamente.")
            }
        }
    }

    suspend fun registerWalkInAndAccredit(
        eventId: Long,
        name: String,
        document: String,
        email: String,
        latitude: Double? = null,
        longitude: Double? = null
    ): AccreditResult {
        val event = eventDao.getById(eventId)
            ?: return AccreditResult.Failure("El evento ya no existe.")
        val now = nowProvider()
        when (val window = AccreditationRules.window(event, now)) {
            is AccreditationWindow.NotOpen -> return AccreditResult.NotOpen(window.opensAt)
            is AccreditationWindow.Closed -> return AccreditResult.Closed(window.closedAt)
            is AccreditationWindow.Open -> Unit
        }

        return when (val added = participantRepository.add(
            eventId = eventId,
            name = name,
            document = document,
            email = email,
            isWalkIn = true
        )) {
            is AddParticipantResult.Success -> {
                val result = accredit(
                    eventId = eventId,
                    rawInput = added.participant.code,
                    method = AttendanceMethod.WALK_IN,
                    latitude = latitude,
                    longitude = longitude
                )
                if (result !is AccreditResult.Success) {
                    participantRepository.delete(added.participant.id)
                }
                result
            }

            is AddParticipantResult.Invalid -> AccreditResult.InvalidInput(
                added.errors.values.firstOrNull() ?: "Datos del participante incompletos."
            )

            is AddParticipantResult.Conflict -> AccreditResult.Conflict(added.message)
            is AddParticipantResult.Failure -> AccreditResult.Failure(added.message)
        }
    }

    suspend fun removeAttendance(attendanceId: Long): Boolean =
        attendanceDao.deleteById(attendanceId) > 0
}
