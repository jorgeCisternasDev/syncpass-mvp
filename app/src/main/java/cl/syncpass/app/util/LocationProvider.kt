package cl.syncpass.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(timeoutMs: Long = 5_000L): Location? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider ->
                runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
            }
        if (providers.isEmpty()) return@withContext null

        val lastKnown = providers
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }

        val fresh = providers.firstNotNullOfOrNull { provider ->
            requestSingleLocation(manager, provider, timeoutMs)
        }
        fresh ?: lastKnown
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleLocation(
        manager: LocationManager,
        provider: String,
        timeoutMs: Long
    ): Location? = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        lateinit var listener: LocationListener

        fun finish(location: Location?) {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            runCatching { manager.removeUpdates(listener) }
            if (continuation.isActive) continuation.resume(location)
        }

        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                finish(location)
            }

            override fun onProviderDisabled(provider: String) {
                finish(null)
            }

            override fun onProviderEnabled(provider: String) = Unit

            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        continuation.invokeOnCancellation {
            handler.removeCallbacksAndMessages(null)
            runCatching { manager.removeUpdates(listener) }
        }

        try {
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            handler.postDelayed({ finish(null) }, timeoutMs)
        } catch (_: Exception) {
            finish(null)
        }
    }
}
