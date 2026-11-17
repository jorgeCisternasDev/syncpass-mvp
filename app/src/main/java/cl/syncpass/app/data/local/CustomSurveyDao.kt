package cl.syncpass.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomSurveyDao {
    @Query(
        """
        SELECT d.*, e.name AS eventName,
            (SELECT COUNT(*) FROM survey_questions q WHERE q.surveyId = d.id) AS questionCount,
            (SELECT COUNT(*) FROM survey_responses r WHERE r.surveyId = d.id) AS responseCount
        FROM survey_definitions d
        LEFT JOIN events e ON e.id = d.eventId
        ORDER BY d.createdAt DESC
        """
    )
    fun observeAllWithStats(): Flow<List<SurveyDefinitionWithStats>>

    @Query(
        """
        SELECT d.*, e.name AS eventName,
            (SELECT COUNT(*) FROM survey_questions q WHERE q.surveyId = d.id) AS questionCount,
            (SELECT COUNT(*) FROM survey_responses r WHERE r.surveyId = d.id) AS responseCount
        FROM survey_definitions d
        LEFT JOIN events e ON e.id = d.eventId
        WHERE d.id = :surveyId
        """
    )
    fun observeByIdWithStats(surveyId: Long): Flow<SurveyDefinitionWithStats?>

    @Query(
        """
        SELECT d.*, e.name AS eventName,
            (SELECT COUNT(*) FROM survey_questions q WHERE q.surveyId = d.id) AS questionCount,
            (SELECT COUNT(*) FROM survey_responses r WHERE r.surveyId = d.id) AS responseCount
        FROM survey_definitions d
        LEFT JOIN events e ON e.id = d.eventId
        WHERE d.id = :surveyId
        """
    )
    suspend fun getByIdWithStats(surveyId: Long): SurveyDefinitionWithStats?

    @Query("SELECT * FROM survey_definitions WHERE id = :surveyId")
    suspend fun getById(surveyId: Long): SurveyDefinitionEntity?

    @Query("SELECT * FROM survey_definitions WHERE eventId = :eventId ORDER BY id LIMIT 1")
    suspend fun getByEvent(eventId: Long): SurveyDefinitionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDefinition(definition: SurveyDefinitionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQuestions(questions: List<SurveyQuestionEntity>)

    @Query("SELECT * FROM survey_questions WHERE surveyId = :surveyId ORDER BY position ASC")
    fun observeQuestions(surveyId: Long): Flow<List<SurveyQuestionEntity>>

    @Query("SELECT * FROM survey_questions WHERE surveyId = :surveyId ORDER BY position ASC")
    suspend fun getQuestions(surveyId: Long): List<SurveyQuestionEntity>

    @Query("UPDATE survey_definitions SET isActive = :active WHERE id = :surveyId")
    suspend fun setActive(surveyId: Long, active: Boolean)

    @Query("DELETE FROM survey_definitions WHERE id = :surveyId")
    suspend fun deleteById(surveyId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertResponse(response: SurveyResponseEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAnswers(answers: List<SurveyAnswerEntity>)

    @Query(
        """
        SELECT r.id AS responseId, r.respondentName AS respondentName, r.submittedAt AS submittedAt,
            q.id AS questionId, q.prompt AS questionPrompt, q.type AS questionType, q.position AS position,
            a.textValue AS textValue, a.ratingValue AS ratingValue
        FROM survey_responses r
        INNER JOIN survey_answers a ON a.responseId = r.id
        INNER JOIN survey_questions q ON q.id = a.questionId
        WHERE r.surveyId = :surveyId
        ORDER BY r.submittedAt DESC, q.position ASC
        """
    )
    fun observeAnswerRows(surveyId: Long): Flow<List<SurveyAnswerRow>>
}
