package cl.syncpass.app

import android.content.Context
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import cl.syncpass.app.auth.AppRole
import cl.syncpass.app.sync.SyncScheduler
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = SyncTestApp::class)
class SyncSchedulerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After
    fun tearDown() {
        (context as? SyncPassApp)?.container?.sessionStore?.clear()
    }

    @Test
    fun w01SchedulerEncolaTrabajoUnicoEtiquetadoYPeriodicoDeQuinceMinutos() {
        SyncScheduler.enqueueOneTime(context)
        SyncScheduler.enqueueOneTime(context)

        val workManager = WorkManager.getInstance(context)
        val unicos = workManager
            .getWorkInfosForUniqueWork(SyncScheduler.NOMBRE_UNICO)
            .get()

        assertEquals(1, unicos.size)
        val unico = unicos.single()
        assertEquals(WorkInfo.State.ENQUEUED, unico.state)
        assertTrue(unico.tags.contains(SyncScheduler.ETIQUETA))
        assertEquals(NetworkType.CONNECTED, unico.constraints.requiredNetworkType)

        SyncScheduler.schedulePeriodic(context)
        SyncScheduler.schedulePeriodic(context)

        val periodicos = workManager
            .getWorkInfosForUniqueWork(SyncScheduler.NOMBRE_PERIODICO)
            .get()
        assertEquals(1, periodicos.size)
        val periodico = periodicos.single()
        assertTrue(periodico.tags.contains(SyncScheduler.ETIQUETA))
        assertEquals(NetworkType.CONNECTED, periodico.constraints.requiredNetworkType)
        assertNotNull(periodico.periodicityInfo)
        assertEquals(
            15L * 60L * 1000L,
            periodico.periodicityInfo!!.repeatIntervalMillis
        )
    }

    @Test
    fun h302AlAbrirConSesionEncolaUnicoYSinTokenNo() {
        val app = context as SyncTestApp
        val workManager = WorkManager.getInstance(context)
        val antes = workManager
            .getWorkInfosForUniqueWork(SyncScheduler.NOMBRE_UNICO)
            .get()
            .map { it.id }
            .toSet()

        app.container.sessionStore.clear()
        app.enqueueSyncOnOpenIfSession()
        val sinToken = workManager
            .getWorkInfosForUniqueWork(SyncScheduler.NOMBRE_UNICO)
            .get()
            .map { it.id }
            .toSet()
        assertEquals(antes, sinToken)

        app.container.sessionStore.save(
            accessToken = "token-de-prueba",
            refreshToken = null,
            idToken = null,
            email = "monitor@syncpass.cl",
            displayName = "Monitor",
            role = AppRole.MONITOR
        )
        app.enqueueSyncOnOpenIfSession()

        val conToken = workManager
            .getWorkInfosForUniqueWork(SyncScheduler.NOMBRE_UNICO)
            .get()
        assertEquals(antes.size + 1, conToken.size)
        assertTrue(conToken.any { it.tags.contains(SyncScheduler.ETIQUETA) })
    }
}
