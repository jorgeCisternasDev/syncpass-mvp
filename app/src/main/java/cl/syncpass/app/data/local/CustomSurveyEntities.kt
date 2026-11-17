package cl.syncpass.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "survey_definitions",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("eventId"), Index("createdAt"), Index("isActive")]
)
data class SurveyDefinitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long? = null,
    val title: String,
    val description: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "survey_questions",
    foreignKeys = [
        ForeignKey(
            entity = SurveyDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["surveyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("surveyId"),
        Index(value = ["surveyId", "position"], unique = true)
    ]
)
data class SurveyQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surveyId: Long,
    val prompt: String,
    val type: String,
    val required: Boolean = true,
    val position: Int
)

@Entity(
    tableName = "survey_responses",
    foreignKeys = [
        ForeignKey(
            entity = SurveyDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["surveyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("surveyId"), Index("submittedAt")]
)
data class SurveyResponseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surveyId: Long,
    val respondentName: String = "",
    val submittedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "survey_answers",
    foreignKeys = [
        ForeignKey(
            entity = SurveyResponseEntity::class,
            parentColumns = ["id"],
            childColumns = ["responseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SurveyQuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("responseId"),
        Index("questionId"),
        Index(value = ["responseId", "questionId"], unique = true)
    ]
)
data class SurveyAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val responseId: Long,
    val questionId: Long,
    val textValue: String? = null,
    val ratingValue: Int? = null
)

object SurveyQuestionType {
    const val TEXT = "TEXT"
    const val RATING = "RATING"

    fun label(type: String): String = when (type) {
        RATING -> "Escala 1–5"
        else -> "Texto corto"
    }
}
