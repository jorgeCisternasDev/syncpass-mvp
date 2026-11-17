package cl.syncpass.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.syncpass.app.data.local.AppDatabase
import cl.syncpass.app.data.local.AttendanceEntity
import cl.syncpass.app.data.local.AttendanceMethod
import cl.syncpass.app.data.local.EventEntity
import cl.syncpass.app.data.local.ParticipantEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParticipantHomeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var db: AppDatabase
    private var eventId = 0L
    private var attendanceId = 0L

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<SyncPassApp>()
        db = app.container.database
        val now = System.currentTimeMillis()
        runBlocking {
            db.clearAllTables()
            eventId = db.eventDao().insert(
                EventEntity(
                    name = "Taller de prueba",
                    venue = "Sede",
                    startAt = now - 60 * 60 * 1000L,
                    endAt = now + 3 * 60 * 60 * 1000L,
                    capacity = 30,
                    surveyEnabled = true,
                    surveyReleased = true
                )
            )
            val participantId = db.participantDao().insert(
                ParticipantEntity(
                    eventId = eventId,
                    code = "SPBENJ01",
                    name = "Benjamin Soto",
                    document = "20.111.222-3",
                    documentNormalized = "201112223"
                )
            )
            attendanceId = db.attendanceDao().insert(
                AttendanceEntity(
                    eventId = eventId,
                    participantId = participantId,
                    attendedAt = now,
                    method = AttendanceMethod.MANUAL
                )
            )
        }
        waitForTag("login_username")
        composeRule.onNodeWithTag("login_username").performTextInput("benjamin")
        composeRule.onNodeWithTag("login_password").performTextInput("1234")
        composeRule.onNodeWithTag("login_button").performClick()
    }

    @Test
    fun accreditedParticipantAnswersSurveyAndCoordinatorSeesIndicators() {
        waitForText("Benjamin Soto")
        waitForText("ACREDITADO")
        composeRule.onNodeWithTag("participant_survey_button").performClick()
        waitForText("Responder encuesta")

        scrollTo("survey_form_scroll", "Satisfacción-5")
        composeRule.onNodeWithTag("Satisfacción-5").performClick()
        scrollTo("survey_form_scroll", "Utilidad-4")
        composeRule.onNodeWithTag("Utilidad-4").performClick()
        Espresso.closeSoftKeyboard()
        scrollTo("survey_form_scroll", "survey_submit")
        composeRule.onNodeWithTag("survey_submit").performClick()
        waitForText("¡Gracias!", timeoutMs = 15_000)

        val survey = runBlocking { db.surveyDao().getByAttendance(attendanceId) }
        assertNotNull(survey)

        composeRule.onNodeWithText("Volver a mi espacio").performClick()
        waitForText("Encuesta respondida")

        composeRule.onNodeWithContentDescription("Cerrar sesión").performClick()
        waitForTag("login_username")
        composeRule.onNodeWithTag("login_username").performTextInput("coordinador")
        composeRule.onNodeWithTag("login_password").performTextInput("1234")
        composeRule.onNodeWithTag("login_button").performClick()
        waitForTag("coordinator_event_$eventId")
        composeRule.onNodeWithTag("coordinator_event_$eventId").performClick()
        waitForText("Indicadores")
        waitForText("COORDINADOR")
        waitForText("Encuestas respondidas")
        waitForText("5,0")
        waitForText("Benjamin Soto")

        assertTrue(
            composeRule.onAllNodesWithTag("event_detail_accredit").fetchSemanticsNodes().isEmpty()
        )
    }

    private fun waitForText(text: String, timeoutMs: Long = 10_000) {
        composeRule.waitUntil(timeoutMs) {
            composeRule.onAllNodesWithText(text, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTag(tag: String, timeoutMs: Long = 10_000) {
        composeRule.waitUntil(timeoutMs) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun scrollTo(scrollTag: String, targetTag: String) {
        composeRule.onNodeWithTag(scrollTag).performScrollToNode(hasTestTag(targetTag))
        composeRule.waitForIdle()
    }
}
