package cl.syncpass.app.data.repository

import cl.syncpass.app.data.local.AttendanceDao
import cl.syncpass.app.data.local.PendingSurveyRow
import cl.syncpass.app.data.local.RatingCount
import cl.syncpass.app.data.local.SurveyContextRow
import cl.syncpass.app.data.local.SurveyDao
import cl.syncpass.app.data.local.SurveyEntity
import cl.syncpass.app.data.local.SurveyListRow
import cl.syncpass.app.data.local.SurveyStatsRow
import cl.syncpass.app.domain.SurveyRules
import kotlinx.coroutines.flow.Flow

sealed interface SubmitSurveyResult {
    data class Success(val survey: SurveyEntity, val created: Boolean) : SubmitSurveyResult
    data class Invalid(val errors: Map<String, String>) : SubmitSurveyResult
    data class NotAllowed(val message: String) : SubmitSurveyResult
    data class Failure(val message: String) : SubmitSurveyResult
}

class SurveyRepository(
    private val attendanceDao: AttendanceDao,
    private val surveyDao: SurveyDao,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    fun observePending(): Flow<List<PendingSurveyRow>> = attendanceDao.observePendingSurveys()

    fun observeCompleted(): Flow<List<SurveyListRow>> = surveyDao.observeCompleted()

    fun observeByEvent(eventId: Long): Flow<List<SurveyListRow>> =
        surveyDao.observeByEvent(eventId)

    fun observeStats(eventId: Long): Flow<SurveyStatsRow> = surveyDao.observeEventStats(eventId)

    fun observeDistribution(eventId: Long): Flow<List<RatingCount>> =
        surveyDao.observeDistribution(eventId)

    suspend fun getContext(attendanceId: Long): SurveyContextRow? =
        attendanceDao.getSurveyContext(attendanceId)

    suspend fun getByAttendance(attendanceId: Long): SurveyEntity? =
        surveyDao.getByAttendance(attendanceId)

    suspend fun submit(
        attendanceId: Long,
        satisfaction: Int,
        usefulness: Int,
        comment: String
    ): SubmitSurveyResult {
        val context = attendanceDao.getSurveyContext(attendanceId)
            ?: return SubmitSurveyResult.NotAllowed(
                "La acreditación ya no existe. La encuesta no se puede registrar."
            )
        if (!context.eventSurveyEnabled) {
            return SubmitSurveyResult.NotAllowed("Las encuestas están deshabilitadas para este evento.")
        }
        if (!context.eventSurveyReleased) {
            return SubmitSurveyResult.NotAllowed("La encuesta aún no está liberada por el monitor.")
        }

        val errors = SurveyRules.validate(satisfaction, usefulness, comment)
        if (errors.isNotEmpty()) return SubmitSurveyResult.Invalid(errors)

        val cleanComment = comment.trim()
        val now = nowProvider()
        return try {
            val existing = surveyDao.getByAttendance(attendanceId)
            if (existing == null) {
                val survey = SurveyEntity(
                    attendanceId = attendanceId,
                    eventId = context.eventId,
                    participantId = context.participantId,
                    satisfaction = satisfaction,
                    usefulness = usefulness,
                    comment = cleanComment,
                    submittedAt = now
                )
                val id = surveyDao.insert(survey)
                SubmitSurveyResult.Success(survey.copy(id = id), created = true)
            } else {
                val updated = existing.copy(
                    satisfaction = satisfaction,
                    usefulness = usefulness,
                    comment = cleanComment,
                    submittedAt = now,
                    sincronizado = false
                )
                surveyDao.update(updated)
                SubmitSurveyResult.Success(updated, created = false)
            }
        } catch (error: Exception) {
            SubmitSurveyResult.Failure(
                "No se pudo guardar la encuesta: ${error.message ?: "error desconocido"}"
            )
        }
    }
}
