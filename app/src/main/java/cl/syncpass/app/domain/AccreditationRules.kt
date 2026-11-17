package cl.syncpass.app.domain

import cl.syncpass.app.data.local.EventEntity

enum class EventStatus { SCHEDULED, LIVE, FINISHED }

sealed interface AccreditationWindow {
    data class Open(val closesAt: Long) : AccreditationWindow
    data class NotOpen(val opensAt: Long) : AccreditationWindow
    data class Closed(val closedAt: Long) : AccreditationWindow
}

object AccreditationRules {
    const val OPEN_BEFORE_MS = 2L * 60L * 60L * 1000L

    fun window(event: EventEntity, now: Long): AccreditationWindow {
        val opensAt = event.startAt - OPEN_BEFORE_MS
        return when {
            now < opensAt -> AccreditationWindow.NotOpen(opensAt)
            now > event.endAt -> AccreditationWindow.Closed(event.endAt)
            else -> AccreditationWindow.Open(event.endAt)
        }
    }

    fun status(event: EventEntity, now: Long): EventStatus = when {
        now < event.startAt -> EventStatus.SCHEDULED
        now <= event.endAt -> EventStatus.LIVE
        else -> EventStatus.FINISHED
    }
}

object SurveyRules {
    fun validate(satisfaction: Int, usefulness: Int, comment: String): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        if (satisfaction !in 1..5) errors["satisfaction"] = "Selecciona una satisfacción de 1 a 5."
        if (usefulness !in 1..5) errors["usefulness"] = "Selecciona una utilidad de 1 a 5."
        if (comment.length > 600) errors["comment"] = "El comentario no puede superar 600 caracteres."
        return errors
    }
}
