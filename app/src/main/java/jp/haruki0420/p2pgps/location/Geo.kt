package jp.haruki0420.p2pgps.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 地理計算の小さなヘルパー（Android 非依存・JVM テスト可能）。 */
object Geo {

    /** 地球の平均半径（m）。 */
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    /**
     * 2 点間の大円距離（m）。Haversine 公式。
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val sinHalfLat = sin(dLat / 2.0)
        val sinHalfLon = sin(dLon / 2.0)
        val a = sinHalfLat * sinHalfLat +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sinHalfLon * sinHalfLon
        return 2.0 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
