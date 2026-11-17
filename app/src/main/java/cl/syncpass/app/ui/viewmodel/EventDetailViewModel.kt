package cl.syncpass.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.AttendanceMethod
import cl.syncpass.app.data.local.AttendanceRow
import cl.syncpass.app.data.local.EventWithStats
import cl.syncpass.app.data.local.ParticipantStatusRow
import cl.syncpass.app.data.local.SurveyListRow
import cl.syncpass.app.data.repository.AccreditResult
import cl.syncpass.app.data.repository.AccreditationRepository
import cl.syncpass.app.data.repository.AddParticipantResult
import cl.syncpass.app.data.repository.DeleteParticipantResult
import cl.syncpass.app.data.repository.EventRepository
import cl.syncpass.app.data.repository.ExportRepository
import cl.syncpass.app.data.repository.ExportResult
import cl.syncpass.app.data.repository.ParticipantRepository
import cl.syncpass.app.data.repository.ProjectRepository
import cl.syncpass.app.data.repository.SurveyRepository
import cl.syncpass.app.util.Formatters
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EventDetailUiState(
    val loading: Boolean = true,
    val eventWithStats: EventWithStats? = null,
    val participants: List<ParticipantStatusRow> = emptyList(),
    val attendance: List<AttendanceRow> = emptyList(),
    val surveys: List<SurveyListRow> = emptyList(),
    val projectName: String? = null
) {
    val event get() = eventWithStats?.event

    val pendingSurveyCount: Int
        get() = if (event?.surveyEnabled == true && event?.surveyReleased == true) {
            participants.count { it.attendanceId != null && it.surveyId == null }
        } else {
            0
        }

    val walkInCount: Int get() = participants.count { it.participant.isWalkIn }
}

data class AddParticipantUiState(
    val saving: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val message: String? = null,
    val successVersion: Int = 0
)

data class ExportUiState(
    val exporting: Boolean = false,
    val message: String? = null
)

class EventDetailViewModel(
    private val eventId: Long,
    private val eventRepository: EventRepository,
    private val participantRepository: ParticipantRepository,
    private val accreditationRepository: AccreditationRepository,
    surveyRepository: SurveyRepository,
    projectRepository: ProjectRepository,
    private val exportRepository: ExportRepository
) : ViewModel() {

    private val _addState = MutableStateFlow(AddParticipantUiState())
    val addState: StateFlow<AddParticipantUiState> = _addState

    private val _exportState = MutableStateFlow(ExportUiState())
    val exportState: StateFlow<ExportUiState> = _exportState

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val accreditingParticipants = mutableSetOf<Long>()

    fun accreditParticipant(row: ParticipantStatusRow) {
        if (row.attendanceId != null) {
            _messages.tryEmit("${row.participant.name} ya está acreditado.")
            return
        }
        if (!accreditingParticipants.add(row.participant.id)) return

        viewModelScope.launch {
            try {
                val result = accreditationRepository.accredit(
                    eventId = eventId,
                    rawInput = row.participant.code,
                    method = AttendanceMethod.MANUAL
                )
                val message = when (result) {
                    is AccreditResult.Success ->
                        "Acreditación registrada: ${result.participant.name}"

                    is AccreditResult.Duplicate ->
                        "${result.participant.name} ya está acreditado."

                    is AccreditResult.NotOpen ->
                        "La acreditación abre el ${Formatters.dateTime(result.opensAt)}."

                    is AccreditResult.Closed ->
                        "El evento finalizó el ${Formatters.dateTime(result.closedAt)}."

                    is AccreditResult.EventMismatch ->
                        "El código pertenece a otro evento."

                    is AccreditResult.InvalidQr -> result.message
                    is AccreditResult.InvalidInput -> result.message
                    is AccreditResult.NotFound -> "No se encontró a ${row.participant.name}."
                    is AccreditResult.Conflict -> result.message
                    is AccreditResult.Failure -> result.message
                    is AccreditResult.NeedsRegistration -> "No se pudo acreditar."
                }
                _messages.emit(message)
            } finally {
                accreditingParticipants.remove(row.participant.id)
            }
        }
    }

    val state: StateFlow<EventDetailUiState> = combine(
        eventRepository.observeEvent(eventId),
        participantRepository.observeByEvent(eventId),
        accreditationRepository.observeAttendance(eventId),
        surveyRepository.observeByEvent(eventId),
        projectRepository.observeOptions()
    ) { eventWithStats, participants, attendance, surveys, projects ->
        EventDetailUiState(
            loading = false,
            eventWithStats = eventWithStats,
            participants = participants,
            attendance = attendance,
            surveys = surveys,
            projectName = eventWithStats?.event?.projectId?.let { projectId ->
                projects.firstOrNull { it.id == projectId }?.name
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EventDetailUiState()
    )

    fun resetAddState() {
        _addState.value = AddParticipantUiState()
    }

    fun addParticipant(name: String, document: String, email: String) {
        if (_addState.value.saving) return
        _addState.update { it.copy(saving = true, errors = emptyMap(), message = null) }
        viewModelScope.launch {
            when (val result = participantRepository.add(eventId, name, document, email)) {
                is AddParticipantResult.Success -> _addState.update {
                    it.copy(saving = false, successVersion = it.successVersion + 1)
                }

                is AddParticipantResult.Invalid -> _addState.update {
                    it.copy(saving = false, errors = result.errors)
                }

                is AddParticipantResult.Conflict -> _addState.update {
                    it.copy(saving = false, message = result.message)
                }

                is AddParticipantResult.Failure -> _addState.update {
                    it.copy(saving = false, message = result.message)
                }
            }
        }
    }

    fun deleteParticipant(participantId: Long) {
        viewModelScope.launch {
            when (val result = participantRepository.delete(participantId)) {
                DeleteParticipantResult.Success -> _messages.emit("Participante eliminado.")
                is DeleteParticipantResult.Blocked -> _messages.emit(result.message)
                is DeleteParticipantResult.Failure -> _messages.emit(result.message)
            }
        }
    }

    fun releaseSurvey() {
        val event = state.value.event ?: return
        if (event.surveyReleased) return
        viewModelScope.launch {
            if (eventRepository.releaseSurvey(eventId)) {
                _messages.emit("Encuesta liberada. Los participantes acreditados ya pueden responder.")
            } else {
                _messages.emit("No se pudo liberar la encuesta.")
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

    fun export(uri: Uri) {
        if (_exportState.value.exporting) return
        _exportState.value = ExportUiState(exporting = true)
        viewModelScope.launch {
            when (val result = exportRepository.writeCsv(uri, eventId)) {
                is ExportResult.Success -> _exportState.value = ExportUiState(
                    exporting = false,
                    message = "Se exportaron ${result.rows} registros a CSV."
                )

                is ExportResult.Failure -> _exportState.value = ExportUiState(
                    exporting = false,
                    message = result.message
                )
            }
        }
    }

    fun clearExportMessage() {
        _exportState.update { it.copy(message = null) }
    }
}
