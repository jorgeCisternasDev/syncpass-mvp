package cl.syncpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.EventWithStats
import cl.syncpass.app.data.local.ParticipantStatusRow
import cl.syncpass.app.data.repository.EventRepository
import cl.syncpass.app.data.repository.ParticipantRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ParticipantModeUiState(
    val loading: Boolean = true,
    val eventWithStats: EventWithStats? = null,
    val participants: List<ParticipantStatusRow> = emptyList()
)

class ParticipantModeViewModel(
    eventId: Long,
    eventRepository: EventRepository,
    participantRepository: ParticipantRepository
) : ViewModel() {
    val state: StateFlow<ParticipantModeUiState> = combine(
        eventRepository.observeEvent(eventId).catch { emit(null) },
        participantRepository.observeByEvent(eventId).catch { emit(emptyList()) }
    ) { event, participants ->
        ParticipantModeUiState(
            loading = false,
            eventWithStats = event,
            participants = participants
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ParticipantModeUiState()
    )
}
