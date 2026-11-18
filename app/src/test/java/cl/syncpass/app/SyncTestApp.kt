package cl.syncpass.app

/**
 * Aplicacion de prueba: no programa el trabajo periodico real; cada prueba
 * inicializa WorkManager con WorkManagerTestInitHelper.
 */
class SyncTestApp : SyncPassApp() {
    override fun configureBackgroundSync() {
        // Intencionalmente vacio en pruebas.
    }
}
