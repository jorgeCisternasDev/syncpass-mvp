package cl.syncpass.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.syncpass.app.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<SyncPassApp>()
        db = app.container.database
        runBlocking { db.clearAllTables() }
        waitForTag("login_username")
        composeRule.onNodeWithTag("login_username").performTextInput("monitor")
        composeRule.onNodeWithTag("login_password").performTextInput("1234")
        composeRule.onNodeWithTag("login_button").performClick()
        waitForText("Crea tu primer evento", timeoutMs = 15_000)
    }

    @Test
    fun createEventReleaseRegisterAccreditAndSeeProgress() {
        scrollTo("home_scroll", "home_new_event")
        composeRule.onNodeWithTag("home_new_event").performClick()
        waitForTag("event_name")
        composeRule.onNodeWithTag("event_name").performTextInput("Taller de prueba")
        scrollTo("event_form_scroll", "event_venue")
        composeRule.onNodeWithTag("event_venue").performTextInput("Sede de prueba")
        scrollTo("event_form_scroll", "event_save")
        composeRule.onNodeWithTag("event_save").performClick()

        waitForText("Taller de prueba")
        val event = runBlocking { db.eventDao().observeAllWithStats().first().firstOrNull() }
        assertNotNull(event)
        val eventId = event!!.event.id
        waitForTag("event_card_$eventId")
        composeRule.onNodeWithTag("event_card_$eventId").performClick()
        waitForText("Detalle del evento")

        scrollTo("event_detail_scroll", "survey_release_button")
        composeRule.onNodeWithTag("survey_release_button").performClick()
        waitForText("LIBERADA")

        scrollTo("event_detail_scroll", "add_participant")
        composeRule.onNodeWithTag("add_participant").performClick()
        waitForTag("participant_name")
        composeRule.onNodeWithTag("participant_name").performTextInput("Ana Pérez")
        composeRule.onNodeWithTag("participant_document").performTextInput("12.345.678-9")
        composeRule.onNodeWithTag("participant_save").performClick()
        waitForText("Ana Pérez")

        val participant = runBlocking {
            db.participantDao().observeByEvent(eventId).first().firstOrNull()?.participant
        }
        assertNotNull(participant)
        val code = participant!!.code
        assertEquals("123456789", participant.documentNormalized)

        composeRule.onNodeWithContentDescription("Volver").performClick()
        waitForTag("nav_accredit")
        composeRule.onNodeWithTag("nav_accredit").performClick()
        waitForText("Acreditar asistencia")
        waitForTag("accredit_event_selector")
        waitForText("Taller de prueba")
        scrollTo("accredit_scroll", "accredit_code")
        composeRule.onNodeWithTag("accredit_code").performTextInput(code)
        Espresso.closeSoftKeyboard()
        composeRule.waitForIdle()
        scrollTo("accredit_scroll", "accredit_manual_button")
        composeRule.onNodeWithTag("accredit_manual_button").assertIsEnabled()
        composeRule.onNodeWithTag("accredit_manual_button").performClick()
        try {
            waitForText("Acreditación registrada", timeoutMs = 15_000)
        } catch (error: Throwable) {
            composeRule.onRoot().printToLog("MainFlowTest")
            val currentAttendance = runBlocking {
                db.attendanceDao().getByParticipant(participant.id)
            }
            throw AssertionError(
                "No apareció la acreditación. attendance=$currentAttendance, code=$code",
                error
            )
        }

        val attendance = runBlocking { db.attendanceDao().getByParticipant(participant.id) }
        assertNotNull(attendance)
        assertEquals(participant.id, attendance!!.participantId)

        scrollTo("accredit_scroll", "accredit_code")
        composeRule.onNodeWithTag("accredit_code").performTextInput(code)
        composeRule.onNodeWithTag("accredit_code").performImeAction()
        waitForText("ya estaba acreditado", timeoutMs = 15_000)

        composeRule.onNodeWithTag("nav_home").performClick()
        waitForTag("event_card_$eventId")
        composeRule.onNodeWithTag("event_card_$eventId").performClick()
        waitForText("Detalle del evento")
        waitForText("Acreditados")
        composeRule.onNodeWithTag("event_detail_scroll")
            .performScrollToNode(hasText("Acreditados"))
        composeRule.onNodeWithTag("event_detail_scroll")
            .performScrollToNode(hasText("LIBERADA"))

        assertEquals(
            0,
            composeRule.onAllNodesWithTag("indicators_button").fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("event_tab_surveys").fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("participant_mode_entry").fetchSemanticsNodes().size
        )

        val summary = runBlocking { db.eventDao().observeByIdWithStats(eventId).first() }
        assertEquals(1, summary?.registeredCount)
        assertEquals(1, summary?.attendedCount)
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
