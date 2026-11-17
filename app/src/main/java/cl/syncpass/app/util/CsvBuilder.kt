package cl.syncpass.app.util

import cl.syncpass.app.data.local.AttendanceExportRow
import cl.syncpass.app.data.local.AttendanceMethod

object CsvBuilder {
    val HEADER = listOf(
        "Evento",
        "Fecha del evento",
        "Participante",
        "Documento",
        "Código",
        "Ingreso sin inscripción",
        "Fecha de inscripción",
        "Acreditado",
        "Fecha de acreditación",
        "Método",
        "Latitud",
        "Longitud",
        "Satisfacción",
        "Utilidad",
        "Comentario"
    )

    fun build(rows: List<AttendanceExportRow>): String = buildString {
        appendLine(HEADER.joinToString(",") { escape(it) })
        rows.forEach { row ->
            appendLine(
                listOf(
                    row.eventName,
                    Formatters.dateTime(row.eventStartAt),
                    row.participantName,
                    row.participantDocument,
                    row.participantCode,
                    if (row.isWalkIn) "Sí" else "No",
                    Formatters.dateTime(row.registeredAt),
                    if (row.attendedAt != null) "Sí" else "No",
                    row.attendedAt?.let(Formatters::dateTime).orEmpty(),
                    methodLabel(row.method),
                    row.latitude?.let { String.format(java.util.Locale.US, "%.6f", it) }.orEmpty(),
                    row.longitude?.let { String.format(java.util.Locale.US, "%.6f", it) }.orEmpty(),
                    row.satisfaction?.toString().orEmpty(),
                    row.usefulness?.toString().orEmpty(),
                    row.comment.orEmpty()
                ).joinToString(",") { escape(it) }
            )
        }
    }

    fun methodLabel(method: String?): String = when (method) {
        AttendanceMethod.QR -> "QR"
        AttendanceMethod.WALK_IN -> "Ingreso sin inscripción"
        AttendanceMethod.MANUAL -> "Manual"
        else -> ""
    }

    fun escape(value: String): String {
        if (value.isEmpty()) return ""
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    fun fileName(eventName: String?, now: Long = System.currentTimeMillis()): String {
        val base = eventName
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9áéíóúñ]+"), "_")
            ?.trim('_')
            ?.take(40)
            ?.ifBlank { "evento" }
            ?: "todos_los_eventos"
        return "syncpass_${base}_${Formatters.fileStamp(now)}.csv"
    }
}
