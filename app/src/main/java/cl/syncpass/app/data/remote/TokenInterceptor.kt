package cl.syncpass.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Agrega el Bearer token a cada request.
 *
 * Lee siempre de [SessionStore], que esta respaldado por SharedPreferences;
 * no depende de un cache en memoria que se pierda al reiniciar la app.
 */
class TokenInterceptor(
    private val sessionStore: SessionStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionStore.accessToken()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
