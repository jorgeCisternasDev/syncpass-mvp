package cl.syncpass.app.domain

data class EventDraft(
    val name: String,
    val description: String,
    val venue: String,
    val address: String,
    val startAt: Long,
    val endAt: Long,
    val capacityText: String
)

data class EventValidation(
    val capacity: Int,
    val errors: Map<String, String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

object EventRules {
    fun validate(draft: EventDraft): EventValidation {
        val errors = linkedMapOf<String, String>()

        val name = draft.name.trim()
        if (name.length < 3) errors["name"] = "Escribe un nombre de al menos 3 caracteres."
        if (name.length > 100) errors["name"] = "El nombre no puede superar 100 caracteres."

        val venue = draft.venue.trim()
        if (venue.length < 3) errors["venue"] = "Indica el recinto o lugar del evento."
        if (venue.length > 120) errors["venue"] = "El recinto no puede superar 120 caracteres."

        if (draft.description.length > 600) {
            errors["description"] = "La descripción no puede superar 600 caracteres."
        }
        if (draft.address.length > 200) {
            errors["address"] = "La dirección no puede superar 200 caracteres."
        }

        if (draft.endAt <= draft.startAt) {
            errors["end"] = "El término debe ser posterior al inicio."
        }

        val capacity = when {
            draft.capacityText.isBlank() -> 0
            else -> draft.capacityText.trim().toIntOrNull() ?: -1
        }
        if (capacity < 0) {
            errors["capacity"] = "El aforo debe ser un número entero positivo."
        } else if (capacity > 100_000) {
            errors["capacity"] = "El aforo no puede superar 100.000 personas."
        }

        return EventValidation(capacity = capacity.coerceAtLeast(0), errors = errors)
    }
}
