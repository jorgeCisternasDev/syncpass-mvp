package cl.syncpass.app

import android.content.Context
import cl.syncpass.app.auth.AppRole
import cl.syncpass.app.auth.AuthUser
import cl.syncpass.app.data.remote.AuthApi
import cl.syncpass.app.data.remote.LoginRequest
import cl.syncpass.app.data.remote.LoginResponse
import cl.syncpass.app.data.remote.SessionStore
import cl.syncpass.app.data.repository.AuthRepository
import cl.syncpass.app.data.repository.LoginResult
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
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
import java.io.IOException
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthRepositoryTest {

    private lateinit var context: Context
    private lateinit var sessionStore: SessionStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("syncpass_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        sessionStore = SessionStore(context)
    }

    @Test
    fun remoteLoginReadsRoleFromJwtAndPersistsTokenForRestart() = runTest {
        val idToken = token(
            """{"name":"Monitor","email":"monitor@syncpass.cl","groups":["Monitor"],"exp":4102444800}"""
        )
        val api = FakeAuthApi(
            response = LoginResponse(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                idToken = idToken
            )
        )
        val repository = AuthRepository(api, sessionStore, demoMode = false)

        val result = repository.login("monitor@syncpass.cl", "Monitor.2026")

        assertTrue(result is LoginResult.Success)
        val success = result as LoginResult.Success
        assertTrue(success.fromApi)
        assertEquals(AppRole.MONITOR, success.user.role)
        assertEquals("Monitor", success.user.displayName)
        assertEquals("access-token", sessionStore.accessToken())
        assertEquals("refresh-token", sessionStore.refreshToken())

        val afterRestart = AuthRepository(api, SessionStore(context), demoMode = false)
        assertEquals("monitor@syncpass.cl", afterRestart.restoreSession()?.email)
        assertEquals(AppRole.MONITOR, afterRestart.restoreSession()?.role)
    }

    @Test
    fun expiredTokenIsDiscardedOnRestore() {
        sessionStore.save(
            accessToken = token("""{"groups":["Monitor"],"exp":1000}"""),
            refreshToken = null,
            idToken = null,
            email = "monitor@syncpass.cl",
            displayName = "Monitor",
            role = AppRole.MONITOR
        )

        val repository = AuthRepository(
            FakeAuthApi(LoginResponse()),
            sessionStore,
            demoMode = false,
            now = { 2_000_000L }
        )

        assertNull(repository.restoreSession())
        assertFalse(sessionStore.hasSession())
    }

    @Test
    fun networkFailureUsesLocalLoginOnlyWhenDemoModeIsEnabled() = runTest {
        val api = FakeAuthApi(error = IOException("sin red"))
        val localAuth: (String, String) -> AuthUser? = { user, password ->
            if (user == "monitor" && password == "1234") {
                AuthUser("monitor", AppRole.MONITOR, "Monitor")
            } else {
                null
            }
        }

        val demo = AuthRepository(
            api,
            sessionStore,
            demoMode = true,
            localAuth = localAuth
        )
        val result = demo.login("monitor", "1234")

        assertTrue(result is LoginResult.Success)
        assertFalse((result as LoginResult.Success).fromApi)
        assertTrue(sessionStore.isLocalSession())
        assertEquals(AppRole.MONITOR, sessionStore.restoreUser()?.role)

        sessionStore.clear()
        val strict = AuthRepository(
            api,
            sessionStore,
            demoMode = false,
            localAuth = localAuth
        )
        assertTrue(strict.login("monitor", "1234") is LoginResult.Failure)
        assertFalse(sessionStore.hasSession())
    }

    @Test
    fun invalidCredentialsAreNotReplacedByLocalFallback() = runTest {
        val api = FakeAuthApi(
            error = retrofit2.HttpException(
                retrofit2.Response.error<Any>(
                    401,
                    "no autorizado".toResponseBody(null)
                )
            )
        )
        val repository = AuthRepository(
            api,
            sessionStore,
            demoMode = true,
            localAuth = { _, _ -> AuthUser("monitor", AppRole.MONITOR, "Monitor") }
        )

        assertTrue(repository.login("monitor@syncpass.cl", "mala") is LoginResult.InvalidCredentials)
        assertFalse(sessionStore.hasSession())
    }

    private class FakeAuthApi(
        private val response: LoginResponse? = null,
        private val error: Exception? = null
    ) : AuthApi {
        override suspend fun login(request: LoginRequest): LoginResponse {
            error?.let { throw it }
            return response ?: LoginResponse()
        }
    }

    private fun token(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        val body = encoder.encodeToString(payload.toByteArray())
        return "$header.$body.firma"
    }
}
