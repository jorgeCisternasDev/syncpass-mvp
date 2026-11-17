package cl.syncpass.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.EventWithStats
import cl.syncpass.app.data.local.RatingCount
import cl.syncpass.app.data.local.SurveyListRow
import cl.syncpass.app.data.local.SurveyStatsRow
import cl.syncpass.app.data.repository.EventRepository
import cl.syncpass.app.data.repository.ExportRepository
import cl.syncpass.app.data.repository.ExportResult
import cl.syncpass.app.data.repository.SurveyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IndicatorsUiState(
    val loading: Boolean = true,
    val eventWithStats: EventWithStats? = null,
    val surveyStats: SurveyStatsRow = SurveyStatsRow(0, null, null),
    val distribution: List<RatingCount> = emptyList(),
    val surveys: List<SurveyListRow> = emptyList(),
    val exporting: Boolean = false,
    val message: String? = null
)

class IndicatorsViewModel(
    private val eventId: Long,
    eventRepository: EventRepository,
    surveyRepository: SurveyRepository,
    private val exportRepository: ExportRepository
) : ViewModel() {

    private val exportState = MutableStateFlow(ExportUiState())

    val state: StateFlow<IndicatorsUiState> = combine(
        eventRepository.observeEvent(eventId),
        surveyRepository.observeStats(eventId),
        surveyRepository.observeDistribution(eventId),
        surveyRepository.observeByEvent(eventId),
        exportState
    ) { event, stats, distribution, surveys, export ->
        IndicatorsUiState(
            loading = false,
            eventWithStats = event,
            surveyStats = stats,
            distribution = distribution,
            surveys = surveys,
            exporting = export.exporting,
            message = export.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = IndicatorsUiState()
    )

    fun export(uri: Uri) {
        if (exportState.value.exporting) return
        exportState.value = ExportUiState(exporting = true)
        viewModelScope.launch {
            when (val result = exportRepository.writeCsv(uri, eventId)) {
                is ExportResult.Success -> exportState.value = ExportUiState(
                    exporting = false,
                    message = "Se exportaron ${result.rows} registros a CSV."
                )

                is ExportResult.Failure -> exportState.value = ExportUiState(
                    exporting = false,
                    message = result.message
                )
            }
        }
    }

    fun clearMessage() {
        exportState.update { it.copy(message = null) }
    }
}
