package cl.syncpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.SurveyContextRow
import cl.syncpass.app.data.repository.SubmitSurveyResult
import cl.syncpass.app.data.repository.SurveyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SurveyFormUiState(
    val loading: Boolean = true,
    val context: SurveyContextRow? = null,
    val satisfaction: Int = 0,
    val usefulness: Int = 0,
    val comment: String = "",
    val errors: Map<String, String> = emptyMap(),
    val message: String? = null,
    val saving: Boolean = false,
    val isEditing: Boolean = false,
    val saved: Boolean = false,
    val loadError: String? = null
)

class SurveyFormViewModel(
    private val attendanceId: Long,
    private val surveyRepository: SurveyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SurveyFormUiState())
    val state: StateFlow<SurveyFormUiState> = _state

    init {
        viewModelScope.launch {
            val context = surveyRepository.getContext(attendanceId)
            if (context == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        loadError = "No encontramos la acreditación asociada a esta encuesta."
                    )
                }
                return@launch
            }
            val existing = surveyRepository.getByAttendance(attendanceId)
            _state.update {
                it.copy(
                    loading = false,
                    context = context,
                    satisfaction = existing?.satisfaction ?: 0,
                    usefulness = existing?.usefulness ?: 0,
                    comment = existing?.comment.orEmpty(),
                    isEditing = existing != null,
                    loadError = if (context.eventSurveyEnabled) null else {
                        "Las encuestas están deshabilitadas para este evento."
                    }
                )
            }
        }
    }

    fun setSatisfaction(value: Int) = _state.update {
        it.copy(satisfaction = value, errors = it.errors - "satisfaction", message = null)
    }

    fun setUsefulness(value: Int) = _state.update {
        it.copy(usefulness = value, errors = it.errors - "usefulness", message = null)
    }

    fun setComment(value: String) = _state.update {
        it.copy(
            comment = value.take(600),
            errors = it.errors - "comment",
            message = null
        )
    }

    fun submit() {
        val current = _state.value
        if (current.saving || current.saved) return
        if (current.context == null) {
            _state.update { it.copy(loadError = "No se puede guardar la encuesta sin acreditación.") }
            return
        }
        _state.update { it.copy(saving = true, errors = emptyMap(), message = null) }
        viewModelScope.launch {
            when (val result = surveyRepository.submit(
                attendanceId = attendanceId,
                satisfaction = current.satisfaction,
                usefulness = current.usefulness,
                comment = current.comment
            )) {
                is SubmitSurveyResult.Success -> _state.update {
                    it.copy(saving = false, saved = true)
                }

                is SubmitSurveyResult.Invalid -> _state.update {
                    it.copy(saving = false, errors = result.errors)
                }

                is SubmitSurveyResult.NotAllowed -> _state.update {
                    it.copy(saving = false, message = result.message)
                }

                is SubmitSurveyResult.Failure -> _state.update {
                    it.copy(saving = false, message = result.message)
                }
            }
        }
    }
}
