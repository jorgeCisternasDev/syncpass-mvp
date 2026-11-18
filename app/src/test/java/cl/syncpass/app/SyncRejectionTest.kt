package cl.syncpass.app

import android.content.Context
import androidx.room.Room
import cl.syncpass.app.data.local.AppDatabase
import cl.syncpass.app.data.local.AttendanceEntity
import cl.syncpass.app.data.local.EventEntity
import cl.syncpass.app.data.local.ParticipantEntity
import cl.syncpass.app.data.remote.ParticipanteApi
import cl.syncpass.app.data.remote.ParticipanteRequest
import cl.syncpass.app.data.remote.ParticipanteResponse
import cl.syncpass.app.data.remote.SincronizacionRequest
import cl.syncpass.app.data.remote.SincronizacionResponse
import cl.syncpass.app.data.remote.SyncApi
import cl.syncpass.app.data.repository.ProvisioningRepository
import cl.syncpass.app.data.repository.SyncRepository
import cl.syncpass.app.data.repository.SyncResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncRejectionTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var api: FakeSyncApi
    private lateinit var repository: SyncRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        api = FakeSyncApi()
        repository = SyncRepository(
            database = database,
            syncApi = api,
            provisioningRepository = ProvisioningRepository(database, FakeParticipanteApi())
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun rechazoDeUnItemLoMarcaYDejaSeguirConElResto() = runBlocking {
        val eventId = crearEvento()
        crearParticipanteYAsistencia(eventId, participantId = 1, codigo = "SPUNO111")
        crearParticipanteYAsistencia(eventId, participantId = 2, codigo = "SPDOS222")

        api.error = error409(indice = 0, tipo = "ASISTENCIA", mensaje = "El evento 999 no existe.")
        val primero = repository.sync()

        assertTrue(primero is SyncResult.Failure)
        val fallo = primero as SyncResult.Failure
        assertEquals(1, fallo.rejected)
        assertFalse(fallo.retryable)
        assertEquals(1, repository.observeRejectedCount().first())
        assertEquals(
            "El evento 999 no existe.",
            repository.observeRejectionMessage().first()
        )
        assertEquals(1, database.attendanceDao().pendingForSync().size)
        assertEquals(1, database.attendanceDao().getById(1)!!.let { if (it.rechazado) 1 else 0 })

        api.error = null
        val segundo = repository.sync()

        assertTrue(segundo is SyncResult.Success)
        assertEquals(0, database.attendanceDao().pendingForSync().size)
        assertEquals(1, repository.observeRejectedCount().first())
    }

    @Test
    fun descartarRechazadosLimpiaElEstadoYLaFila() = runBlocking {
        val eventId = crearEvento()
        crearParticipanteYAsistencia(eventId, participantId = 1, codigo = "SPUNO333")

        api.error = error409(indice = 0, tipo = "ASISTENCIA", mensaje = "Dato invalido.")
        repository.sync()

        assertEquals(1, repository.observeRejectedCount().first())
        assertEquals(1, repository.discardRejected())
        assertEquals(0, repository.observeRejectedCount().first())
        assertNull(database.attendanceDao().getById(1))
        assertNull(repository.observeRejectionMessage().first())
    }

    @Test
    fun rechazoSinDetalleMarcaTodoParaNoReintentarEnBucle() = runBlocking {
        val eventId = crearEvento()
        crearParticipanteYAsistencia(eventId, participantId = 1, codigo = "SPUNO444")

        api.error = error409SinDetalle()
        val resultado = repository.sync()

        assertTrue(resultado is SyncResult.Failure)
        assertEquals(1, (resultado as SyncResult.Failure).rejected)
        assertTrue(database.attendanceDao().pendingForSync().isEmpty())
        assertEquals(1, repository.observeRejectedCount().first())
    }

    private suspend fun crearEvento(): Long = database.eventDao().insert(
        EventEntity(
            name = "Evento",
            venue = "Sede",
            startAt = 1_000,
            endAt = 2_000,
            remoteId = 10,
            remoteProjectId = 1
        )
    )

    private suspend fun crearParticipanteYAsistencia(
        eventId: Long,
        participantId: Long,
        codigo: String
    ) {
        database.participantDao().insert(
            ParticipantEntity(
                id = participantId,
                eventId = eventId,
                code = codigo,
                name = "Participante $participantId",
                document = "11111111-$participantId",
                documentNormalized = "11111111$participantId",
                usuarioUuid = "11111111-1111-4111-8111-11111111111$participantId",
                provisioned = true
            )
        )
        database.attendanceDao().insert(
            AttendanceEntity(
                eventId = eventId,
                participantId = participantId,
                attendedAt = 1_500,
                method = "QR"
            )
        )
    }

    private fun error409(indice: Int, tipo: String, mensaje: String): HttpException {
        val cuerpo = """
            {"success":false,"data":{"errores":[
              {"indice":$indice,"tipo":"$tipo","codigo":"EVENTO_NO_EXISTE","mensaje":"$mensaje"}
            ]},"message":"El lote no se aplico."}
        """.trimIndent()
        return HttpException(
            Response.error<Any>(
                409,
                cuerpo.toResponseBody("application/json".toMediaType())
            )
        )
    }

    private fun error409SinDetalle(): HttpException = HttpException(
        Response.error<Any>(
            409,
            """{"success":false,"message":"El lote no se aplico."}"""
                .toResponseBody("application/json".toMediaType())
        )
    )

    private class FakeSyncApi : SyncApi {
        var error: HttpException? = null
        var calls = 0

        override suspend fun subirLote(request: SincronizacionRequest): SincronizacionResponse {
            calls++
            error?.let { throw it }
            return SincronizacionResponse(
                aceptados = request.asistencias.size + request.encuestas.size
            )
        }
    }

    private class FakeParticipanteApi : ParticipanteApi {
        override suspend fun registrar(request: ParticipanteRequest): ParticipanteResponse =
            ParticipanteResponse(usuarioUuid = request.usuarioUuid, creado = true)
    }
}
