package cl.syncpass.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}

interface SyncApi {
    @POST("api/sync/lote")
    suspend fun subirLote(@Body request: SincronizacionRequest): SincronizacionResponse
}

/** Catalogo remoto de proyectos y eventos (offline-first: se suma a Room). */
interface CatalogoApi {
    @GET("api/proyectos")
    suspend fun proyectos(): List<ProyectoRemoto>

    @GET("api/proyectos/{proyectoId}/eventos")
    suspend fun eventos(@Path("proyectoId") proyectoId: Long): List<EventoRemoto>
}

/** Alta idempotente de un participante local como usuario del backend. */
interface ParticipanteApi {
    @POST("api/participantes")
    suspend fun registrar(@Body request: ParticipanteRequest): ParticipanteResponse
}
