package cl.syncpass.app.data.repository

import cl.syncpass.app.BuildConfig
import cl.syncpass.app.auth.AuthUser
import cl.syncpass.app.auth.LocalAuth
import cl.syncpass.app.data.remote.AuthApi
import cl.syncpass.app.data.remote.JwtRoles
import cl.syncpass.app.data.remote.LoginRequest
import cl.syncpass.app.data.remote.SessionStore
import retrofit2.HttpException
import java.io.IOException

sealed interface LoginResult {
    data class Success(val user: AuthUser, val fromApi: Boolean) : LoginResult
    data object InvalidCredentials : LoginResult
    data class Failure(val message: String) : LoginResult
}

/**
 * Login contra syncpass-api.
 *
 * - Si el backend responde, el rol sale del JWT (Coordinador/Monitor/Asistente).
 * - Si el backend no responde y MODO_DEMO = true, se usa LocalAuth como respaldo.
 * - Los tokens se guardan en SharedPreferences a traves de [SessionStore].
 */
class AuthRepository(
    private val authApi: AuthApi,
    private val sessionStore: SessionStore,
    private val demoMode: Boolean = BuildConfig.MODO_DEMO,
    private val localAuth: (String, String) -> AuthUser? = LocalAuth::authenticate,
    private val now: () -> Long = System::currentTimeMillis
) {

    fun restoreSession(): AuthUser? {
        val user = sessionStore.restoreUser() ?: return null
        if (!sessionStore.isLocalSession()) {
            val token = sessionStore.accessToken()
            if (token.isNullOrBlank() || JwtRoles.isExpired(token, now())) {
                sessionStore.clear()
                return null
            }
        }
        return user
    }

    suspend fun login(identifier: String, password: String): LoginResult {
        val email = identifier.trim()
        if (email.isBlank() || password.isBlank()) {
            return LoginResult.InvalidCredentials
        }

        return try {
            loginRemote(email, password)
        } catch (error: HttpException) {
            when {
                error.code() == 400 || error.code() == 401 || error.code() == 403 ->
                    LoginResult.InvalidCredentials

                demoMode -> localFallback(identifier, password)
                    ?: LoginResult.Failure("El servidor respondio ${error.code()}.")

                else -> LoginResult.Failure("El servidor respondio ${error.code()}.")
            }
        } catch (error: IOException) {
            if (demoMode) {
                localFallback(identifier, password) ?: LoginResult.Failure(
                    "No se pudo conectar con el servidor. Revisa la red o usa el acceso demo."
                )
            } else {
                LoginResult.Failure(
                    "No se pudo conectar con el servidor. Revisa que la API este disponible."
                )
            }
        } catch (error: Exception) {
            LoginResult.Failure("No se pudo iniciar sesion: ${error.message ?: "error desconocido"}")
        }
    }

    fun logout() {
        sessionStore.clear()
    }

    private suspend fun loginRemote(email: String, password: String): LoginResult {
        val response = authApi.login(LoginRequest(email = email, password = password))
        val accessToken = response.accessToken
        if (accessToken.isNullOrBlank()) return LoginResult.InvalidCredentials

        val idToken = response.idToken
        val role = JwtRoles.appRoleFromToken(idToken)
            ?: JwtRoles.appRoleFromToken(accessToken)
            ?: return LoginResult.Failure("La cuenta no tiene un rol SyncPass valido.")

        val displayName = JwtRoles.displayName(idToken)
            ?: JwtRoles.displayName(accessToken)
            ?: JwtRoles.email(idToken)
            ?: JwtRoles.email(accessToken)
            ?: email.substringBefore("@")

        val user = AuthUser(
            username = email,
            role = role,
            displayName = displayName,
            email = email
        )
        sessionStore.save(
            accessToken = accessToken,
            refreshToken = response.refreshToken,
            idToken = idToken,
            email = email,
            displayName = displayName,
            role = role
        )
        return LoginResult.Success(user, fromApi = true)
    }

    private fun localFallback(identifier: String, password: String): LoginResult? {
        val local = localAuth(identifier, password) ?: return null
        val user = local.copy(email = local.email ?: identifier.takeIf { it.contains("@") })
        sessionStore.saveLocal(user)
        return LoginResult.Success(user, fromApi = false)
    }
}
