package cl.syncpass.app

import android.app.Application
import android.content.Context
import cl.syncpass.app.data.local.AppDatabase
import cl.syncpass.app.data.remote.NetworkModule
import cl.syncpass.app.data.remote.SessionStore
import cl.syncpass.app.data.repository.AccreditationRepository
import cl.syncpass.app.data.repository.AuthRepository
import cl.syncpass.app.data.repository.CustomSurveyRepository
import cl.syncpass.app.data.repository.EventRepository
import cl.syncpass.app.data.repository.ExportRepository
import cl.syncpass.app.data.repository.ParticipantRepository
import cl.syncpass.app.data.repository.ProjectRepository
import cl.syncpass.app.data.repository.SurveyRepository
import cl.syncpass.app.data.repository.SyncRepository
import cl.syncpass.app.util.ConnectivityMonitor
import cl.syncpass.app.util.LocationProvider

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.get(context)

    val sessionStore = SessionStore(context.applicationContext)
    private val network = NetworkModule(
        baseUrl = BuildConfig.API_BASE_URL,
        sessionStore = sessionStore,
        debugLogging = BuildConfig.DEBUG
    )

    private val eventDao = database.eventDao()
    private val projectDao = database.projectDao()
    private val participantDao = database.participantDao()
    private val attendanceDao = database.attendanceDao()
    private val surveyDao = database.surveyDao()

    val authRepository = AuthRepository(network.authApi, sessionStore)
    val syncRepository = SyncRepository(database, network.syncApi)
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

class SyncPassApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
