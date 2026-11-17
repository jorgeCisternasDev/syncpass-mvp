package cl.syncpass.app.data.repository

import cl.syncpass.app.data.local.ProjectDao
import cl.syncpass.app.data.local.ProjectEntity
import cl.syncpass.app.data.local.ProjectWithCount
import kotlinx.coroutines.flow.Flow

sealed interface ProjectResult {
    data class Success(val id: Long) : ProjectResult
    data class Invalid(val message: String) : ProjectResult
    data class Failure(val message: String) : ProjectResult
}

class ProjectRepository(private val projectDao: ProjectDao) {
    fun observeProjects(): Flow<List<ProjectWithCount>> = projectDao.observeAllWithCount()

    fun observeOptions(): Flow<List<ProjectEntity>> = projectDao.observeAll()

    suspend fun getById(projectId: Long): ProjectEntity? = projectDao.getById(projectId)

    suspend fun save(projectId: Long?, name: String, description: String): ProjectResult {
        val cleanName = name.trim()
        val cleanDescription = description.trim()
        if (cleanName.length < 2) return ProjectResult.Invalid("El nombre debe tener al menos 2 caracteres.")
        if (cleanName.length > 80) return ProjectResult.Invalid("El nombre no puede superar 80 caracteres.")
        if (cleanDescription.length > 300) {
            return ProjectResult.Invalid("La descripción no puede superar 300 caracteres.")
        }

        return try {
            if (projectId == null) {
                val id = projectDao.insert(
                    ProjectEntity(name = cleanName, description = cleanDescription)
                )
                ProjectResult.Success(id)
            } else {
                val existing = projectDao.getById(projectId)
                    ?: return ProjectResult.Failure("El proyecto ya no existe.")
                projectDao.update(existing.copy(name = cleanName, description = cleanDescription))
                ProjectResult.Success(projectId)
            }
        } catch (error: Exception) {
            ProjectResult.Failure("No se pudo guardar el proyecto: ${error.message ?: "error desconocido"}")
        }
    }

    suspend fun delete(projectId: Long): Boolean {
        val project = projectDao.getById(projectId) ?: return false
        return try {
            projectDao.delete(project)
            true
        } catch (_: Exception) {
            false
        }
    }
}
