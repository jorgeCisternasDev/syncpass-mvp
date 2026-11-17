package cl.syncpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.EventEntity
import cl.syncpass.app.data.local.ParticipantEntity
import cl.syncpass.app.data.repository.EventRepository
import cl.syncpass.app.data.repository.ParticipantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QrUiState(
    val loading: Boolean = true,
    val participant: ParticipantEntity? = null,
    val event: EventEntity? = null,
    val error: String? = null
)

class QrViewModel(
    private val participantId: Long,
    participantRepository: ParticipantRepository,
    eventRepository: EventRepository
) : ViewModel() {
    private val _state = MutableStateFlow(QrUiState())
    val state: StateFlow<QrUiState> = _state

    init {
        viewModelScope.launch {
            val participant = participantRepository.getById(participantId)
            if (participant == null) {
                _state.update {
                    it.copy(loading = false, error = "El participante ya no existe.")
                }
                return@launch
            }
            val event = eventRepository.getEvent(participant.eventId)
            _state.update {
                it.copy(
                    loading = false,
                    participant = participant,
                    event = event,
                    error = if (event == null) "El evento del participante ya no existe." else null
                )
            }
        }
    }
}
