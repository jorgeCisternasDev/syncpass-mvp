package cl.syncpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.SurveyDefinitionWithStats
import cl.syncpass.app.data.local.SurveyQuestionEntity
import cl.syncpass.app.data.repository.CustomSurveyRepository
import cl.syncpass.app.data.repository.SurveyAnswerInput
import cl.syncpass.app.data.repository.SubmitSurveyResponseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnswerSurveyUiState(
    val loading: Boolean = true,
    val survey: SurveyDefinitionWithStats? = null,
    val questions: List<SurveyQuestionEntity> = emptyList(),
    val respondentName: String = "",
    val textAnswers: Map<Long, String> = emptyMap(),
    val ratingAnswers: Map<Long, Int> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val loadError: String? = null,
    val message: String? = null
)

class AnswerSurveyViewModel(
    private val surveyId: Long,
    private val customSurveyRepository: CustomSurveyRepository,
    private val onLocalSave: () -> Unit = {}
) : ViewModel() {
    private val _state = MutableStateFlow(AnswerSurveyUiState())
    val state: StateFlow<AnswerSurveyUiState> = _state

    init {
        viewModelScope.launch {
            val survey = customSurveyRepository.getDefinition(surveyId)
            if (survey == null) {
                _state.update {
                    it.copy(loading = false, loadError = "La encuesta ya no existe.")
                }
                return@launch
            }
            if (!survey.definition.isActive) {
                _state.update {
                    it.copy(
                        loading = false,
                        survey = survey,
                        loadError = "La encuesta está pausada y no recibe respuestas."
                    )
                }
                return@launch
            }
            val questions = customSurveyRepository.getQuestions(surveyId)
            if (questions.isEmpty()) {
                _state.update {
                    it.copy(
                        loading = false,
                        survey = survey,
                        loadError = "La encuesta no tiene preguntas."
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(loading = false, survey = survey, questions = questions)
            }
        }
    }

    fun setRespondentName(value: String) = _state.update {
        it.copy(respondentName = value.take(80))
    }

    fun setTextAnswer(questionId: Long, value: String) = _state.update {
        it.copy(
            textAnswers = it.textAnswers + (questionId to value.take(1_000)),
            errors = it.errors - "q_$questionId"
        )
    }

    fun setRatingAnswer(questionId: Long, value: Int) = _state.update {
        it.copy(
            ratingAnswers = it.ratingAnswers + (questionId to value),
            errors = it.errors - "q_$questionId"
        )
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun submit() {
        val current = _state.value
        if (current.saving || current.saved) return
        if (current.survey == null || current.questions.isEmpty()) return

        val answers = current.questions.associate { question ->
            question.id to SurveyAnswerInput(
                text = current.textAnswers[question.id],
                rating = current.ratingAnswers[question.id]
            )
        }
        _state.update { it.copy(saving = true, errors = emptyMap(), message = null) }
        viewModelScope.launch {
            when (val result = customSurveyRepository.submit(
                surveyId = surveyId,
                respondentName = current.respondentName,
                answers = answers
            )) {
                is SubmitSurveyResponseResult.Success -> {
                    runCatching { onLocalSave() }
                    _state.update {
                        it.copy(saving = false, saved = true)
                    }
                }

                is SubmitSurveyResponseResult.Invalid -> _state.update {
                    it.copy(saving = false, errors = result.errors)
                }

                is SubmitSurveyResponseResult.NotAllowed -> _state.update {
                    it.copy(saving = false, message = result.message)
                }

                is SubmitSurveyResponseResult.Failure -> _state.update {
                    it.copy(saving = false, message = result.message)
                }
            }
        }
    }
}
