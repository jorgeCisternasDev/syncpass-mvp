package cl.syncpass.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.AppDatabase
import cl.syncpass.app.data.local.StatsDao
import cl.syncpass.app.data.repository.ExportRepository
import cl.syncpass.app.data.repository.ExportResult
import cl.syncpass.app.data.repository.SyncRepository
import cl.syncpass.app.data.repository.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DataCounts(
    val events: Int = 0,
    val participants: Int = 0,
    val attendance: Int = 0,
    val surveys: Int = 0
)

private data class SettingsActions(
    val exporting: Boolean = false,
    val clearing: Boolean = false,
    val syncing: Boolean = false
)

data class SettingsUiState(
    val loading: Boolean = true,
    val eventCount: Int = 0,
    val participantCount: Int = 0,
    val attendanceCount: Int = 0,
    val surveyCount: Int = 0,
    val pendingSyncCount: Int = 0,
    val exporting: Boolean = false,
    val clearing: Boolean = false,
    val syncing: Boolean = false
)

class SettingsViewModel(
    statsDao: StatsDao,
    private val exportRepository: ExportRepository,
    private val database: AppDatabase,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val actions = MutableStateFlow(SettingsActions())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val counts = combine(
        statsDao.observeEventCount().catch { emit(0) },
        statsDao.observeParticipantCount().catch { emit(0) },
        statsDao.observeAttendanceCount().catch { emit(0) },
        statsDao.observeSurveyCount().catch { emit(0) }
    ) { events, participants, attendance, surveys ->
        DataCounts(events, participants, attendance, surveys)
    }

    val state: StateFlow<SettingsUiState> = combine(
        counts,
        actions,
        syncRepository.observePendingCount().catch { emit(0) }
    ) { data, current, pending ->
        SettingsUiState(
            loading = false,
            eventCount = data.events,
            participantCount = data.participants,
            attendanceCount = data.attendance,
            surveyCount = data.surveys,
            pendingSyncCount = pending,
            exporting = current.exporting,
            clearing = current.clearing,
            syncing = current.syncing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun exportAll(uri: Uri) {
        if (actions.value.exporting || actions.value.clearing) return
        actions.update { it.copy(exporting = true) }
        viewModelScope.launch {
            when (val result = exportRepository.writeCsv(uri, null)) {
                is ExportResult.Success -> _messages.emit(
                    "Se exportaron ${result.rows} registros de todos los eventos."
                )

                is ExportResult.Failure -> _messages.emit(result.message)
            }
            actions.update { it.copy(exporting = false) }
        }
    }

    fun syncNow() {
        if (actions.value.syncing) return
        actions.update { it.copy(syncing = true) }
        viewModelScope.launch {
            when (val result = syncRepository.sync()) {
                SyncResult.NothingPending -> _messages.emit(
                    "No hay acreditaciones ni encuestas pendientes de envio."
                )

                is SyncResult.Success -> _messages.emit(
                    "${result.accepted} registros aceptados por el servidor " +
                        "(${result.sent} enviados)."
                )

                is SyncResult.Failure -> _messages.emit(result.message)
            }
            actions.update { it.copy(syncing = false) }
        }
    }

    fun clearAllData() {
        if (actions.value.clearing || actions.value.exporting) return
        actions.update { it.copy(clearing = true) }
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching { database.clearAllTables() }.exceptionOrNull()
            }
            actions.update { it.copy(clearing = false) }
            if (error == null) {
                _messages.emit("Se eliminaron todos los datos locales del dispositivo.")
            } else {
                _messages.emit("No se pudieron eliminar los datos: ${error.message ?: "error desconocido"}")
            }
        }
    }
}
