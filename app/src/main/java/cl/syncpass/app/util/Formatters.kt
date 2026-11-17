package cl.syncpass.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    private val locale = Locale.forLanguageTag("es-CL")
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", locale)
    private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", locale)
    private val shortDateTimeFormatter = DateTimeFormatter.ofPattern("d MMM · HH:mm", locale)
    private val fileStampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", locale)

    private fun zoned(epochMillis: Long) =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())

    fun date(epochMillis: Long): String = dateFormatter.format(zoned(epochMillis))

    fun shortDate(epochMillis: Long): String = shortDateFormatter.format(zoned(epochMillis))

    fun weekdayDate(epochMillis: Long): String = weekdayFormatter.format(zoned(epochMillis))

    fun time(epochMillis: Long): String = timeFormatter.format(zoned(epochMillis))

    fun dateTime(epochMillis: Long): String = dateTimeFormatter.format(zoned(epochMillis))

    fun shortDateTime(epochMillis: Long): String = shortDateTimeFormatter.format(zoned(epochMillis))

    fun schedule(startAt: Long, endAt: Long): String {
        val start = zoned(startAt)
        val end = zoned(endAt)
        return if (start.toLocalDate() == end.toLocalDate()) {
            "${dateFormatter.format(start)} · ${timeFormatter.format(start)}–${timeFormatter.format(end)}"
        } else {
            "${dateFormatter.format(start)} ${timeFormatter.format(start)} – " +
                "${dateFormatter.format(end)} ${timeFormatter.format(end)}"
        }
    }

    fun timeRange(startAt: Long, endAt: Long): String =
        "${timeFormatter.format(zoned(startAt))}–${timeFormatter.format(zoned(endAt))}"

    fun fileStamp(epochMillis: Long = System.currentTimeMillis()): String =
        fileStampFormatter.format(zoned(epochMillis))

    fun rating(value: Double?): String =
        if (value == null) "—" else String.format(locale, "%.1f", value)

    fun percent(part: Int, total: Int): String =
        if (total <= 0) "0 %" else "${((part * 100f) / total).toInt()} %"
}
