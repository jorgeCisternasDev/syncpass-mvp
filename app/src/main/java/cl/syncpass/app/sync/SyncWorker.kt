package cl.syncpass.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cl.syncpass.app.SyncPassApp
import cl.syncpass.app.data.repository.SyncResult

/**
 * Sincronizacion en segundo plano.
 *
 * - `success`: no habia pendientes o el lote se envio completo.
 * - `retry`: fallo de red o respuesta 5xx; WorkManager aplica backoff exponencial.
 * - `failure`: no hay sesion valida o el error no es recuperable (4xx, excepcion local).
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SyncPassApp ?: return Result.failure()
        val container = app.container
        if (container.sessionStore.accessToken().isNullOrBlank()) return Result.failure()

        val lote = container.syncRepository.syncCompleto()

        // El catalogo se descarga aunque no haya lote pendiente. Un fallo de
        // catalogo no cambia el resultado del lote: el proximo ciclo reintenta.
        runCatching { container.catalogoRepository.refrescar() }

        return when (lote) {
            SyncResult.NothingPending -> Result.success()
            is SyncResult.Success -> Result.success()
            is SyncResult.Failure -> if (lote.retryable) Result.retry() else Result.failure()
        }
    }
}
