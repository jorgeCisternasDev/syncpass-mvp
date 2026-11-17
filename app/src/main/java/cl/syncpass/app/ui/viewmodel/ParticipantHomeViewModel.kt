package cl.syncpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.EventWithStats
import cl.syncpass.app.data.local.ParticipantStatusRow
import cl.syncpass.app.data.repository.EventRepository
import cl.syncpass.app.data.repository.ParticipantRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId

data class ParticipantHomeUiState(
    val loading: Boolean = true,
    val eventWithStats: EventWithStats? = null,
    val participant: ParticipantStatusRow? = null,
    val noEventToday: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ParticipantHomeViewModel(
    private val username: String,
    eventRepository: EventRepository,
    participantRepository: ParticipantRepository
) : ViewModel() {
    private val todayEvents = eventRepository.observeEvents()
        .catch { emit(emptyList()) }
        .map { events ->
            val zone = ZoneId.systemDefault()
            val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
            val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            events
                .filter { it.event.startAt < end && it.event.endAt >= start }
                .sortedBy { it.event.startAt }
        }

    val state: StateFlow<ParticipantHomeUiState> = todayEvents
        .flatMapLatest { events ->
            val event = events.firstOrNull()
            if (event == null) {
                flowOf(ParticipantHomeUiState(loading = false, noEventToday = true))
            } else {
                participantRepository.observeByEvent(event.event.id)
                    .catch { emit(emptyList()) }
                    .map { participants ->
                        ParticipantHomeUiState(
                            loading = false,
                            eventWithStats = event,
                            participant = participants.firstOrNull {
                                it.participant.name.startsWith(username, ignoreCase = true)
                            }
                        )
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ParticipantHomeUiState()
        )
}
