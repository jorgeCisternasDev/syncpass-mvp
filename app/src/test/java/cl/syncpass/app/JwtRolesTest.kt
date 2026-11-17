package cl.syncpass.app

import cl.syncpass.app.auth.AppRole
import cl.syncpass.app.data.remote.JwtRoles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class JwtRolesTest {

    @Test
    fun coordinatorGroupMapsToCoordinatorInterface() {
        val token = token("""{"groups":["Coordinador"],"exp":4102444800}""")

        assertEquals(AppRole.COORDINATOR, JwtRoles.appRoleFromToken(token))
        assertEquals(listOf("Coordinador"), JwtRoles.roles(token))
    }

    @Test
    fun monitorRoleMapsToMonitorInterface() {
        val token = token("""{"roles":["Monitor"],"exp":4102444800}""")

        assertEquals(AppRole.MONITOR, JwtRoles.appRoleFromToken(token))
    }

    @Test
    fun assistantGroupMapsToParticipantInterface() {
        val token = token("""{"groups":["Asistente"],"exp":4102444800}""")

        assertEquals(AppRole.PARTICIPANT, JwtRoles.appRoleFromToken(token))
    }

    @Test
    fun multipleRolesUseCoordinatorMonitorParticipantPrecedence() {
        assertEquals(
            AppRole.COORDINATOR,
            JwtRoles.appRole(listOf("Asistente", "Monitor", "Coordinador"))
        )
        assertEquals(AppRole.MONITOR, JwtRoles.appRole(listOf("Asistente", "Monitor")))
    }

    @Test
    fun displayNameAndEmailComeFromJwtClaims() {
        val token = token(
            """{"name":"Monitor","email":"monitor@syncpass.cl","groups":["Monitor"],"exp":4102444800}"""
        )

        assertEquals("Monitor", JwtRoles.displayName(token))
        assertEquals("monitor@syncpass.cl", JwtRoles.email(token))
    }

    @Test
    fun invalidOrUnknownTokensDoNotCrash() {
        assertNull(JwtRoles.appRoleFromToken(null))
        assertNull(JwtRoles.appRoleFromToken("no-es-un-jwt"))
        assertNull(JwtRoles.appRoleFromToken(token("""{"groups":["Invitado"]}""")))
        assertEquals(emptyList<String>(), JwtRoles.roles("a.b.c"))
    }

    @Test
    fun expirationIsReadFromExpClaim() {
        val token = token("""{"groups":["Monitor"],"exp":1000}""")

        assertTrue(JwtRoles.isExpired(token, nowMillis = 1_001_000L))
        assertFalse(JwtRoles.isExpired(token, nowMillis = 999_000L))
        assertFalse(JwtRoles.isExpired(token("""{"groups":["Monitor"]}"""), nowMillis = 2_000_000L))
    }

    private fun token(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        val body = encoder.encodeToString(payload.toByteArray())
        return "$header.$body.firma"
    }
}
