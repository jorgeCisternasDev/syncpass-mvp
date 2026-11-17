package cl.syncpass.app.data.remote

import cl.syncpass.app.auth.AppRole
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64

/**
 * Lectura de los claims del JWT emitido por syncpass-api.
 *
 * El idToken trae `groups` y el accessToken trae `roles`. Los valores usan los
 * nombres del backend: Coordinador, Monitor, Asistente.
 */
object JwtRoles {

    fun payload(token: String?): JsonObject? {
        val parts = token?.split('.') ?: return null
        if (parts.size < 2) return null
        return runCatching {
            val decoded = Base64.getUrlDecoder().decode(parts[1])
            JsonParser.parseString(String(decoded, Charsets.UTF_8)).asJsonObject
        }.getOrNull()
    }

    fun roles(token: String?): List<String> {
        val json = payload(token) ?: return emptyList()
        val values = linkedSetOf<String>()
        listOf("groups", "roles", "cognito:groups").forEach { key ->
            val array = json.getAsJsonArray(key) ?: return@forEach
            array.forEach { element ->
                runCatching { element.asString }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(values::add)
            }
        }
        return values.toList()
    }

    fun appRole(roles: List<String>): AppRole? {
        val normalized = roles.map { it.trim().lowercase() }.toSet()
        return when {
            normalized.any { it in COORDINATOR_NAMES } -> AppRole.COORDINATOR
            normalized.any { it in MONITOR_NAMES } -> AppRole.MONITOR
            normalized.any { it in PARTICIPANT_NAMES } -> AppRole.PARTICIPANT
            else -> null
        }
    }

    fun appRoleFromToken(token: String?): AppRole? = appRole(roles(token))

    fun displayName(token: String?): String? {
        val json = payload(token) ?: return null
        return sequenceOf("name", "nombre", "display_name")
            .mapNotNull { key -> json.get(key)?.takeIf { it.isJsonPrimitive }?.asString }
            .firstOrNull { it.isNotBlank() }
    }

    fun email(token: String?): String? {
        val json = payload(token) ?: return null
        return json.get("email")?.takeIf { it.isJsonPrimitive }?.asString
            ?.takeIf { it.isNotBlank() }
    }

    /** Expiracion en segundos desde epoch, o null si el token no la declara. */
    fun expiresAt(token: String?): Long? {
        val json = payload(token) ?: return null
        val exp = json.get("exp") ?: return null
        return runCatching { exp.asLong }.getOrNull()
    }

    fun isExpired(token: String?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val exp = expiresAt(token) ?: return false
        return exp <= nowMillis / 1000L
    }

    private val COORDINATOR_NAMES = setOf("coordinador", "coordinator")
    private val MONITOR_NAMES = setOf("monitor")
    private val PARTICIPANT_NAMES = setOf("asistente", "participante", "participant")
}
