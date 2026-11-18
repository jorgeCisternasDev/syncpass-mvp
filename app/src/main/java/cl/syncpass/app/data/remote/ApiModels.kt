package cl.syncpass.app.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Contrato plano de la API SyncPass.
 *
 * Login responde exactamente {accessToken, refreshToken, idToken}; el lote
 * responde exactamente {aceptados}. No hay envoltorio ApiResponse en estos
 * dos endpoints.
 */

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class LoginResponse(
    @SerializedName("accessToken") val accessToken: String? = null,
    @SerializedName("refreshToken") val refreshToken: String? = null,
    @SerializedName("idToken") val idToken: String? = null
)

data class AsistenciaRemota(
    @SerializedName("eventoId") val eventoId: Long,
    @SerializedName("usuarioId") val usuarioId: Long,
    @SerializedName("usuarioUuid") val usuarioUuid: String? = null,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("validado") val validado: Boolean,
    @SerializedName("latitud") val latitud: Double? = null,
    @SerializedName("longitud") val longitud: Double? = null
)

data class EncuestaRemota(
    @SerializedName("eventoId") val eventoId: Long,
    @SerializedName("usuarioId") val usuarioId: Long,
    @SerializedName("usuarioUuid") val usuarioUuid: String? = null,
    @SerializedName("satisfaccion") val satisfaccion: Int,
    @SerializedName("utilidad") val utilidad: Int,
    @SerializedName("comentario") val comentario: String? = null,
    @SerializedName("fecha") val fecha: Long
)

data class SincronizacionRequest(
    @SerializedName("asistencias") val asistencias: List<AsistenciaRemota>,
    @SerializedName("encuestas") val encuestas: List<EncuestaRemota>
)

data class SincronizacionResponse(
    @SerializedName("aceptados") val aceptados: Int = 0
)

/**
 * Contrato de catalogo. `ProyectoDto` y `EventoDto` del backend se devuelven
 * como listas planas; `fechaTermino` es opcional y lo agrega H3.
 */
data class ProyectoRemoto(
    @SerializedName("proyectoId") val proyectoId: Long,
    @SerializedName("titulo") val titulo: String,
    @SerializedName("descripcion") val descripcion: String = "",
    @SerializedName("areaTematica") val areaTematica: String = "",
    @SerializedName("unidadVecinal") val unidadVecinal: String = "",
    @SerializedName("fechaInicio") val fechaInicio: Long? = null,
    @SerializedName("fechaTermino") val fechaTermino: Long? = null,
    @SerializedName("estado") val estado: String = ""
)

data class EventoRemoto(
    @SerializedName("eventoId") val eventoId: Long,
    @SerializedName("proyectoId") val proyectoId: Long,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("fechaHora") val fechaHora: Long,
    @SerializedName("fechaTermino") val fechaTermino: Long? = null,
    @SerializedName("cuposMax") val cuposMax: Int = 0,
    @SerializedName("recinto") val recinto: String = "",
    @SerializedName("latitud") val latitud: Double? = null,
    @SerializedName("longitud") val longitud: Double? = null,
    @SerializedName("radioMetros") val radioMetros: Int = 0
)

data class ParticipanteRequest(
    @SerializedName("usuarioUuid") val usuarioUuid: String,
    @SerializedName("eventoId") val eventoId: Long,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("email") val email: String? = null
)

data class ParticipanteResponse(
    @SerializedName("usuarioUuid") val usuarioUuid: String? = null,
    @SerializedName("creado") val creado: Boolean = false
)
