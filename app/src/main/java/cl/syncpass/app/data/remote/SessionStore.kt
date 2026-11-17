package cl.syncpass.app.data.remote

import android.content.Context
import cl.syncpass.app.auth.AppRole
import cl.syncpass.app.auth.AuthUser

/**
 * Sesion persistida en SharedPreferences.
 *
 * El token se guarda en disco, no solo en memoria: sobrevive al cierre del
 * proceso y se lee en cada request mediante [TokenInterceptor].
 */
class SessionStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun save(
        accessToken: String,
        refreshToken: String?,
        idToken: String?,
        email: String,
        displayName: String,
        role: AppRole
    ) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_ID_TOKEN, idToken)
            .putString(KEY_EMAIL, email)
            .putString(KEY_DISPLAY_NAME, displayName)
            .putString(KEY_ROLE, role.name)
            .putBoolean(KEY_LOCAL, false)
            .apply()
    }

    /** Respaldo demo: no hay JWT, pero la sesion local tambien sobrevive. */
    fun saveLocal(user: AuthUser) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, "")
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_ID_TOKEN)
            .putString(KEY_EMAIL, user.email ?: user.username)
            .putString(KEY_DISPLAY_NAME, user.displayName)
            .putString(KEY_ROLE, user.role.name)
            .putBoolean(KEY_LOCAL, true)
            .apply()
    }

    fun accessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun refreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun idToken(): String? = prefs.getString(KEY_ID_TOKEN, null)

    fun email(): String? = prefs.getString(KEY_EMAIL, null)

    fun displayName(): String? = prefs.getString(KEY_DISPLAY_NAME, null)

    fun isLocalSession(): Boolean = prefs.getBoolean(KEY_LOCAL, false)

    fun role(): AppRole? = prefs.getString(KEY_ROLE, null)?.let { raw ->
        runCatching { AppRole.valueOf(raw) }.getOrNull()
    }

    fun hasSession(): Boolean = isLocalSession() || !accessToken().isNullOrBlank()

    fun restoreUser(): AuthUser? {
        if (!hasSession()) return null
        val role = role() ?: return null
        val name = displayName()?.takeIf { it.isNotBlank() } ?: return null
        val mail = email()
        return AuthUser(
            username = mail ?: name,
            role = role,
            displayName = name,
            email = mail
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "syncpass_session"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_ID_TOKEN = "id_token"
        const val KEY_EMAIL = "email"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_ROLE = "role"
        const val KEY_LOCAL = "local_session"
    }
}
