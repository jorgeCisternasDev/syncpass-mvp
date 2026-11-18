package cl.syncpass.app

import android.content.Context
import androidx.room.Room
import cl.syncpass.app.data.local.AppDatabase
import cl.syncpass.app.data.local.AttendanceEntity
import cl.syncpass.app.data.local.EventEntity
import cl.syncpass.app.data.local.ParticipantEntity
import cl.syncpass.app.data.local.SurveyEntity
import cl.syncpass.app.data.remote.ParticipanteApi
import cl.syncpass.app.data.remote.ParticipanteRequest
import cl.syncpass.app.data.remote.ParticipanteResponse
import cl.syncpass.app.data.remote.SincronizacionRequest
import cl.syncpass.app.data.remote.SincronizacionResponse
import cl.syncpass.app.data.remote.SyncApi
import cl.syncpass.app.data.repository.ProvisioningRepository
import cl.syncpass.app.data.repository.SyncRepository
import cl.syncpass.app.data.repository.SyncResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncOrderTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var order: MutableList<String>
    private lateinit var participanteApi: RecordingParticipanteApi
    private lateinit var syncApi: RecordingSyncApi
    private lateinit var repository: SyncRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        order = mutableListOf()
        participanteApi = RecordingParticipanteApi(order)
        syncApi = RecordingSyncApi(order)
        repository = SyncRepository(
            database = database,
            syncApi = syncApi,
            provisioningRepository = ProvisioningRepository(database, participanteApi)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aprovisionaAntesDeEnviarElLoteYElUuidViajaEnElJson() = runBlocking {
        crearPendientes()

        val result = repository.sync()

        assertEquals(SyncResult.Success(accepted = 2, sent = 2), result)
        assertEquals(listOf("aprovisionar", "lote"), order)
        val request = syncApi.captured!!
        assertEquals(UUID, request.asistencias.single().usuarioUuid)
        assertEquals(UUID, request.encuestas.single().usuarioUuid)
        assertTrue(database.attendanceDao().pendingForSync().isEmpty())
        assertTrue(database.surveyDao().pendingForSync().isEmpty())
        assertTrue(database.participantDao().getById(1)!!.provisioned)
    }

    @Test
    fun siFallaElAprovisionamientoNoEnviaElLoteNiMarcaNada() = runBlocking {
        crearPendientes()
        participanteApi.failure = IOException("sin red")

        val result = repository.sync()

        assertTrue(result is SyncResult.Failure)
        assertEquals(listOf("aprovisionar"), order)
        assertEquals(1, database.attendanceDao().pendingForSync().size)
        assertEquals(1, database.surveyDao().pendingForSync().size)
        assertFalse(database.participantDao().getById(1)!!.provisioned)
    }

    @Test
    fun h305AprovisionaSinLoteYDevuelveNothingPending() = runBlocking {
        val eventId = database.eventDao().insert(
            EventEntity(
                name = "Evento remoto",
                venue = "Sede",
                startAt = 1_000,
                endAt = 2_000,
                remoteId = 10,
                remoteProjectId = 1
            )
        )
        val participantId = database.participantDao().insert(
            ParticipantEntity(
                eventId = eventId,
                code = "SPXYZ789",
                name = "Participante Sin Lote",
                document = "22222222-2",
                documentNormalized = "222222222",
                usuarioUuid = UUID
            )
        )

        val result = repository.syncCompleto()

        assertEquals(SyncResult.NothingPending, result)
        assertEquals(listOf("aprovisionar"), order)
        assertEquals(1, participanteApi.requests.size)
        assertEquals(0, syncApi.calls)
        assertTrue(database.participantDao().getById(participantId)!!.provisioned)
    }

    private suspend fun crearPendientes() {
        val eventId = database.eventDao().insert(
            EventEntity(
                name = "Evento remoto",
                venue = "Sede",
                startAt = 1_000,
                endAt = 2_000,
                remoteId = 10,
                remoteProjectId = 1
            )
        )
        val participantId = database.participantDao().insert(
            ParticipantEntity(
                id = 1,
                eventId = eventId,
                code = "SPABC123",
                name = "Participante Ficticio",
                document = "11111111-1",
                documentNormalized = "111111111",
                usuarioUuid = UUID
            )
        )
        val attendanceId = database.attendanceDao().insert(
            AttendanceEntity(
                eventId = eventId,
                participantId = participantId,
                attendedAt = 1_500,
                method = "QR"
            )
        )
        database.surveyDao().insert(
            SurveyEntity(
                attendanceId = attendanceId,
                eventId = eventId,
                participantId = participantId,
                satisfaction = 5,
                usefulness = 4,
                submittedAt = 1_600
            )
        )
    }

    private class RecordingParticipanteApi(
        private val order: MutableList<String>
    ) : ParticipanteApi {
        val requests = mutableListOf<ParticipanteRequest>()
        var failure: Exception? = null

        override suspend fun registrar(request: ParticipanteRequest): ParticipanteResponse {
            order += "aprovisionar"
            failure?.let { throw it }
            requests += request
            return ParticipanteResponse(usuarioUuid = request.usuarioUuid, creado = true)
        }
    }

    private class RecordingSyncApi(
        private val order: MutableList<String>
    ) : SyncApi {
        var captured: SincronizacionRequest? = null
        var calls = 0

        override suspend fun subirLote(request: SincronizacionRequest): SincronizacionResponse {
            calls++
            order += "lote"
            captured = request
            return SincronizacionResponse(aceptados = 2)
        }
    }

    private companion object {
        const val UUID = "11111111-1111-4111-8111-111111111111"
    }
}
