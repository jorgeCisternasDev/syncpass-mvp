package cl.syncpass.app

import android.content.Context
import androidx.room.Room
import cl.syncpass.app.data.local.AppDatabase
import cl.syncpass.app.data.local.EventEntity
import cl.syncpass.app.data.local.ParticipantEntity
import cl.syncpass.app.data.remote.ParticipanteApi
import cl.syncpass.app.data.remote.ParticipanteRequest
import cl.syncpass.app.data.remote.ParticipanteResponse
import cl.syncpass.app.data.repository.ProvisioningRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProvisioningRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var api: FakeParticipanteApi
    private lateinit var repository: ProvisioningRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        api = FakeParticipanteApi()
        repository = ProvisioningRepository(database, api)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun filaAntiguaSinUuidRecibeUnoEstableYReutilizaElMismoAlReintentar() = runBlocking {
        val eventId = crearEvento(remoteId = 10)
        val participantId = database.participantDao().insert(
            ParticipantEntity(
                eventId = eventId,
                code = "SPABC123",
                name = "Participante Ficticio",
                document = "11111111-1",
                documentNormalized = "111111111",
                usuarioUuid = null
            )
        )

        api.failure = IOException("sin red")
        try {
            repository.provisionPending()
            fail("El fallo de red debe propagarse para conservar los pendientes")
        } catch (_: IOException) {
        }

        val trasFallo = database.participantDao().getById(participantId)!!
        assertNotNull(trasFallo.usuarioUuid)
        assertFalse(trasFallo.provisioned)
        val uuid = trasFallo.usuarioUuid!!

        api.failure = null
        val report = repository.provisionPending()

        assertEquals(1, report.provisioned)
        assertEquals(uuid, api.requests.single().usuarioUuid)
        val trasExito = database.participantDao().getById(participantId)!!
        assertTrue(trasExito.provisioned)
        assertEquals(uuid, trasExito.usuarioUuid)

        val segundoIntento = repository.provisionPending()
        assertEquals(0, segundoIntento.provisioned)
        assertEquals(1, api.requests.size)
    }

    @Test
    fun provisionaConEventoRemotoYEmailDeRespaldo() = runBlocking {
        val eventId = crearEvento(remoteId = 10)
        val participantId = database.participantDao().insert(
            ParticipantEntity(
                eventId = eventId,
                code = "SPABC124",
                name = "Participante Ficticio",
                document = "22222222-2",
                documentNormalized = "222222222",
                email = "  ",
                usuarioUuid = "11111111-1111-4111-8111-111111111111"
            )
        )

        repository.provisionPending()

        val request = api.requests.single()
        assertEquals("11111111-1111-4111-8111-111111111111", request.usuarioUuid)
        assertEquals(10L, request.eventoId)
        assertEquals("Participante Ficticio", request.nombre)
        assertEquals(
            "participante+11111111-1111-4111-8111-111111111111@syncpass.local",
            request.email
        )
        assertTrue(database.participantDao().getById(participantId)!!.provisioned)
    }

    @Test
    fun sinEventoRemotoAsignaUuidPeroNoLlamaALaApi() = runBlocking {
        val eventId = crearEvento(remoteId = null)
        val participantId = database.participantDao().insert(
            ParticipantEntity(
                eventId = eventId,
                code = "SPABC125",
                name = "Participante Local",
                document = "33333333-3",
                documentNormalized = "333333333",
                usuarioUuid = null
            )
        )

        val report = repository.provisionPending()

        assertEquals(0, report.provisioned)
        assertEquals(1, report.pending)
        assertTrue(api.requests.isEmpty())
        val guardado = database.participantDao().getById(participantId)!!
        assertNotNull(guardado.usuarioUuid)
        assertFalse(guardado.provisioned)
    }

    private suspend fun crearEvento(remoteId: Long?): Long = database.eventDao().insert(
        EventEntity(
            name = "Evento",
            venue = "Sede",
            startAt = 1_000,
            endAt = 2_000,
            remoteId = remoteId,
            remoteProjectId = remoteId?.let { 1L }
        )
    )

    private class FakeParticipanteApi : ParticipanteApi {
        val requests = mutableListOf<ParticipanteRequest>()
        var failure: Exception? = null

        override suspend fun registrar(request: ParticipanteRequest): ParticipanteResponse {
            failure?.let { throw it }
            requests += request
            return ParticipanteResponse(usuarioUuid = request.usuarioUuid, creado = true)
        }
    }
}
