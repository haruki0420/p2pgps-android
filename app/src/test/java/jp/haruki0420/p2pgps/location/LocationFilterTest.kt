package jp.haruki0420.p2pgps.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 位置フィルタ（精度・外れ値除去）の JVM テスト。
 *
 * BLE や LocationManager は実機なしでは検証できないが、
 * フィルタは純粋関数なのでここで担保する。
 */
class LocationFilterTest {

    private fun fix(
        latitude: Double = 35.6812360,
        longitude: Double = 139.7671250,
        accuracyMeters: Float = 5f,
        timestampMillis: Long = 1_763_136_000_000L,
        speedMps: Float? = null,
    ) = GpsFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        provider = "gps",
        timestampMillis = timestampMillis,
        speedMps = speedMps,
    )

    @Test
    fun acceptsGoodFix() {
        val filter = LocationFilter()
        val result = filter.accept(fix())
        assertTrue(result is FilterResult.Accepted)
        assertEquals(fix(), (result as FilterResult.Accepted).fix)
        assertEquals(fix(), filter.lastFix)
    }

    @Test
    fun rejectsPoorAccuracy() {
        val filter = LocationFilter(minAccuracyMeters = 50f)
        val result = filter.accept(fix(accuracyMeters = 120f))
        assertTrue(result is FilterResult.Rejected)
    }

    @Test
    fun rejectsUnknownAccuracy() {
        val filter = LocationFilter()
        assertTrue(filter.accept(fix(accuracyMeters = 0f)) is FilterResult.Rejected)
        assertTrue(filter.accept(fix(accuracyMeters = Float.NaN)) is FilterResult.Rejected)
    }

    @Test
    fun rejectsOutOfRangeCoordinate() {
        val filter = LocationFilter()
        assertTrue(filter.accept(fix(latitude = 91.0)) is FilterResult.Rejected)
        assertTrue(filter.accept(fix(longitude = 181.0)) is FilterResult.Rejected)
        assertTrue(filter.accept(fix(latitude = Double.NaN)) is FilterResult.Rejected)
    }

    @Test
    fun rejectsSuddenJump() {
        val filter = LocationFilter(maxJumpMeters = 200.0, jumpGraceMillis = 5_000L)
        assertTrue(filter.accept(fix(timestampMillis = 1_000_000L)) is FilterResult.Accepted)

        // 1 秒で約 11km 移動する fix は外れ値。
        val jumped = fix(
            latitude = 35.7812360,
            longitude = 139.7671250,
            timestampMillis = 1_001_000L,
        )
        assertTrue(filter.accept(jumped) is FilterResult.Rejected)
        // 直前の採用点は変わらない。
        assertEquals(35.6812360, filter.lastFix!!.latitude, 1e-9)
    }

    @Test
    fun acceptsJumpAfterGracePeriod() {
        val filter = LocationFilter(maxJumpMeters = 200.0, jumpGraceMillis = 5_000L)
        assertTrue(filter.accept(fix(timestampMillis = 1_000_000L)) is FilterResult.Accepted)

        // 10 秒後の大きな移動は正当な移動として受理する。
        val moved = fix(
            latitude = 35.7812360,
            longitude = 139.7671250,
            timestampMillis = 1_010_000L,
        )
        assertTrue(filter.accept(moved) is FilterResult.Accepted)
    }

    @Test
    fun reset_forgetsPreviousFix() {
        val filter = LocationFilter(maxJumpMeters = 200.0, jumpGraceMillis = 5_000L)
        filter.accept(fix(timestampMillis = 1_000_000L))
        filter.reset()
        val jumped = fix(
            latitude = 35.7812360,
            longitude = 139.7671250,
            timestampMillis = 1_001_000L,
        )
        assertTrue(filter.accept(jumped) is FilterResult.Accepted)
    }

    @Test
    fun geoDistance_isApproximatelyCorrect() {
        // 東京駅から北へ約 1km。
        val distance = Geo.distanceMeters(35.6812360, 139.7671250, 35.6902360, 139.7671250)
        assertEquals(1_000.0, distance, 10.0)

        // 同一点は 0。
        assertEquals(0.0, Geo.distanceMeters(35.6812360, 139.7671250, 35.6812360, 139.7671250), 1e-6)
    }

    @Test
    fun gpsFix_isMovingAtHalfMeterPerSecond() {
        assertTrue(fix(speedMps = 0.5f).isMoving)
        assertTrue(fix(speedMps = 3f).isMoving)
        assertTrue(!fix(speedMps = 0.49f).isMoving)
        assertTrue(!fix(speedMps = null).isMoving)
    }
}
