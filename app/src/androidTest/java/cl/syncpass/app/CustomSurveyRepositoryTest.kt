package cl.syncpass.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.syncpass.app.data.local.AppDatabase
import cl.syncpass.app.data.local.SurveyQuestionType
import cl.syncpass.app.data.repository.CreateSurveyResult
import cl.syncpass.app.data.repository.CustomSurveyRepository
import cl.syncpass.app.data.repository.SubmitSurveyResponseResult
import cl.syncpass.app.data.repository.SurveyAnswerInput
import cl.syncpass.app.domain.SurveyQuestionInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomSurveyRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: CustomSurveyRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = CustomSurveyRepository(
            database = db,
            dao = db.customSurveyDao(),
            eventDao = db.eventDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createAnswerPauseAndDeleteSurvey() = runBlocking {
        val created = repository.create(
            eventId = null,
            title = "Encuesta de prueba",
            description = "Descripción",
            questions = listOf(
                SurveyQuestionInput("¿Qué te pareció?", SurveyQuestionType.TEXT, true),
                SurveyQuestionInput("¿Cómo calificas la organización?", SurveyQuestionType.RATING, true)
            )
        )
        assertTrue(created is CreateSurveyResult.Success)
        val surveyId = (created as CreateSurveyResult.Success).surveyId

        val questions = repository.getQuestions(surveyId)
        assertEquals(2, questions.size)

        val submitted = repository.submit(
            surveyId = surveyId,
            respondentName = "Carolina",
            answers = mapOf(
                questions[0].id to SurveyAnswerInput(text = "Muy buena"),
                questions[1].id to SurveyAnswerInput(rating = 4)
            )
        )
        assertTrue(submitted is SubmitSurveyResponseResult.Success)

        val definition = repository.observeDefinitions().first().first()
        assertEquals(2, definition.questionCount)
        assertEquals(1, definition.responseCount)
        val answerRows = repository.observeAnswerRows(surveyId).first()
        assertEquals(2, answerRows.size)
        assertEquals("Muy buena", answerRows.first { it.questionType == SurveyQuestionType.TEXT }.textValue)
        assertEquals(4, answerRows.first { it.questionType == SurveyQuestionType.RATING }.ratingValue)

        assertTrue(repository.setActive(surveyId, false))
        val paused = repository.submit(
            surveyId = surveyId,
            respondentName = "",
            answers = questions.associate { it.id to SurveyAnswerInput(text = "x", rating = 5) }
        )
        assertTrue(paused is SubmitSurveyResponseResult.NotAllowed)

        assertTrue(repository.setActive(surveyId, true))
        val invalid = repository.submit(
            surveyId = surveyId,
            respondentName = "",
            answers = emptyMap()
        )
        assertTrue(invalid is SubmitSurveyResponseResult.Invalid)

        assertTrue(repository.delete(surveyId))
        assertTrue(repository.observeDefinitions().first().isEmpty())
        assertTrue(repository.observeAnswerRows(surveyId).first().isEmpty())
    }
}
