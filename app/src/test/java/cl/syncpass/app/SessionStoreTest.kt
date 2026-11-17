package cl.syncpass.app

import android.content.Context
import cl.syncpass.app.auth.AppRole
import cl.syncpass.app.auth.AuthUser
import cl.syncpass.app.data.remote.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("syncpass_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun tokenAndRoleSurviveANewStoreInstance() {
        val first = SessionStore(context)
        first.save(
            accessToken = "access-123",
            refreshToken = "refresh-456",
            idToken = "id-789",
            email = "monitor@syncpass.cl",
            displayName = "Monitor",
            role = AppRole.MONITOR
        )

        val second = SessionStore(context)

        assertEquals("access-123", second.accessToken())
        assertEquals("refresh-456", second.refreshToken())
        assertEquals("id-789", second.idToken())
        assertEquals("monitor@syncpass.cl", second.email())
        assertEquals("Monitor", second.displayName())
        assertEquals(AppRole.MONITOR, second.role())
        assertTrue(second.hasSession())
        assertEquals(
            AuthUser("monitor@syncpass.cl", AppRole.MONITOR, "Monitor", "monitor@syncpass.cl"),
            second.restoreUser()
        )
    }

    @Test
    fun localFallbackSessionAlsoSurvivesANewStoreInstance() {
        SessionStore(context).saveLocal(
            AuthUser("monitor", AppRole.MONITOR, "Monitor")
        )

        val restored = SessionStore(context)

        assertTrue(restored.isLocalSession())
        assertNull(restored.accessToken())
        assertEquals(AppRole.MONITOR, restored.role())
        assertEquals("monitor", restored.restoreUser()?.username)
    }

    @Test
    fun clearRemovesPersistedSession() {
        val store = SessionStore(context)
        store.save("access", "refresh", "id", "a@b.cl", "Persona", AppRole.PARTICIPANT)

        store.clear()

        val reopened = SessionStore(context)
        assertFalse(reopened.hasSession())
        assertNull(reopened.accessToken())
        assertNull(reopened.role())
        assertNull(reopened.restoreUser())
    }
}
