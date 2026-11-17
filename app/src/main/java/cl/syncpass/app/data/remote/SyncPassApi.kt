package cl.syncpass.app.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}

interface SyncApi {
    @POST("api/sync/lote")
    suspend fun subirLote(@Body request: SincronizacionRequest): SincronizacionResponse
}
