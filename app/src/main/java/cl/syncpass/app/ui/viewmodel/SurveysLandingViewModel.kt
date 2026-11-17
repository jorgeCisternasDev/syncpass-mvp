package cl.syncpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.SurveyDefinitionWithStats
import cl.syncpass.app.data.repository.CustomSurveyRepository
import cl.syncpass.app.data.repository.SurveyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SurveysLandingUiState(
    val loading: Boolean = true,
    val definitions: List<SurveyDefinitionWithStats> = emptyList(),
    val pendingSatisfaction: Int = 0,
    val completedSatisfaction: Int = 0
) {
    val totalResponses: Int get() = definitions.sumOf { it.responseCount }
}

class SurveysLandingViewModel(
    customSurveyRepository: CustomSurveyRepository,
    surveyRepository: SurveyRepository
) : ViewModel() {
    val state: StateFlow<SurveysLandingUiState> = combine(
        customSurveyRepository.observeDefinitions().catch { emit(emptyList()) },
        surveyRepository.observePending().catch { emit(emptyList()) },
        surveyRepository.observeCompleted().catch { emit(emptyList()) }
    ) { definitions, pending, completed ->
        SurveysLandingUiState(
            loading = false,
            definitions = definitions,
            pendingSatisfaction = pending.size,
            completedSatisfaction = completed.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SurveysLandingUiState()
    )
}
