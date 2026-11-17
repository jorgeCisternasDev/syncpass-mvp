package cl.syncpass.app

import cl.syncpass.app.data.local.SurveyQuestionType
import cl.syncpass.app.domain.SurveyDefinitionRules
import cl.syncpass.app.domain.SurveyQuestionInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyDefinitionRulesTest {
    private fun text(prompt: String = "¿Qué te pareció?") =
        SurveyQuestionInput(prompt, SurveyQuestionType.TEXT, true)

    @Test
    fun acceptsValidSurvey() {
        val errors = SurveyDefinitionRules.validate(
            title = "Encuesta de satisfacción",
            description = "Cuéntanos tu experiencia",
            questions = listOf(text(), SurveyQuestionInput("Nota", SurveyQuestionType.RATING, true))
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun rejectsShortTitleAndEmptyQuestions() {
        val errors = SurveyDefinitionRules.validate("ab", "", emptyList())
        assertTrue(errors.containsKey("title"))
        assertTrue(errors.containsKey("questions"))
    }

    @Test
    fun rejectsShortPromptAndInvalidType() {
        val errors = SurveyDefinitionRules.validate(
            title = "Encuesta válida",
            description = "",
            questions = listOf(SurveyQuestionInput("no", "OTRA", false))
        )
        assertFalse(errors.isEmpty())
        assertTrue(errors.containsKey("q_0"))
    }

    @Test
    fun rejectsTooManyQuestions() {
        val errors = SurveyDefinitionRules.validate(
            title = "Encuesta válida",
            description = "",
            questions = List(21) { text("Pregunta ${it + 1}") }
        )
        assertTrue(errors.containsKey("questions"))
    }
}
