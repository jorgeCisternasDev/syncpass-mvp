package cl.syncpass.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Programa la sincronizacion con WorkManager.
 *
 * El trabajo de un disparo usa el nombre unico `syncpass-sync`; el periodico
 * lleva otro nombre para no chocar con el anterior en la tabla de nombres.
 */
object SyncScheduler {
    const val NOMBRE_UNICO = "syncpass-sync"
    const val NOMBRE_PERIODICO = "syncpass-sync-periodico"
    const val ETIQUETA = "syncpass-sync"

    private const val INTERVALO_MINUTOS = 15L
    private const val BACKOFF_SEGUNDOS = 30L

    fun enqueueOneTime(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(restriccionesDeRed())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_SEGUNDOS,
                TimeUnit.SECONDS
            )
            .addTag(ETIQUETA)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            NOMBRE_UNICO,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            INTERVALO_MINUTOS,
            TimeUnit.MINUTES
        )
            .setConstraints(restriccionesDeRed())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_SEGUNDOS,
                TimeUnit.SECONDS
            )
            .addTag(ETIQUETA)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            NOMBRE_PERIODICO,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun restriccionesDeRed(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
