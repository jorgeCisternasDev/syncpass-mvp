package cl.syncpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.EventWithStats
import cl.syncpass.app.data.local.ProjectEntity
import cl.syncpass.app.data.repository.EventRepository
import cl.syncpass.app.data.repository.ProjectRepository
import cl.syncpass.app.data.repository.SurveyRepository
import cl.syncpass.app.util.ConnectivityMonitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    val events: List<EventWithStats> = emptyList(),
    val projects: Map<Long, ProjectEntity> = emptyMap(),
    val totalParticipants: Int = 0,
    val totalAttendance: Int = 0,
    val totalSurveys: Int = 0,
    val pendingSurveys: Int = 0,
    val isOnline: Boolean = false
)

class HomeViewModel(
    eventRepository: EventRepository,
    projectRepository: ProjectRepository,
    surveyRepository: SurveyRepository,
    connectivityMonitor: ConnectivityMonitor
) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        eventRepository.observeEvents().catch { emit(emptyList()) },
        projectRepository.observeOptions().catch { emit(emptyList()) },
        surveyRepository.observePending().catch { emit(emptyList()) },
        connectivityMonitor.isOnline.catch { emit(false) }
    ) { events, projects, pending, online ->
        HomeUiState(
            loading = false,
            events = events,
            projects = projects.associateBy { it.id },
            totalParticipants = events.sumOf { it.registeredCount },
            totalAttendance = events.sumOf { it.attendedCount },
            totalSurveys = events.sumOf { it.surveyCount },
            pendingSurveys = pending.size,
            isOnline = online
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )
}
