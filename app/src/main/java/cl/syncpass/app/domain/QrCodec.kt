package cl.syncpass.app.domain

import java.util.Locale

object QrCodec {
    private const val PREFIX = "SYNCPASS"
    private const val VERSION = "V1"
    private val codePattern = Regex("^[A-Z0-9]{4,20}$")

    data class Payload(val eventId: Long, val participantCode: String)

    fun encode(eventId: Long, participantCode: String): String =
        "$PREFIX:$VERSION:$eventId:${participantCode.trim().uppercase(Locale.ROOT)}"

    fun looksLikeQr(raw: String): Boolean =
        raw.trim().startsWith(PREFIX, ignoreCase = true)

    fun parse(raw: String): Payload? {
        val parts = raw.trim().split(':')
        if (parts.size != 4) return null
        if (!parts[0].equals(PREFIX, ignoreCase = true)) return null
        if (!parts[1].equals(VERSION, ignoreCase = true)) return null
        val eventId = parts[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val code = parts[3].trim().uppercase(Locale.ROOT)
        if (!codePattern.matches(code)) return null
        return Payload(eventId = eventId, participantCode = code)
    }
}
