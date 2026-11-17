package cl.syncpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.SurveyAnswerRow
import cl.syncpass.app.data.local.SurveyDefinitionWithStats
import cl.syncpass.app.data.local.SurveyQuestionEntity
import cl.syncpass.app.data.repository.CustomSurveyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SurveyResponseGroup(
    val responseId: Long,
    val respondentName: String,
    val submittedAt: Long,
    val answers: List<SurveyAnswerRow>
)

data class SurveyResultsUiState(
    val loading: Boolean = true,
    val survey: SurveyDefinitionWithStats? = null,
    val questions: List<SurveyQuestionEntity> = emptyList(),
    val responses: List<SurveyResponseGroup> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val deleted: Boolean = false
)

private data class ResultsAction(
    val busy: Boolean = false,
    val message: String? = null,
    val deleted: Boolean = false
)

class SurveyResultsViewModel(
    private val surveyId: Long,
    private val customSurveyRepository: CustomSurveyRepository
) : ViewModel() {
    private val action = MutableStateFlow(ResultsAction())

    val state: StateFlow<SurveyResultsUiState> = combine(
        customSurveyRepository.observeDefinition(surveyId),
        customSurveyRepository.observeQuestions(surveyId),
        customSurveyRepository.observeAnswerRows(surveyId),
        action
    ) { survey, questions, answerRows, currentAction ->
        val groups = answerRows
            .groupBy { it.responseId }
            .map { (responseId, rows) ->
                SurveyResponseGroup(
                    responseId = responseId,
                    respondentName = rows.first().respondentName,
                    submittedAt = rows.first().submittedAt,
                    answers = rows.sortedBy { it.position }
                )
            }
        SurveyResultsUiState(
            loading = false,
            survey = survey,
            questions = questions,
            responses = groups,
            busy = currentAction.busy,
            message = currentAction.message,
            deleted = currentAction.deleted
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SurveyResultsUiState()
    )

    fun toggleActive() {
        val survey = state.value.survey ?: return
        if (action.value.busy) return
        action.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val newValue = !survey.definition.isActive
            val success = customSurveyRepository.setActive(surveyId, newValue)
            action.update {
                it.copy(
                    busy = false,
                    message = if (success) {
                        if (newValue) "Encuesta reactivada." else "Encuesta pausada."
                    } else {
                        "No se pudo cambiar el estado de la encuesta."
                    }
                )
            }
        }
    }

    fun delete() {
        if (action.value.busy) return
        action.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val success = customSurveyRepository.delete(surveyId)
            action.update {
                it.copy(
                    busy = false,
                    deleted = success,
                    message = if (success) null else "No se pudo eliminar la encuesta."
                )
            }
        }
    }

    fun clearMessage() {
        action.update { it.copy(message = null) }
    }
}
