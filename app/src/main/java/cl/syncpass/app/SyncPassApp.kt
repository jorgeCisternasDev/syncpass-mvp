package cl.syncpass.app

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import cl.syncpass.app.data.local.AppDatabase
import cl.syncpass.app.data.remote.NetworkModule
import cl.syncpass.app.data.remote.SessionStore
import cl.syncpass.app.data.repository.AccreditationRepository
import cl.syncpass.app.data.repository.AuthRepository
import cl.syncpass.app.data.repository.CatalogoRepository
import cl.syncpass.app.data.repository.CustomSurveyRepository
import cl.syncpass.app.data.repository.EventRepository
import cl.syncpass.app.data.repository.ExportRepository
import cl.syncpass.app.data.repository.ParticipantRepository
import cl.syncpass.app.data.repository.ProjectRepository
import cl.syncpass.app.data.repository.ProvisioningRepository
import cl.syncpass.app.data.repository.SurveyRepository
import cl.syncpass.app.data.repository.SyncRepository
import cl.syncpass.app.sync.SyncScheduler
import cl.syncpass.app.util.ConnectivityMonitor
import cl.syncpass.app.util.LocationProvider

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: AppDatabase = AppDatabase.get(context)

    val sessionStore = SessionStore(context.applicationContext)
    private val network = NetworkModule(
        baseUrl = BuildConfig.API_BASE_URL,
        sessionStore = sessionStore,
        debugLogging = BuildConfig.DEBUG
    )

    val catalogoApi = network.catalogoApi
    val participanteApi = network.participanteApi

    private val eventDao = database.eventDao()
    private val projectDao = database.projectDao()
    private val participantDao = database.participantDao()
    private val attendanceDao = database.attendanceDao()
    private val surveyDao = database.surveyDao()

    val authRepository = AuthRepository(network.authApi, sessionStore)
    val provisioningRepository = ProvisioningRepository(database, network.participanteApi)
    var syncRepository = SyncRepository(database, network.syncApi, provisioningRepository)
        internal set
    var catalogoRepository = CatalogoRepository(database, network.catalogoApi)
        internal set
    val eventRepository = EventRepository(eventDao, projectDao)
    val projectRepository = ProjectRepository(projectDao)
    val participantRepository = ParticipantRepository(eventDao, participantDao, attendanceDao)
    val accreditationRepository = AccreditationRepository(
        eventDao = eventDao,
        participantDao = participantDao,
        attendanceDao = attendanceDao,
        participantRepository = participantRepository
    )
    val surveyRepository = SurveyRepository(attendanceDao, surveyDao)
    val customSurveyRepository = CustomSurveyRepository(
        database = database,
        dao = database.customSurveyDao(),
        eventDao = eventDao
    )
    val exportRepository = ExportRepository(context.applicationContext, eventDao, attendanceDao)
    val statsDao = database.statsDao()
    val locationProvider = LocationProvider(context.applicationContext)
    val connectivityMonitor = ConnectivityMonitor(context.applicationContext)
}

open class SyncPassApp : Application() {
    lateinit var container: AppContainer
        internal set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** Se aísla para que las pruebas no programen trabajo real. */
    open fun configureBackgroundSync() {
        runCatching {
            // En produccion el inicializador por defecto corre despues de
            // onCreate: se inicializa a pedido. En pruebas ya existe.
            runCatching { WorkManager.getInstance(this) }
                .onFailure {
                    WorkManager.initialize(this, Configuration.Builder().build())
                }
            SyncScheduler.schedulePeriodic(this)
            enqueueSyncOnOpenIfSession()
        }.onFailure {
            Log.e(TAG, "No se pudo programar la sincronizacion periodica", it)
        }
    }

    /** Al abrir con sesion se intenta un ciclo inmediato, ademas del periodico. */
    internal fun enqueueSyncOnOpenIfSession() {
        if (!container.sessionStore.accessToken().isNullOrBlank()) {
            SyncScheduler.enqueueOneTime(this)
        }
    }

    private companion object {
        const val TAG = "SyncPassApp"
    }
}
