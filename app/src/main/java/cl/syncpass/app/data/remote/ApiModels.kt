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
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("validado") val validado: Boolean,
    @SerializedName("latitud") val latitud: Double? = null,
    @SerializedName("longitud") val longitud: Double? = null
)

data class EncuestaRemota(
    @SerializedName("eventoId") val eventoId: Long,
    @SerializedName("usuarioId") val usuarioId: Long,
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
