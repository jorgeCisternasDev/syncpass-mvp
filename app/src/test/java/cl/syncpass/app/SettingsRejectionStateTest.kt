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
import cl.syncpass.app.data.repository.ExportRepository
import cl.syncpass.app.data.repository.ProvisioningRepository
import cl.syncpass.app.data.repository.SyncRepository
import cl.syncpass.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRejectionStateTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: SyncRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val api = FakeSyncApi(
            HttpException(
                Response.error<Any>(
                    409,
                    """{"success":false,"data":{"errores":[{"indice":0,"tipo":"ASISTENCIA","codigo":"EVENTO_NO_EXISTE","mensaje":"El evento no existe."}]},"message":"El lote no se aplico."}"""
                        .toResponseBody("application/json".toMediaType())
                )
            )
        )
        repository = SyncRepository(
            database = database,
            syncApi = api,
            provisioningRepository = ProvisioningRepository(database, FakeParticipanteApi())
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun ajustesMuestraRechazadosYDescartarLosLimpia() = runTest {
        val eventId = database.eventDao().insert(
            EventEntity(
                name = "Evento",
                venue = "Sede",
                startAt = 1_000,
                endAt = 2_000,
                remoteId = 10,
                remoteProjectId = 1
            )
        )
        database.participantDao().insert(
            ParticipantEntity(
                id = 1,
                eventId = eventId,
                code = "SPESTA01",
                name = "Participante",
                document = "11111111-1",
                documentNormalized = "111111111",
                usuarioUuid = "11111111-1111-4111-8111-111111111111",
                provisioned = true
            )
        )
        database.attendanceDao().insert(
            AttendanceEntity(
                eventId = eventId,
                participantId = 1,
                attendedAt = 1_500,
                method = "QR"
            )
        )

        repository.sync()

        val viewModel = SettingsViewModel(
            statsDao = database.statsDao(),
            exportRepository = ExportRepository(
                context.applicationContext,
                database.eventDao(),
                database.attendanceDao()
            ),
            database = database,
            syncRepository = repository
        )

        val estado = viewModel.state.first { !it.loading }
        assertEquals(1, estado.rejectedSyncCount)
        assertEquals(0, estado.pendingSyncCount)
        assertNotNull(estado.rejectionMessage)

        viewModel.discardRejected()
        val despues = viewModel.state.first { !it.loading && it.rejectedSyncCount == 0 }
        assertEquals(0, despues.rejectedSyncCount)
        assertEquals(0, despues.pendingSyncCount)
    }

    private class FakeSyncApi(private val error: HttpException) : SyncApi {
        override suspend fun subirLote(request: SincronizacionRequest): SincronizacionResponse {
            throw error
        }
    }

    private class FakeParticipanteApi : ParticipanteApi {
        override suspend fun registrar(request: ParticipanteRequest): ParticipanteResponse =
            ParticipanteResponse(usuarioUuid = request.usuarioUuid, creado = true)
    }
}
