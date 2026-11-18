package cl.syncpass.app.data.local

import androidx.room.Embedded

data class EventWithStats(
    @Embedded val event: EventEntity,
    val registeredCount: Int,
    val attendedCount: Int,
    val surveyCount: Int
)

data class ProjectWithCount(
    @Embedded val project: ProjectEntity,
    val eventCount: Int
)

data class ParticipantStatusRow(
    @Embedded val participant: ParticipantEntity,
    val attendanceId: Long?,
    val attendedAt: Long?,
    val method: String?,
    val latitude: Double?,
    val longitude: Double?,
    val surveyId: Long?,
    val satisfaction: Int?,
    val usefulness: Int?,
    val surveySubmittedAt: Long?
)

data class ProvisioningParticipantRow(
    @Embedded val participant: ParticipantEntity,
    val eventRemoteId: Long?
)

data class AttendanceRow(
    @Embedded val attendance: AttendanceEntity,
    val participantName: String,
    val participantDocument: String,
    val participantCode: String
)

data class PendingSurveyRow(
    val attendanceId: Long,
    val eventId: Long,
    val eventName: String,
    val participantId: Long,
    val participantName: String,
    val participantDocument: String,
    val attendedAt: Long
)

data class SurveyListRow(
    @Embedded val survey: SurveyEntity,
    val participantName: String,
    val participantDocument: String,
    val eventName: String
)

data class SurveyContextRow(
    val attendanceId: Long,
    val eventId: Long,
    val eventName: String,
    val eventSurveyEnabled: Boolean,
    val eventSurveyReleased: Boolean,
    val participantId: Long,
    val participantName: String,
    val participantDocument: String,
    val attendedAt: Long,
    val surveyId: Long?,
    val satisfaction: Int?,
    val usefulness: Int?,
    val comment: String?,
    val submittedAt: Long?
)

data class SurveyStatsRow(
    val total: Int,
    val avgSatisfaction: Double?,
    val avgUsefulness: Double?
)

data class RatingCount(
    val rating: Int,
    val count: Int
)

data class SurveyDefinitionWithStats(
    @Embedded val definition: SurveyDefinitionEntity,
    val eventName: String?,
    val questionCount: Int,
    val responseCount: Int
)

data class SurveyAnswerRow(
    val responseId: Long,
    val respondentName: String,
    val submittedAt: Long,
    val questionId: Long,
    val questionPrompt: String,
    val questionType: String,
    val position: Int,
    val textValue: String?,
    val ratingValue: Int?
)

data class AttendanceExportRow(
    val eventName: String,
    val eventStartAt: Long,
    val participantName: String,
    val participantDocument: String,
    val participantCode: String,
    val isWalkIn: Boolean,
    val registeredAt: Long,
    val attendedAt: Long?,
    val method: String?,
    val latitude: Double?,
    val longitude: Double?,
    val satisfaction: Int?,
    val usefulness: Int?,
    val comment: String?
)
