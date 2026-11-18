package cl.syncpass.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class NetworkModule(
    baseUrl: String,
    sessionStore: SessionStore,
    debugLogging: Boolean
) {
    private val client = OkHttpClient.Builder()
        .addInterceptor(TokenInterceptor(sessionStore))
        .apply {
            if (debugLogging) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                )
            }
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val syncApi: SyncApi = retrofit.create(SyncApi::class.java)
    val catalogoApi: CatalogoApi = retrofit.create(CatalogoApi::class.java)
    val participanteApi: ParticipanteApi = retrofit.create(ParticipanteApi::class.java)
}
