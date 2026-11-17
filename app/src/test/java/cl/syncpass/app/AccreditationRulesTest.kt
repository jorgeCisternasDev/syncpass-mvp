package cl.syncpass.app

import cl.syncpass.app.data.local.EventEntity
import cl.syncpass.app.domain.AccreditationRules
import cl.syncpass.app.domain.AccreditationWindow
import cl.syncpass.app.domain.EventStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccreditationRulesTest {
    private val start = 1_800_000_000_000L
    private val end = start + 3 * 60 * 60 * 1000L
    private val event = EventEntity(
        id = 1,
        name = "Evento",
        venue = "Sede",
        startAt = start,
        endAt = end
    )

    @Test
    fun windowOpensTwoHoursBefore() {
        assertTrue(AccreditationRules.window(event, start - 3 * 60 * 60 * 1000L) is AccreditationWindow.NotOpen)
        assertTrue(AccreditationRules.window(event, start - 2 * 60 * 60 * 1000L) is AccreditationWindow.Open)
        assertTrue(AccreditationRules.window(event, start) is AccreditationWindow.Open)
        assertTrue(AccreditationRules.window(event, end) is AccreditationWindow.Open)
        assertTrue(AccreditationRules.window(event, end + 1) is AccreditationWindow.Closed)
    }

    @Test
    fun statusChangesAcrossSchedule() {
        assertEquals(EventStatus.SCHEDULED, AccreditationRules.status(event, start - 1))
        assertEquals(EventStatus.LIVE, AccreditationRules.status(event, start))
        assertEquals(EventStatus.LIVE, AccreditationRules.status(event, end))
        assertEquals(EventStatus.FINISHED, AccreditationRules.status(event, end + 1))
    }
}
