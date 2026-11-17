package cl.syncpass.app.ui.viewmodel

import cl.syncpass.app.data.repository.AccreditResult
import cl.syncpass.app.util.Formatters

sealed interface AccreditFeedback {
    data class Success(
        val participantName: String,
        val participantCode: String,
        val attendedAt: Long,
        val hasLocation: Boolean
    ) : AccreditFeedback

    data class Duplicate(
        val participantName: String,
        val attendedAt: Long
    ) : AccreditFeedback

    data class NeedsRegistration(
        val rawInput: String,
        val fromQr: Boolean
    ) : AccreditFeedback

    data class Error(val message: String) : AccreditFeedback
}

fun AccreditResult.toFeedback(): AccreditFeedback = when (this) {
    is AccreditResult.Success -> AccreditFeedback.Success(
        participantName = participant.name,
        participantCode = participant.code,
        attendedAt = attendance.attendedAt,
        hasLocation = attendance.latitude != null && attendance.longitude != null
    )

    is AccreditResult.Duplicate -> AccreditFeedback.Duplicate(
        participantName = participant.name,
        attendedAt = attendedAt
    )

    is AccreditResult.NeedsRegistration -> AccreditFeedback.NeedsRegistration(
        rawInput = rawInput,
        fromQr = fromQr
    )

    is AccreditResult.EventMismatch -> AccreditFeedback.Error(
        if (eventName != null) {
            "Este QR pertenece al evento «$eventName», no al evento seleccionado."
        } else {
            "Este QR pertenece a otro evento."
        }
    )

    is AccreditResult.NotOpen -> AccreditFeedback.Error(
        "La acreditación abre el ${Formatters.dateTime(opensAt)}, dos horas antes del inicio."
    )

    is AccreditResult.Closed -> AccreditFeedback.Error(
        "El evento finalizó el ${Formatters.dateTime(closedAt)}. No se pueden registrar más asistencias."
    )

    is AccreditResult.InvalidQr -> AccreditFeedback.Error(message)
    is AccreditResult.InvalidInput -> AccreditFeedback.Error(message)
    is AccreditResult.NotFound -> AccreditFeedback.Error(
        "No encontramos «$input» entre los inscritos. Revisa el código o habilita el ingreso sin inscripción."
    )

    is AccreditResult.Conflict -> AccreditFeedback.Error(message)
    is AccreditResult.Failure -> AccreditFeedback.Error(message)
}
