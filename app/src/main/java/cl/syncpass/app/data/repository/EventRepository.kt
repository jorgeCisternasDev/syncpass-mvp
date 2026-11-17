package cl.syncpass.app.data.repository

import cl.syncpass.app.data.local.EventDao
import cl.syncpass.app.data.local.EventEntity
import cl.syncpass.app.data.local.EventWithStats
import cl.syncpass.app.data.local.ProjectDao
import cl.syncpass.app.domain.EventDraft
import cl.syncpass.app.domain.EventRules
import cl.syncpass.app.domain.SurveyReleaseRules
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

data class EventInput(
    val name: String,
    val description: String,
    val venue: String,
    val address: String,
    val startAt: Long,
    val endAt: Long,
    val capacity: Int,
    val allowWalkIn: Boolean,
    val surveyEnabled: Boolean,
    val projectId: Long?
)

sealed interface SaveEventResult {
    data class Success(val eventId: Long) : SaveEventResult
    data class Invalid(val errors: Map<String, String>) : SaveEventResult
    data class Failure(val message: String) : SaveEventResult
}

class EventRepository(
    private val eventDao: EventDao,
    private val projectDao: ProjectDao,
    private val nowProvider: () -> Long = System::currentTimeMillis
) {
    fun observeEvents(): Flow<List<EventWithStats>> =
        eventDao.observeAllWithStats().transformLatest { events ->
            var current = events
            while (true) {
                val now = nowProvider()
                if (current.any {
                        SurveyReleaseRules.shouldAutoRelease(
                            it.event.surveyReleased,
                            it.event.endAt,
                            now
                        )
                    }
                ) {
                    eventDao.autoReleaseFinishedSurveys(now)
                }
                val released = current.map { summary ->
                    if (SurveyReleaseRules.isReleased(
                            summary.event.surveyReleased,
                            summary.event.endAt,
                            now
                        )
                    ) {
                        summary.copy(event = summary.event.copy(surveyReleased = true))
                    } else {
                        summary
                    }
                }
                emit(released)
                val nextEnd = released
                    .filter { !it.event.surveyReleased && it.event.surveyEnabled }
                    .minOfOrNull { it.event.endAt }
                    ?: break
                delay((nextEnd - now).coerceAtLeast(50L))
                current = released
            }
        }

    fun observeEvent(eventId: Long): Flow<EventWithStats?> =
        eventDao.observeByIdWithStats(eventId).transformLatest { initial ->
            var summary = initial
            while (summary != null) {
                val now = nowProvider()
                if (SurveyReleaseRules.shouldAutoRelease(
                        summary.event.surveyReleased,
                        summary.event.endAt,
                        now
                    )
                ) {
                    eventDao.update(
                        summary.event.copy(surveyReleased = true, updatedAt = now)
                    )
                    summary = summary.copy(
                        event = summary.event.copy(surveyReleased = true)
                    )
                }
                emit(summary)
                if (summary.event.surveyReleased || !summary.event.surveyEnabled) break
                delay((summary.event.endAt - now).coerceAtLeast(50L))
            }
        }

    fun observeRawEvent(eventId: Long): Flow<EventEntity?> = eventDao.observeById(eventId)

    suspend fun getEvent(eventId: Long): EventEntity? {
        val event = eventDao.getById(eventId) ?: return null
        val now = nowProvider()
        return if (SurveyReleaseRules.shouldAutoRelease(
                event.surveyReleased,
                event.endAt,
                now
            )
        ) {
            eventDao.update(event.copy(surveyReleased = true, updatedAt = now))
            event.copy(surveyReleased = true)
        } else {
            event
        }
    }

    suspend fun save(eventId: Long?, input: EventInput): SaveEventResult {
        val draft = EventDraft(
            name = input.name,
            description = input.description,
            venue = input.venue,
            address = input.address,
            startAt = input.startAt,
            endAt = input.endAt,
            capacityText = if (input.capacity <= 0) "" else input.capacity.toString()
        )
        val validation = EventRules.validate(draft)
        if (!validation.isValid) return SaveEventResult.Invalid(validation.errors)

        if (input.projectId != null && projectDao.getById(input.projectId) == null) {
            return SaveEventResult.Invalid(mapOf("project" to "El proyecto seleccionado ya no existe."))
        }

        val now = System.currentTimeMillis()
        return try {
            if (eventId == null) {
                val id = eventDao.insert(
                    EventEntity(
                        projectId = input.projectId,
                        name = input.name.trim(),
                        description = input.description.trim(),
                        venue = input.venue.trim(),
                        address = input.address.trim(),
                        startAt = input.startAt,
                        endAt = input.endAt,
                        capacity = validation.capacity,
                        allowWalkIn = input.allowWalkIn,
                        surveyEnabled = input.surveyEnabled,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                SaveEventResult.Success(id)
            } else {
                val existing = eventDao.getById(eventId)
                    ?: return SaveEventResult.Failure("El evento ya no existe.")
                eventDao.update(
                    existing.copy(
                        projectId = input.projectId,
                        name = input.name.trim(),
                        description = input.description.trim(),
                        venue = input.venue.trim(),
                        address = input.address.trim(),
                        startAt = input.startAt,
                        endAt = input.endAt,
                        capacity = validation.capacity,
                        allowWalkIn = input.allowWalkIn,
                        surveyEnabled = input.surveyEnabled,
                        updatedAt = now
                    )
                )
                SaveEventResult.Success(eventId)
            }
        } catch (error: Exception) {
            SaveEventResult.Failure(
                "No se pudo guardar el evento: ${error.message ?: "error desconocido"}"
            )
        }
    }

    suspend fun setSurveyReleased(eventId: Long, released: Boolean): Boolean {
        val event = getEvent(eventId) ?: return false
        if (event.surveyReleased == released) return true
        return try {
            eventDao.update(
                event.copy(
                    surveyReleased = released,
                    updatedAt = nowProvider()
                )
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun releaseSurvey(eventId: Long): Boolean {
        val event = eventDao.getById(eventId) ?: return false
        if (event.surveyReleased) return true
        return try {
            eventDao.update(
                event.copy(
                    surveyReleased = true,
                    updatedAt = nowProvider()
                )
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun delete(eventId: Long): Boolean {
        val event = eventDao.getById(eventId) ?: return false
        return try {
            eventDao.delete(event)
            true
        } catch (_: Exception) {
            false
        }
    }
}
