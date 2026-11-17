package cl.syncpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.PendingSurveyRow
import cl.syncpass.app.data.local.SurveyDefinitionWithStats
import cl.syncpass.app.data.local.SurveyListRow
import cl.syncpass.app.data.repository.CustomSurveyRepository
import cl.syncpass.app.data.repository.SurveyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TakeSurveyUiState(
    val loading: Boolean = true,
    val availableDefinitions: List<SurveyDefinitionWithStats> = emptyList(),
    val pendingSatisfaction: List<PendingSurveyRow> = emptyList(),
    val completedSatisfaction: List<SurveyListRow> = emptyList()
)

class TakeSurveyViewModel(
    customSurveyRepository: CustomSurveyRepository,
    surveyRepository: SurveyRepository
) : ViewModel() {
    val state: StateFlow<TakeSurveyUiState> = combine(
        customSurveyRepository.observeDefinitions().catch { emit(emptyList()) },
        surveyRepository.observePending().catch { emit(emptyList()) },
        surveyRepository.observeCompleted().catch { emit(emptyList()) }
    ) { definitions, pending, completed ->
        TakeSurveyUiState(
            loading = false,
            availableDefinitions = definitions.filter { it.definition.isActive },
            pendingSatisfaction = pending,
            completedSatisfaction = completed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TakeSurveyUiState()
    )
}
