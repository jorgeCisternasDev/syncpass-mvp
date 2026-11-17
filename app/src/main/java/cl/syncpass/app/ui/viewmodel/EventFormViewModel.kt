package cl.syncpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.EventEntity
import cl.syncpass.app.data.local.ProjectEntity
import cl.syncpass.app.data.local.SurveyQuestionType
import cl.syncpass.app.data.repository.CreateSurveyResult
import cl.syncpass.app.data.repository.CustomSurveyRepository
import cl.syncpass.app.data.repository.EventInput
import cl.syncpass.app.data.repository.EventRepository
import cl.syncpass.app.data.repository.ProjectRepository
import cl.syncpass.app.data.repository.SaveEventResult
import cl.syncpass.app.domain.EventDraft
import cl.syncpass.app.domain.EventRules
import cl.syncpass.app.domain.SurveyDefinitionRules
import cl.syncpass.app.domain.SurveyQuestionInput
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class EventFormUiState(
    val loading: Boolean = true,
    val eventId: Long? = null,
    val isEditing: Boolean = false,
    val name: String = "",
    val description: String = "",
    val venue: String = "",
    val address: String = "",
    val startAt: Long = System.currentTimeMillis(),
    val endAt: Long = System.currentTimeMillis() + 3 * 60 * 60 * 1000L,
    val capacityText: String = "50",
    val allowWalkIn: Boolean = false,
    val surveyEnabled: Boolean = true,
    val surveyTitle: String = "Encuesta del evento",
    val surveyDescription: String = "Cuéntanos cómo fue tu experiencia.",
    val surveyQuestions: List<SurveyQuestionDraft> = listOf(
        SurveyQuestionDraft(
            localId = 1,
            prompt = "¿Cómo evalúas el evento?",
            type = SurveyQuestionType.RATING
        )
    ),
    val projectId: Long? = null,
    val projects: List<ProjectEntity> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val deleting: Boolean = false,
    val loadError: String? = null
)

