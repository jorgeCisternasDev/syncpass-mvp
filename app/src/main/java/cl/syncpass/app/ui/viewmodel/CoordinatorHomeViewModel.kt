package cl.syncpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.EventWithStats
import cl.syncpass.app.data.repository.EventRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CoordinatorHomeUiState(
    val loading: Boolean = true,
    val events: List<EventWithStats> = emptyList()
)

class CoordinatorHomeViewModel(eventRepository: EventRepository) : ViewModel() {
    val state: StateFlow<CoordinatorHomeUiState> = eventRepository.observeEvents()
        .catch { emit(emptyList()) }
        .map { CoordinatorHomeUiState(loading = false, events = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CoordinatorHomeUiState()
        )
}
