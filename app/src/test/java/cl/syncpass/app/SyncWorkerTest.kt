package cl.syncpass.app

import androidx.room.Room
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import cl.syncpass.app.auth.AppRole
import cl.syncpass.app.data.local.AppDatabase
import cl.syncpass.app.data.local.AttendanceEntity
import cl.syncpass.app.data.local.EventEntity
import cl.syncpass.app.data.local.ParticipantEntity
import cl.syncpass.app.data.remote.CatalogoApi
import cl.syncpass.app.data.remote.EventoRemoto
import cl.syncpass.app.data.remote.ParticipanteApi
import cl.syncpass.app.data.remote.ParticipanteRequest
import cl.syncpass.app.data.remote.ParticipanteResponse
import cl.syncpass.app.data.remote.ProyectoRemoto
import cl.syncpass.app.data.remote.SincronizacionRequest
import cl.syncpass.app.data.remote.SincronizacionResponse
import cl.syncpass.app.data.remote.SyncApi
import cl.syncpass.app.data.repository.CatalogoRepository
import cl.syncpass.app.data.repository.ProvisioningRepository
import cl.syncpass.app.data.repository.SyncRepository
import cl.syncpass.app.sync.SyncWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = SyncTestApp::class)
class SyncWorkerTest {

    private lateinit var app: SyncTestApp
    private lateinit var database: AppDatabase
    private lateinit var syncApi: FakeSyncApi
    private lateinit var catalogoApi: FakeCatalogoApi

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication() as SyncTestApp
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncApi = FakeSyncApi()
        catalogoApi = FakeCatalogoApi()
        app.container.syncRepository = SyncRepository(
            database = database,
            syncApi = syncApi,
            provisioningRepository = ProvisioningRepository(database, FakeParticipanteApi())
        )
        app.container.catalogoRepository = CatalogoRepository(database, catalogoApi)
        app.container.sessionStore.clear()
        app.container.sessionStore.save(
            accessToken = "token-de-prueba",
            refreshToken = null,
            idToken = null,
            email = "monitor@syncpass.cl",
            displayName = "Monitor",
            role = AppRole.MONITOR
        )
    }

    @After
    fun tearDown() {
        app.container.sessionStore.clear()
        database.close()
    }

    @Test
    fun w02WorkerConExitoDevuelveSuccessYMarcaLoEnviado() = runBlocking {
        crearPendiente()

        val worker = TestListenableWorkerBuilder<SyncWorker>(app).build()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, syncApi.calls)
        assertTrue(database.attendanceDao().pendingForSync().isEmpty())
    }

    @Test
    fun w03WorkerConFalloDeRedDevuelveRetrySinMarcarPendientes() = runBlocking {
        crearPendiente()
        syncApi.failure = IOException("sin red")

        val worker = TestListenableWorkerBuilder<SyncWorker>(app).build()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(1, syncApi.calls)
        assertEquals(1, database.attendanceDao().pendingForSync().size)
    }

    @Test
    fun h301WorkerDescargaCatalogoAunqueNoHayaLotePendiente() = runBlocking {
        catalogoApi.proyectos = listOf(
            ProyectoRemoto(
                proyectoId = 10,
                titulo = "Proyecto remoto",
                descripcion = "Descripcion",
                areaTematica = "Salud",
                unidadVecinal = "Centro",
                fechaInicio = 1_000,
                fechaTermino = 2_000,
                estado = "ACTIVO"
            )
        )
        catalogoApi.eventos = mapOf(
            10L to listOf(
                EventoRemoto(
                    eventoId = 100,
                    proyectoId = 10,
                    nombre = "Evento remoto",
                    fechaHora = 1_500,
                    fechaTermino = 2_500,
                    cuposMax = 20,
                    recinto = "Sede",
                    latitud = -33.5,
                    longitud = -70.6,
                    radioMetros = 100
                )
            )
        )

        val worker = TestListenableWorkerBuilder<SyncWorker>(app).build()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, syncApi.calls)
        assertEquals(1, catalogoApi.calls)
        val proyecto = database.projectDao().findByRemoteId(10)
        val evento = database.eventDao().findByRemoteId(100)
        assertNotNull(proyecto)
        assertNotNull(evento)
        assertEquals(proyecto!!.id, evento!!.projectId)
        assertEquals(10L, evento.remoteProjectId)
    }

    private suspend fun crearPendiente() {
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
                code = "SPABC123",
                name = "Participante Ficticio",
                document = "11111111-1",
                documentNormalized = "111111111",
                usuarioUuid = "11111111-1111-4111-8111-111111111111",
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

    private class FakeSyncApi : SyncApi {
        var failure: Exception? = null
        var calls = 0

        override suspend fun subirLote(request: SincronizacionRequest): SincronizacionResponse {
            calls++
            failure?.let { throw it }
            return SincronizacionResponse(
                aceptados = request.asistencias.size + request.encuestas.size
            )
        }
    }

    private class FakeParticipanteApi : ParticipanteApi {
        override suspend fun registrar(request: ParticipanteRequest): ParticipanteResponse =
            ParticipanteResponse(usuarioUuid = request.usuarioUuid, creado = true)
    }

    private class FakeCatalogoApi : CatalogoApi {
        var proyectos: List<ProyectoRemoto> = emptyList()
        var eventos: Map<Long, List<EventoRemoto>> = emptyMap()
        var calls = 0

        override suspend fun proyectos(): List<ProyectoRemoto> {
            calls++
            return proyectos
        }

        override suspend fun eventos(proyectoId: Long): List<EventoRemoto> =
            eventos[proyectoId].orEmpty()
    }
}