class EventFormViewModel(
    private val eventId: Long?,
    private val eventRepository: EventRepository,
    projectRepository: ProjectRepository,
    private val customSurveyRepository: CustomSurveyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        EventFormUiState(
            loading = eventId != null,
            eventId = eventId,
            isEditing = eventId != null
        )
    )
    val state: StateFlow<EventFormUiState> = _state

    private val _saved = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()

    private val _deleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleted = _deleted.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            projectRepository.observeOptions().collect { projects ->
                _state.update { it.copy(projects = projects) }
            }
        }
        if (eventId != null) {
            viewModelScope.launch {
                val event = eventRepository.getEvent(eventId)
                if (event == null) {
                    _state.update {
                        it.copy(
                            loading = false,
                            loadError = "El evento ya no existe o fue eliminado."
                        )
                    }
                } else {
                    _state.update { it.populate(event) }
                }
            }
        } else {
            _state.update {
                it.copy(
                    loading = false,
                    startAt = System.currentTimeMillis(),
                    endAt = System.currentTimeMillis() + 3 * 60 * 60 * 1000L
                )
            }
        }
    }

    private fun EventFormUiState.populate(event: EventEntity) = copy(
        loading = false,
        eventId = event.id,
        isEditing = true,
        name = event.name,
        description = event.description,
        venue = event.venue,
        address = event.address,
        startAt = event.startAt,
        endAt = event.endAt,
        capacityText = if (event.capacity > 0) event.capacity.toString() else "",
        allowWalkIn = event.allowWalkIn,
        surveyEnabled = event.surveyEnabled,
        projectId = event.projectId,
        loadError = null
    )

    fun setName(value: String) = _state.update {
        it.copy(name = value, errors = it.errors - "name")
    }

    fun setDescription(value: String) = _state.update {
        it.copy(description = value, errors = it.errors - "description")
    }

    fun setVenue(value: String) = _state.update {
        it.copy(venue = value, errors = it.errors - "venue")
    }

    fun setAddress(value: String) = _state.update {
        it.copy(address = value, errors = it.errors - "address")
    }

    fun setCapacity(value: String) = _state.update {
        val filtered = value.filter { char -> char.isDigit() }.take(6)
        it.copy(capacityText = filtered, errors = it.errors - "capacity")
    }

    fun setAllowWalkIn(value: Boolean) = _state.update { it.copy(allowWalkIn = value) }

    fun setSurveyEnabled(value: Boolean) = _state.update { it.copy(surveyEnabled = value) }

    fun setSurveyTitle(value: String) = _state.update {
        it.copy(surveyTitle = value.take(100), errors = it.errors - "survey_title")
    }

    fun setSurveyDescription(value: String) = _state.update {
        it.copy(surveyDescription = value.take(500), errors = it.errors - "survey_description")
    }

    fun addSurveyQuestion() {
        val current = _state.value
        if (current.surveyQuestions.size >= SurveyDefinitionRules.MAX_QUESTIONS) {
            _messages.tryEmit("Puedes agregar hasta ${SurveyDefinitionRules.MAX_QUESTIONS} preguntas.")
            return
        }
        _state.update {
            it.copy(
                surveyQuestions = it.surveyQuestions + SurveyQuestionDraft(
                    localId = (it.surveyQuestions.maxOfOrNull { q -> q.localId } ?: 0L) + 1
                ),
                errors = it.errors - "survey_questions"
            )
        }
    }

    fun removeSurveyQuestion(localId: Long) = _state.update { current ->
        if (current.surveyQuestions.size <= 1) {
            current
        } else {
            current.copy(
                surveyQuestions = current.surveyQuestions.filterNot { it.localId == localId }
            )
        }
    }

    fun setSurveyQuestionPrompt(localId: Long, value: String) = _state.update { current ->
        val index = current.surveyQuestions.indexOfFirst { it.localId == localId }
        current.copy(
            surveyQuestions = current.surveyQuestions.map {
                if (it.localId == localId) it.copy(prompt = value.take(200)) else it
            },
            errors = if (index >= 0) current.errors - "survey_q_$index" else current.errors
        )
    }

    fun setSurveyQuestionType(localId: Long, type: String) = _state.update { current ->
        current.copy(
            surveyQuestions = current.surveyQuestions.map {
                if (it.localId == localId) it.copy(type = type) else it
            }
        )
    }

    fun setSurveyQuestionRequired(localId: Long, required: Boolean) = _state.update { current ->
        current.copy(
            surveyQuestions = current.surveyQuestions.map {
                if (it.localId == localId) it.copy(required = required) else it
            }
        )
    }

    fun setProject(projectId: Long?) = _state.update {
        it.copy(projectId = projectId, errors = it.errors - "project")
    }

    fun setStartDate(year: Int, month: Int, day: Int) = _state.update {
        it.copy(
            startAt = replaceDate(it.startAt, year, month, day),
            errors = it.errors - "start" - "end"
        )
    }

    fun setStartTime(hour: Int, minute: Int) = _state.update {
        it.copy(
            startAt = replaceTime(it.startAt, hour, minute),
            errors = it.errors - "start" - "end"
        )
    }

    fun setEndDate(year: Int, month: Int, day: Int) = _state.update {
        it.copy(
            endAt = replaceDate(it.endAt, year, month, day),
            errors = it.errors - "end"
        )
    }

    fun setEndTime(hour: Int, minute: Int) = _state.update {
        it.copy(
            endAt = replaceTime(it.endAt, hour, minute),
            errors = it.errors - "end"
        )
    }

    private fun replaceDate(epochMillis: Long, year: Int, month: Int, day: Int): Long {
        val zone = ZoneId.systemDefault()
        val current = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val date = runCatching { LocalDate.of(year, month, day) }.getOrElse { current.toLocalDate() }
        return ZonedDateTime.of(date, current.toLocalTime(), zone).toInstant().toEpochMilli()
    }

    private fun replaceTime(epochMillis: Long, hour: Int, minute: Int): Long {
        val zone = ZoneId.systemDefault()
        val current = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val time = runCatching { LocalTime.of(hour, minute) }.getOrElse { current.toLocalTime() }
        return ZonedDateTime.of(current.toLocalDate(), time, zone).toInstant().toEpochMilli()
    }

    fun save() {
        val current = _state.value
        if (current.saving || current.deleting) return

        val draft = EventDraft(
            name = current.name,
            description = current.description,
            venue = current.venue,
            address = current.address,
            startAt = current.startAt,
            endAt = current.endAt,
            capacityText = current.capacityText
        )
        val validation = EventRules.validate(draft)
        if (!validation.isValid) {
            _state.update { it.copy(errors = validation.errors) }
            return
        }

        val createSurvey = current.eventId == null && current.surveyEnabled
        val surveyInputs = current.surveyQuestions.map { question ->
            SurveyQuestionInput(
                prompt = question.prompt,
                type = question.type,
                required = question.required
            )
        }
        if (createSurvey) {
            val surveyErrors = SurveyDefinitionRules.validate(
                title = current.surveyTitle,
                description = current.surveyDescription,
                questions = surveyInputs
            )
            if (surveyErrors.isNotEmpty()) {
                _state.update {
                    it.copy(errors = surveyErrors.mapKeys { (key, _) -> "survey_$key" })
                }
                return
            }
        }

        _state.update { it.copy(saving = true, errors = emptyMap()) }
        viewModelScope.launch {
            val result = eventRepository.save(
                eventId = current.eventId,
                input = EventInput(
                    name = current.name,
                    description = current.description,
                    venue = current.venue,
                    address = current.address,
                    startAt = current.startAt,
                    endAt = current.endAt,
                    capacity = validation.capacity,
                    allowWalkIn = current.allowWalkIn,
                    surveyEnabled = current.surveyEnabled,
                    projectId = current.projectId
                )
            )
            when (result) {
                is SaveEventResult.Success -> {
                    if (createSurvey) {
                        when (val surveyResult = customSurveyRepository.create(
                            eventId = result.eventId,
                            title = current.surveyTitle,
                            description = current.surveyDescription,
                            questions = surveyInputs
                        )) {
                            is CreateSurveyResult.Success -> {
                                _state.update { it.copy(saving = false, eventId = result.eventId) }
                                _saved.emit(result.eventId)
                            }

                            is CreateSurveyResult.Invalid -> {
                                eventRepository.delete(result.eventId)
                                _state.update {
                                    it.copy(
                                        saving = false,
                                        errors = surveyResult.errors.mapKeys { (key, _) ->
                                            "survey_$key"
                                        }
                                    )
                                }
                            }

                            is CreateSurveyResult.Failure -> {
                                eventRepository.delete(result.eventId)
                                _state.update { it.copy(saving = false) }
                                _messages.emit(surveyResult.message)
                            }
                        }
                    } else {
                        _state.update { it.copy(saving = false, eventId = result.eventId) }
                        _saved.emit(result.eventId)
                    }
                }

                is SaveEventResult.Invalid -> _state.update {
                    it.copy(saving = false, errors = result.errors)
                }

                is SaveEventResult.Failure -> {
                    _state.update { it.copy(saving = false) }
                    _messages.emit(result.message)
                }
            }
        }
    }

    fun deleteEvent() {
        val id = _state.value.eventId ?: return
        if (_state.value.saving || _state.value.deleting) return
        _state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            if (eventRepository.delete(id)) {
                _deleted.emit(Unit)
            } else {
                _state.update { it.copy(deleting = false) }
                _messages.emit("No se pudo eliminar el evento.")
            }
        }
    }
}
