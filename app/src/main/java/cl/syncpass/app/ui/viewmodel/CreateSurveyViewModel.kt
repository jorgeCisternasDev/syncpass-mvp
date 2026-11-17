package cl.syncpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.EventWithStats
import cl.syncpass.app.data.local.SurveyQuestionType
import cl.syncpass.app.data.repository.CreateSurveyResult
import cl.syncpass.app.data.repository.CustomSurveyRepository
import cl.syncpass.app.data.repository.EventRepository
import cl.syncpass.app.domain.SurveyDefinitionRules
import cl.syncpass.app.domain.SurveyQuestionInput
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SurveyQuestionDraft(
    val localId: Long,
    val prompt: String = "",
    val type: String = SurveyQuestionType.TEXT,
    val required: Boolean = true
)

data class CreateSurveyUiState(
    val loading: Boolean = true,
    val events: List<EventWithStats> = emptyList(),
    val title: String = "",
    val description: String = "",
    val eventId: Long? = null,
    val questions: List<SurveyQuestionDraft> = listOf(
        SurveyQuestionDraft(localId = 1)
    ),
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val message: String? = null
)

class CreateSurveyViewModel(
    private val customSurveyRepository: CustomSurveyRepository,
    eventRepository: EventRepository
) : ViewModel() {
    private val _state = MutableStateFlow(CreateSurveyUiState())
    val state: StateFlow<CreateSurveyUiState> = _state

    private val _saved = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()

    private var nextQuestionId = 2L

    init {
        viewModelScope.launch {
            eventRepository.observeEvents().collect { events ->
                _state.update { it.copy(events = events, loading = false) }
            }
        }
    }

    fun setTitle(value: String) = _state.update {
        it.copy(title = value.take(100), errors = it.errors - "title")
    }

    fun setDescription(value: String) = _state.update {
        it.copy(description = value.take(500), errors = it.errors - "description")
    }

    fun setEvent(eventId: Long?) = _state.update {
        it.copy(eventId = eventId, errors = it.errors - "event")
    }

    fun addQuestion() {
        val current = _state.value
        if (current.questions.size >= SurveyDefinitionRules.MAX_QUESTIONS) {
            _state.update {
                it.copy(message = "Puedes agregar hasta ${SurveyDefinitionRules.MAX_QUESTIONS} preguntas.")
            }
            return
        }
        _state.update {
            it.copy(
                questions = it.questions + SurveyQuestionDraft(localId = nextQuestionId++),
                errors = it.errors - "questions"
            )
        }
    }

    fun removeQuestion(localId: Long) {
        _state.update { current ->
            if (current.questions.size <= 1) current else {
                current.copy(questions = current.questions.filterNot { it.localId == localId })
            }
        }
    }

    fun setQuestionPrompt(localId: Long, value: String) = _state.update { current ->
        val index = current.questions.indexOfFirst { it.localId == localId }
        current.copy(
            questions = current.questions.map {
                if (it.localId == localId) it.copy(prompt = value.take(200)) else it
            },
            errors = if (index >= 0) current.errors - "q_$index" else current.errors
        )
    }

    fun setQuestionType(localId: Long, type: String) = _state.update { current ->
        current.copy(
            questions = current.questions.map {
                if (it.localId == localId) it.copy(type = type) else it
            }
        )
    }

    fun setQuestionRequired(localId: Long, required: Boolean) = _state.update { current ->
        current.copy(
            questions = current.questions.map {
                if (it.localId == localId) it.copy(required = required) else it
            }
        )
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun save() {
        val current = _state.value
        if (current.saving) return

        val inputs = current.questions.map { question ->
            SurveyQuestionInput(
                prompt = question.prompt,
                type = question.type,
                required = question.required
            )
        }
        val errors = SurveyDefinitionRules.validate(
            title = current.title,
            description = current.description,
            questions = inputs
        )
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }

        _state.update { it.copy(saving = true, errors = emptyMap(), message = null) }
        viewModelScope.launch {
            when (val result = customSurveyRepository.create(
                eventId = current.eventId,
                title = current.title,
                description = current.description,
                questions = inputs
            )) {
                is CreateSurveyResult.Success -> {
                    _state.update { it.copy(saving = false) }
                    _saved.emit(result.surveyId)
                }

                is CreateSurveyResult.Invalid -> _state.update {
                    it.copy(saving = false, errors = result.errors)
                }

                is CreateSurveyResult.Failure -> _state.update {
                    it.copy(saving = false, message = result.message)
                }
            }
        }
    }
}
