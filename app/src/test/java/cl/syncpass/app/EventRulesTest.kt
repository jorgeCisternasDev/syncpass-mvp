package cl.syncpass.app

import cl.syncpass.app.domain.EventDraft
import cl.syncpass.app.domain.EventRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRulesTest {
    private fun draft(
        name: String = "Taller de robótica",
        venue: String = "Sede Central",
        start: Long = 1_000L,
        end: Long = 2_000L,
        capacity: String = "50"
    ) = EventDraft(
        name = name,
        description = "",
        venue = venue,
        address = "",
        startAt = start,
        endAt = end,
        capacityText = capacity
    )

    @Test
    fun acceptsValidDraft() {
        val result = EventRules.validate(draft())
        assertTrue(result.isValid)
        assertEquals(50, result.capacity)
    }

    @Test
    fun blankCapacityMeansUnlimited() {
        val result = EventRules.validate(draft(capacity = ""))
        assertTrue(result.isValid)
        assertEquals(0, result.capacity)
    }

    @Test
    fun rejectsEndBeforeStart() {
        val result = EventRules.validate(draft(start = 2_000L, end = 1_000L))
        assertFalse(result.isValid)
        assertTrue(result.errors.containsKey("end"))
    }

    @Test
    fun rejectsBlankFieldsAndInvalidCapacity() {
        val result = EventRules.validate(
            draft(name = " ", venue = "", capacity = "abc")
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.containsKey("name"))
        assertTrue(result.errors.containsKey("venue"))
        assertTrue(result.errors.containsKey("capacity"))
    }

    @Test
    fun rejectsCapacityOverLimit() {
        val result = EventRules.validate(draft(capacity = "100001"))
        assertFalse(result.isValid)
        assertTrue(result.errors.containsKey("capacity"))
    }
}
