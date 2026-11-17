package cl.syncpass.app

import cl.syncpass.app.domain.SurveyReleaseRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyReleaseRulesTest {
    private val end = 1_000_000L

    @Test
    fun notReleasedBeforeEventEnd() {
        assertFalse(SurveyReleaseRules.isReleased(false, end, end - 1))
        assertFalse(SurveyReleaseRules.shouldAutoRelease(false, end, end - 1))
    }

    @Test
    fun releasedAutomaticallyAtEventEnd() {
        assertTrue(SurveyReleaseRules.isReleased(false, end, end))
        assertTrue(SurveyReleaseRules.shouldAutoRelease(false, end, end))
        assertTrue(SurveyReleaseRules.isReleased(false, end, end + 5_000))
    }

    @Test
    fun manualReleaseWinsBeforeEventEnd() {
        assertTrue(SurveyReleaseRules.isReleased(true, end, end - 1))
        assertFalse(SurveyReleaseRules.shouldAutoRelease(true, end, end - 1))
    }

    @Test
    fun alreadyReleasedIsNotAutoReleasedAgain() {
        assertTrue(SurveyReleaseRules.isReleased(true, end, end + 1))
        assertFalse(SurveyReleaseRules.shouldAutoRelease(true, end, end + 1))
    }
}
