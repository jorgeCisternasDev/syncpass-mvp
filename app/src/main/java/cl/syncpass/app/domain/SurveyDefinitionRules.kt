package cl.syncpass.app.domain

import cl.syncpass.app.data.local.SurveyQuestionType

data class SurveyQuestionInput(
    val prompt: String,
    val type: String,
    val required: Boolean
)

object SurveyDefinitionRules {
    const val MAX_QUESTIONS = 20

    fun validate(
        title: String,
        description: String,
        questions: List<SurveyQuestionInput>
    ): Map<String, String> {
        val errors = linkedMapOf<String, String>()

        val cleanTitle = title.trim()
        if (cleanTitle.length < 3) errors["title"] = "El título debe tener al menos 3 caracteres."
        if (cleanTitle.length > 100) errors["title"] = "El título no puede superar 100 caracteres."
        if (description.length > 500) {
            errors["description"] = "La descripción no puede superar 500 caracteres."
        }

        if (questions.isEmpty()) {
            errors["questions"] = "Agrega al menos una pregunta."
        } else if (questions.size > MAX_QUESTIONS) {
            errors["questions"] = "Una encuesta puede tener como máximo $MAX_QUESTIONS preguntas."
        }

        questions.forEachIndexed { index, question ->
            val prompt = question.prompt.trim()
            val key = "q_$index"
            when {
                prompt.length < 3 -> errors[key] = "La pregunta ${index + 1} debe tener al menos 3 caracteres."
                prompt.length > 200 -> errors[key] = "La pregunta ${index + 1} no puede superar 200 caracteres."
            }
            if (question.type != SurveyQuestionType.TEXT && question.type != SurveyQuestionType.RATING) {
                errors[key] = "El tipo de la pregunta ${index + 1} no es válido."
            }
        }

        return errors
    }
}
