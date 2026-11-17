package cl.syncpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.AttendanceMethod
import cl.syncpass.app.data.local.EventEntity
import cl.syncpass.app.data.repository.AccreditResult
import cl.syncpass.app.data.repository.AccreditationRepository
import cl.syncpass.app.data.repository.EventRepository
import cl.syncpass.app.util.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class ScannerLocalState(
    val processing: Boolean = false,
    val feedback: AccreditFeedback? = null
)

data class ScannerUiState(
    val loading: Boolean = true,
    val event: EventEntity? = null,
    val processing: Boolean = false,
    val feedback: AccreditFeedback? = null,
    val attendedCount: Int = 0,
    val registeredCount: Int = 0
)

class ScannerViewModel(
    private val eventId: Long,
    eventRepository: EventRepository,
    private val accreditationRepository: AccreditationRepository,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val _local = MutableStateFlow(ScannerLocalState())

    val state: StateFlow<ScannerUiState> = combine(
        eventRepository.observeEvent(eventId).catch { emit(null) },
        _local
    ) { stats, local ->
        ScannerUiState(
            loading = false,
            event = stats?.event,
            processing = local.processing,
            feedback = local.feedback,
            attendedCount = stats?.attendedCount ?: 0,
            registeredCount = stats?.registeredCount ?: 0
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScannerUiState()
    )

    fun onCode(rawValue: String, useLocation: Boolean) {
        if (_local.value.processing || _local.value.feedback != null) return
        _local.update { it.copy(processing = true, feedback = null) }
        viewModelScope.launch {
            try {
                val (latitude, longitude) = captureLocation(useLocation)
                val result = accreditationRepository.accredit(
                    eventId = eventId,
                    rawInput = rawValue,
                    method = AttendanceMethod.QR,
                    latitude = latitude,
                    longitude = longitude
                )
                _local.update { it.copy(processing = false, feedback = result.toFeedback()) }
            } catch (error: Exception) {
                _local.update {
                    it.copy(
                        processing = false,
                        feedback = AccreditFeedback.Error(
                            "No se pudo leer el QR: ${error.message ?: "error desconocido"}"
                        )
                    )
                }
            }
        }
    }

    fun submitManual(rawValue: String, useLocation: Boolean) {
        if (_local.value.processing || _local.value.feedback != null) return
        _local.update { it.copy(processing = true, feedback = null) }
        viewModelScope.launch {
            try {
                val (latitude, longitude) = captureLocation(useLocation)
                val result = accreditationRepository.accredit(
                    eventId = eventId,
                    rawInput = rawValue,
                    method = AttendanceMethod.MANUAL,
                    latitude = latitude,
                    longitude = longitude
                )
                _local.update { it.copy(processing = false, feedback = result.toFeedback()) }
            } catch (error: Exception) {
                _local.update {
                    it.copy(
                        processing = false,
                        feedback = AccreditFeedback.Error(
                            "No se pudo acreditar: ${error.message ?: "error desconocido"}"
                        )
                    )
                }
            }
        }
    }

    fun registerWalkIn(name: String, document: String, email: String, useLocation: Boolean) {
        if (_local.value.processing) return
        _local.update { it.copy(processing = true, feedback = null) }
        viewModelScope.launch {
            try {
                val (latitude, longitude) = captureLocation(useLocation)
                val result = accreditationRepository.registerWalkInAndAccredit(
                    eventId = eventId,
                    name = name,
                    document = document,
                    email = email,
                    latitude = latitude,
                    longitude = longitude
                )
                _local.update { it.copy(processing = false, feedback = result.toFeedback()) }
            } catch (error: Exception) {
                _local.update {
                    it.copy(
                        processing = false,
                        feedback = AccreditFeedback.Error(
                            "No se pudo registrar el ingreso: ${error.message ?: "error desconocido"}"
                        )
                    )
                }
            }
        }
    }

    fun clearFeedback() {
        _local.update { it.copy(feedback = null) }
    }

    private suspend fun captureLocation(useLocation: Boolean): Pair<Double?, Double?> {
        if (!useLocation || !locationProvider.hasPermission()) return null to null
        val location = locationProvider.getCurrentLocation()
        return location?.latitude to location?.longitude
    }
}
