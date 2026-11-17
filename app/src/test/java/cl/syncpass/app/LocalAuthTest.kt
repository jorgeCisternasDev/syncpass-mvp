package cl.syncpass.app

import cl.syncpass.app.auth.AppRole
import cl.syncpass.app.auth.LocalAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalAuthTest {
    @Test
    fun monitorCredentialsOpenMonitorRole() {
        val user = LocalAuth.authenticate("monitor", "1234")
        assertEquals(AppRole.MONITOR, user?.role)
        assertEquals("Monitor", user?.displayName)
    }

    @Test
    fun coordinatorCredentialsOpenCoordinatorRole() {
        val user = LocalAuth.authenticate("coordinador", "1234")
        assertEquals(AppRole.COORDINATOR, user?.role)
        assertEquals("Coordinador", user?.displayName)
    }

    @Test
    fun benjaminCredentialsOpenParticipantRole() {
        val user = LocalAuth.authenticate("benjamin", "1234")
        assertEquals(AppRole.PARTICIPANT, user?.role)
        assertEquals("Benjamin", user?.displayName)
    }

    @Test
    fun usernameIsTrimmedAndCaseInsensitive() {
        assertEquals(AppRole.MONITOR, LocalAuth.authenticate("  MONITOR ", "1234")?.role)
        assertEquals(AppRole.PARTICIPANT, LocalAuth.authenticate("Benjamin", "1234")?.role)
    }

    @Test
    fun wrongPasswordOrUnknownUserIsRejected() {
        assertNull(LocalAuth.authenticate("monitor", "0000"))
        assertNull(LocalAuth.authenticate("benjamin", ""))
        assertNull(LocalAuth.authenticate("otro", "1234"))
    }
}
