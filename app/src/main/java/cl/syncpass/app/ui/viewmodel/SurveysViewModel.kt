package cl.syncpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.PendingSurveyRow
import cl.syncpass.app.data.local.SurveyListRow
import cl.syncpass.app.data.repository.SurveyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SurveysUiState(
    val loading: Boolean = true,
    val pending: List<PendingSurveyRow> = emptyList(),
    val completed: List<SurveyListRow> = emptyList()
)

class SurveysViewModel(surveyRepository: SurveyRepository) : ViewModel() {
    val state: StateFlow<SurveysUiState> = combine(
        surveyRepository.observePending().catch { emit(emptyList()) },
        surveyRepository.observeCompleted().catch { emit(emptyList()) }
    ) { pending, completed ->
        SurveysUiState(loading = false, pending = pending, completed = completed)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SurveysUiState()
    )
}
