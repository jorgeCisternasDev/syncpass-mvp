package cl.syncpass.app

import cl.syncpass.app.data.local.AttendanceExportRow
import cl.syncpass.app.data.local.AttendanceMethod
import cl.syncpass.app.util.CsvBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvBuilderTest {
    private val row = AttendanceExportRow(
        eventName = "Taller, \"avanzado\"",
        eventStartAt = 1_800_000_000_000L,
        participantName = "Ana\nPérez",
        participantDocument = "12.345.678-9",
        participantCode = "SPAB12CD",
        isWalkIn = true,
        registeredAt = 1_800_000_000_000L,
        attendedAt = 1_800_000_100_000L,
        method = AttendanceMethod.QR,
        latitude = -33.4489,
        longitude = -70.6693,
        satisfaction = 5,
        usefulness = 4,
        comment = "Muy buena, \"excelente\"."
    )

    @Test
    fun headerHasAllColumns() {
        val csv = CsvBuilder.build(emptyList())
        assertTrue(csv.startsWith("Evento,Fecha del evento,Participante"))
        assertEquals(CsvBuilder.HEADER.size, csv.trim().split(",").size)
    }

    @Test
    fun escapesCommasQuotesAndNewlines() {
        val csv = CsvBuilder.build(listOf(row))
        assertTrue(csv.contains("\"Taller, \"\"avanzado\"\"\""))
        assertTrue(csv.contains("\"Ana\nPérez\""))
        assertTrue(csv.contains("\"Muy buena, \"\"excelente\"\".\""))
        assertTrue(csv.contains("SPAB12CD"))
        assertTrue(csv.contains("Sí"))
        assertTrue(csv.contains("QR"))
        assertTrue(csv.contains("-33.448900"))
    }

    @Test
    fun methodLabelsAreSpanish() {
        assertEquals("QR", CsvBuilder.methodLabel(AttendanceMethod.QR))
        assertEquals("Manual", CsvBuilder.methodLabel(AttendanceMethod.MANUAL))
        assertEquals("Ingreso sin inscripción", CsvBuilder.methodLabel(AttendanceMethod.WALK_IN))
        assertEquals("", CsvBuilder.methodLabel(null))
    }
}
