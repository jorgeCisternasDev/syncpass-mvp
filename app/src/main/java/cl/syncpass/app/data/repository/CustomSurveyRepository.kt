package cl.syncpass.app.data.repository

import androidx.room.withTransaction
import cl.syncpass.app.data.local.AppDatabase
import cl.syncpass.app.data.local.CustomSurveyDao
import cl.syncpass.app.data.local.EventDao
import cl.syncpass.app.data.local.SurveyAnswerEntity
import cl.syncpass.app.data.local.SurveyAnswerRow
import cl.syncpass.app.data.local.SurveyDefinitionEntity
import cl.syncpass.app.data.local.SurveyDefinitionWithStats
import cl.syncpass.app.data.local.SurveyQuestionEntity
import cl.syncpass.app.data.local.SurveyQuestionType
import cl.syncpass.app.data.local.SurveyResponseEntity
import cl.syncpass.app.domain.SurveyDefinitionRules
import cl.syncpass.app.domain.SurveyQuestionInput
import kotlinx.coroutines.flow.Flow

data class SurveyAnswerInput(
    val text: String? = null,
    val rating: Int? = null
)

sealed interface CreateSurveyResult {
    data class Success(val surveyId: Long) : CreateSurveyResult
    data class Invalid(val errors: Map<String, String>) : CreateSurveyResult
    data class Failure(val message: String) : CreateSurveyResult
}

sealed interface SubmitSurveyResponseResult {
    data class Success(val responseId: Long) : SubmitSurveyResponseResult
    data class Invalid(val errors: Map<String, String>) : SubmitSurveyResponseResult
    data class NotAllowed(val message: String) : SubmitSurveyResponseResult
    data class Failure(val message: String) : SubmitSurveyResponseResult
}

class CustomSurveyRepository(
    private val database: AppDatabase,
    private val dao: CustomSurveyDao,
    private val eventDao: EventDao
) {
    fun observeDefinitions(): Flow<List<SurveyDefinitionWithStats>> =
        dao.observeAllWithStats()

    fun observeDefinition(surveyId: Long): Flow<SurveyDefinitionWithStats?> =
        dao.observeByIdWithStats(surveyId)

    fun observeQuestions(surveyId: Long): Flow<List<SurveyQuestionEntity>> =
        dao.observeQuestions(surveyId)

    fun observeAnswerRows(surveyId: Long): Flow<List<SurveyAnswerRow>> =
        dao.observeAnswerRows(surveyId)

    suspend fun getDefinition(surveyId: Long): SurveyDefinitionWithStats? =
        dao.getByIdWithStats(surveyId)

    suspend fun getQuestions(surveyId: Long): List<SurveyQuestionEntity> =
        dao.getQuestions(surveyId)

    suspend fun create(
        eventId: Long?,
        title: String,
        description: String,
        questions: List<SurveyQuestionInput>
    ): CreateSurveyResult {
        val errors = SurveyDefinitionRules.validate(title, description, questions).toMutableMap()
        if (eventId != null && eventDao.getById(eventId) == null) {
            errors["event"] = "El evento seleccionado ya no existe."
        }
        if (errors.isNotEmpty()) return CreateSurveyResult.Invalid(errors)

        return try {
            val surveyId = database.withTransaction {
                val id = dao.insertDefinition(
                    SurveyDefinitionEntity(
                        eventId = eventId,
                        title = title.trim(),
                        description = description.trim(),
                        isActive = true
                    )
                )
                dao.insertQuestions(
                    questions.mapIndexed { index, question ->
                        SurveyQuestionEntity(
                            surveyId = id,
                            prompt = question.prompt.trim(),
                            type = question.type,
                            required = question.required,
                            position = index
                        )
                    }
                )
                id
            }
            CreateSurveyResult.Success(surveyId)
        } catch (error: Exception) {
            CreateSurveyResult.Failure(
                "No se pudo crear la encuesta: ${error.message ?: "error desconocido"}"
            )
        }
    }

    suspend fun submit(
        surveyId: Long,
        respondentName: String,
        answers: Map<Long, SurveyAnswerInput>
    ): SubmitSurveyResponseResult {
        val survey = dao.getById(surveyId)
            ?: return SubmitSurveyResponseResult.NotAllowed("La encuesta ya no existe.")
        if (!survey.isActive) {
            return SubmitSurveyResponseResult.NotAllowed("La encuesta está pausada y no recibe respuestas.")
        }
        val questions = dao.getQuestions(surveyId)
        if (questions.isEmpty()) {
            return SubmitSurveyResponseResult.NotAllowed("La encuesta no tiene preguntas.")
        }

        val errors = linkedMapOf<String, String>()
        questions.forEach { question ->
            val answer = answers[question.id] ?: SurveyAnswerInput()
            when (question.type) {
                SurveyQuestionType.RATING -> {
                    val rating = answer.rating
                    if (question.required && rating == null) {
                        errors["q_${question.id}"] = "Selecciona una nota de 1 a 5."
                    } else if (rating != null && rating !in 1..5) {
                        errors["q_${question.id}"] = "La nota debe estar entre 1 y 5."
                    }
                }

                else -> {
                    val text = answer.text?.trim().orEmpty()
                    if (question.required && text.isBlank()) {
                        errors["q_${question.id}"] = "Esta pregunta es obligatoria."
                    }
                    if (text.length > 1_000) {
                        errors["q_${question.id}"] = "La respuesta no puede superar 1000 caracteres."
                    }
                }
            }
        }
        if (errors.isNotEmpty()) return SubmitSurveyResponseResult.Invalid(errors)

        return try {
            val responseId = database.withTransaction {
                val id = dao.insertResponse(
                    SurveyResponseEntity(
                        surveyId = surveyId,
                        respondentName = respondentName.trim().take(80)
                    )
                )
                dao.insertAnswers(
                    questions.map { question ->
                        val answer = answers[question.id] ?: SurveyAnswerInput()
                        SurveyAnswerEntity(
                            responseId = id,
                            questionId = question.id,
                            textValue = if (question.type == SurveyQuestionType.TEXT) {
                                answer.text?.trim()?.takeIf { it.isNotEmpty() }
                            } else {
                                null
                            },
                            ratingValue = if (question.type == SurveyQuestionType.RATING) {
                                answer.rating
                            } else {
                                null
                            }
                        )
                    }
                )
                id
            }
            SubmitSurveyResponseResult.Success(responseId)
        } catch (error: Exception) {
            SubmitSurveyResponseResult.Failure(
                "No se pudo guardar la respuesta: ${error.message ?: "error desconocido"}"
            )
        }
    }

    suspend fun setActive(surveyId: Long, active: Boolean): Boolean = try {
        dao.setActive(surveyId, active)
        true
    } catch (_: Exception) {
        false
    }

    suspend fun delete(surveyId: Long): Boolean = try {
        dao.deleteById(surveyId)
        true
    } catch (_: Exception) {
        false
    }
}
