package cl.syncpass.app.auth

import java.util.Locale

enum class AppRole {
    MONITOR,
    COORDINATOR,
    PARTICIPANT
}

data class AuthUser(
    val username: String,
    val role: AppRole,
    val displayName: String,
    val email: String? = null
)

object LocalAuth {
    fun authenticate(username: String, password: String): AuthUser? {
        val normalized = username.trim().lowercase(Locale.ROOT)
            .substringBefore("@")
        return when {
            normalized == "monitor" && password == "1234" -> AuthUser(
                username = "monitor",
                role = AppRole.MONITOR,
                displayName = "Monitor"
            )

            normalized == "coordinador" && password == "1234" -> AuthUser(
                username = "coordinador",
                role = AppRole.COORDINATOR,
                displayName = "Coordinador"
            )

            normalized == "benjamin" && password == "1234" -> AuthUser(
                username = "benjamin",
                role = AppRole.PARTICIPANT,
                displayName = "Benjamin"
            )

            else -> null
        }
    }
}
