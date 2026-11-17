package cl.syncpass.app.domain

object SurveyReleaseRules {
    fun isReleased(manuallyReleased: Boolean, eventEndAt: Long, now: Long): Boolean =
        manuallyReleased || now >= eventEndAt

    fun shouldAutoRelease(manuallyReleased: Boolean, eventEndAt: Long, now: Long): Boolean =
        !manuallyReleased && now >= eventEndAt
}
