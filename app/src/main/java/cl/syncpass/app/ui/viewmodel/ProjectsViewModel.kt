package cl.syncpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.data.local.ProjectWithCount
import cl.syncpass.app.data.repository.ProjectRepository
import cl.syncpass.app.data.repository.ProjectResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val loading: Boolean = true,
    val projects: List<ProjectWithCount> = emptyList()
)

class ProjectsViewModel(private val projectRepository: ProjectRepository) : ViewModel() {

    val state: StateFlow<ProjectsUiState> = projectRepository.observeProjects()
        .catch { emit(emptyList()) }
        .map { ProjectsUiState(loading = false, projects = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProjectsUiState()
        )

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun save(projectId: Long?, name: String, description: String) {
        viewModelScope.launch {
            when (val result = projectRepository.save(projectId, name, description)) {
                is ProjectResult.Success -> _saved.emit(Unit)
                is ProjectResult.Invalid -> _messages.emit(result.message)
                is ProjectResult.Failure -> _messages.emit(result.message)
            }
        }
    }

    fun delete(projectId: Long) {
        viewModelScope.launch {
            if (projectRepository.delete(projectId)) {
                _messages.emit("Proyecto eliminado. Sus eventos quedaron sin proyecto.")
            } else {
                _messages.emit("No se pudo eliminar el proyecto.")
            }
        }
    }
}
