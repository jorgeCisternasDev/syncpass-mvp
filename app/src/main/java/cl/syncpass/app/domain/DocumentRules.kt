package cl.syncpass.app.domain

import java.util.Locale

object DocumentRules {
    private val emailPattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val allowedDocument = Regex("^[A-Z0-9]+$")

    fun normalize(raw: String): String =
        raw.trim().uppercase(Locale.ROOT).replace(Regex("[.\\s-]"), "")

    fun validateDocument(raw: String): String? {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return "El documento o identificador es obligatorio."
        if (normalized.length < 3) return "El documento debe tener al menos 3 caracteres."
        if (normalized.length > 30) return "El documento no puede superar 30 caracteres."
        if (!allowedDocument.matches(normalized)) {
            return "Usa solo letras y números (sin símbolos distintos de punto o guion)."
        }
        return null
    }

    fun validateEmail(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        if (value.length > 120) return "El correo es demasiado largo."
        if (!emailPattern.matches(value)) return "El correo no tiene un formato válido."
        return null
    }

    fun validateName(raw: String, label: String = "El nombre"): String? {
        val value = raw.trim()
        if (value.length < 2) return "$label debe tener al menos 2 caracteres."
        if (value.length > 80) return "$label no puede superar 80 caracteres."
        return null
    }
}
