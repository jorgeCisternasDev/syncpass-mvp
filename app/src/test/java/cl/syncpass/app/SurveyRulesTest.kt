package cl.syncpass.app

import cl.syncpass.app.domain.SurveyRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyRulesTest {
    @Test
    fun acceptsValidSurvey() {
        assertTrue(SurveyRules.validate(5, 4, "Buen taller").isEmpty())
    }

    @Test
    fun rejectsMissingRatings() {
        val errors = SurveyRules.validate(0, 6, "")
        assertTrue(errors.containsKey("satisfaction"))
        assertTrue(errors.containsKey("usefulness"))
    }

    @Test
    fun rejectsLongComment() {
        val errors = SurveyRules.validate(3, 3, "a".repeat(601))
        assertFalse(errors.isEmpty())
        assertTrue(errors.containsKey("comment"))
    }
}
