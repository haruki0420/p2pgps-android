package jp.haruki0420.p2pgps.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [LocationManager] による位置取得（GPS + NETWORK）。
 *
 * Google Play Services の FusedLocationProvider は使わない（仕様 §3 / architecture §3）。
 * 画面 OFF でも送信を継続するため、呼び出し側（前景サービス）が購読を保持する。
 */
class LocationProvider(private val context: Context) {

    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    /** 位置情報の権限（FINE または COARSE）があるか。 */
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /** GPS / NETWORK のどちらかが有効か。 */
    fun isLocationEnabled(): Boolean {
        val manager = locationManager ?: return false
        return try {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    /** 直近の既知位置（起動直後の初回送信を早めるため）。 */
    fun lastKnownFix(): GpsFix? {
        if (!hasPermission()) return null
        val manager = locationManager ?: return null
        return try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { provider -> manager.getLastKnownLocation(provider)?.toGpsFix() }
                .maxByOrNull { it.timestampMillis }
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * 位置更新の Flow。collect を止めると位置更新も解除される。
     *
     * @param minTimeMs 最小更新間隔（Android のヒントであり厳密ではない）
     * @param minDistanceM 最小移動距離
     */
    @Suppress("DEPRECATION")
    fun fixes(minTimeMs: Long = 1_000L, minDistanceM: Float = 0f): Flow<GpsFix> = callbackFlow {
        val manager = locationManager
        if (manager == null) {
            close(IllegalStateException("LocationManager が利用できません"))
            return@callbackFlow
        }
        if (!hasPermission()) {
            close(SecurityException("位置情報の権限がありません"))
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toGpsFix())
            }

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("Deprecated in API 30")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        try {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(
                        provider,
                        minTimeMs,
                        minDistanceM,
                        listener,
                        Looper.getMainLooper(),
                    )
                }
            }
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        } catch (e: IllegalArgumentException) {
            // 端末に存在しない provider は無視する。
        }

        awaitClose {
            try {
                manager.removeUpdates(listener)
            } catch (e: SecurityException) {
                // 権限が剥奪された場合は解除できないが、プロセス終了で解放される。
            }
        }
    }

    private fun Location.toGpsFix(): GpsFix = GpsFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        provider = provider ?: "unknown",
        timestampMillis = time,
        altitudeMeters = if (hasAltitude()) altitude else null,
        verticalAccuracyMeters = if (hasVerticalAccuracy()) verticalAccuracyMeters else null,
        speedMps = if (hasSpeed()) speed else null,
        bearingDegrees = if (hasBearing()) bearing else null,
        simulated = isMockLocation(),
    )

    private fun Location.isMockLocation(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isMock
        } else {
            @Suppress("DEPRECATION")
            isFromMockProvider
        }
}
