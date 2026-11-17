package cl.syncpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.AttendanceMethod
import cl.syncpass.app.data.local.AttendanceRow
import cl.syncpass.app.data.local.EventEntity
import cl.syncpass.app.data.local.EventWithStats
import cl.syncpass.app.data.local.StatsDao
import cl.syncpass.app.data.repository.AccreditResult
import cl.syncpass.app.data.repository.AccreditationRepository
import cl.syncpass.app.data.repository.EventRepository
import cl.syncpass.app.domain.AccreditationRules
import cl.syncpass.app.domain.AccreditationWindow
import cl.syncpass.app.util.ConnectivityMonitor
import cl.syncpass.app.util.LocationProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class AccreditLocalState(
    val selectedEventId: Long? = null,
    val busy: Boolean = false,
    val feedback: AccreditFeedback? = null
)

data class AccreditUiState(
    val loading: Boolean = true,
    val events: List<EventWithStats> = emptyList(),
    val selectedEvent: EventEntity? = null,
    val attendance: List<AttendanceRow> = emptyList(),
    val busy: Boolean = false,
    val feedback: AccreditFeedback? = null,
    val isOnline: Boolean = false,
    val savedRecords: Int = 0
)

class AccreditViewModel(
    initialEventId: Long?,
    private val eventRepository: EventRepository,
    private val accreditationRepository: AccreditationRepository,
    private val locationProvider: LocationProvider,
    statsDao: StatsDao,
    connectivityMonitor: ConnectivityMonitor
) : ViewModel() {

    private val _local = MutableStateFlow(AccreditLocalState(selectedEventId = initialEventId))
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val eventsFlow = eventRepository.observeEvents()
        .catch { emit(emptyList()) }

    private val selectedEventFlow = _local
        .map { it.selectedEventId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else eventRepository.observeRawEvent(id)
        }

    private val attendanceFlow = _local
        .map { it.selectedEventId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else accreditationRepository.observeAttendance(id)
        }

    private val connectionFlow = combine(
        statsDao.observeAttendanceCount().catch { emit(0) },
        connectivityMonitor.isOnline.catch { emit(false) }
    ) { count, online -> count to online }

    val state: StateFlow<AccreditUiState> = combine(
        eventsFlow,
        _local,
        selectedEventFlow,
        attendanceFlow,
        connectionFlow
    ) { events, local, selectedEvent, attendance, connection ->
        AccreditUiState(
            loading = false,
            events = events,
            selectedEvent = selectedEvent,
            attendance = attendance,
            busy = local.busy,
            feedback = local.feedback,
            isOnline = connection.second,
            savedRecords = connection.first
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccreditUiState()
    )

    init {
        viewModelScope.launch {
            val events = runCatching { eventRepository.observeEvents().first() }
                .getOrDefault(emptyList())
            if (events.isEmpty()) return@launch

            val currentId = _local.value.selectedEventId
            if (currentId != null && events.any { it.event.id == currentId }) return@launch

            val now = System.currentTimeMillis()
            val initial = initialEventId?.let { id -> events.firstOrNull { it.event.id == id } }
            val open = events.firstOrNull {
                AccreditationRules.window(it.event, now) is AccreditationWindow.Open
            }
            val upcoming = events
                .filter { it.event.endAt >= now }
                .minByOrNull { it.event.startAt }
            val chosen = initial?.event?.id
                ?: open?.event?.id
                ?: upcoming?.event?.id
                ?: events.firstOrNull()?.event?.id
            _local.update { it.copy(selectedEventId = chosen) }
        }
    }

    fun selectEvent(eventId: Long) {
        _local.update { it.copy(selectedEventId = eventId, feedback = null) }
    }

    fun clearFeedback() {
        _local.update { it.copy(feedback = null) }
    }

    fun accredit(rawInput: String, useLocation: Boolean) {
        if (_local.value.busy) return
        val eventId = _local.value.selectedEventId
        if (eventId == null) {
            _local.update { it.copy(feedback = AccreditFeedback.Error("Selecciona un evento primero.")) }
            return
        }
        _local.update { it.copy(busy = true, feedback = null) }
        viewModelScope.launch {
            try {
                val (latitude, longitude) = captureLocation(useLocation)
                val result = accreditationRepository.accredit(
                    eventId = eventId,
                    rawInput = rawInput,
                    method = AttendanceMethod.MANUAL,
                    latitude = latitude,
                    longitude = longitude
                )
                _local.update { it.copy(busy = false, feedback = result.toFeedback()) }
            } catch (error: Exception) {
                _local.update {
                    it.copy(
                        busy = false,
                        feedback = AccreditFeedback.Error(
                            "No se pudo acreditar: ${error.message ?: "error desconocido"}"
                        )
                    )
                }
            }
        }
    }

    fun registerWalkIn(name: String, document: String, email: String, useLocation: Boolean) {
        if (_local.value.busy) return
        val eventId = _local.value.selectedEventId
        if (eventId == null) {
            _local.update { it.copy(feedback = AccreditFeedback.Error("Selecciona un evento primero.")) }
            return
        }
        _local.update { it.copy(busy = true, feedback = null) }
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
                _local.update { it.copy(busy = false, feedback = result.toFeedback()) }
            } catch (error: Exception) {
                _local.update {
                    it.copy(
                        busy = false,
                        feedback = AccreditFeedback.Error(
                            "No se pudo registrar el ingreso: ${error.message ?: "error desconocido"}"
                        )
                    )
                }
            }
        }
    }

    fun removeAttendance(attendanceId: Long) {
        viewModelScope.launch {
            if (accreditationRepository.removeAttendance(attendanceId)) {
                _messages.emit("Acreditación anulada. La encuesta asociada también fue eliminada.")
            } else {
                _messages.emit("No se pudo anular la acreditación.")
            }
        }
    }

    private suspend fun captureLocation(useLocation: Boolean): Pair<Double?, Double?> {
        if (!useLocation || !locationProvider.hasPermission()) return null to null
        val location = locationProvider.getCurrentLocation()
        return location?.latitude to location?.longitude
    }
}
