package jp.haruki0420.p2pgps.location

import kotlin.math.roundToInt

/**
 * 位置情報の精度・外れ値除去フィルタ。
 *
 * - 精度が [minAccuracyMeters] より悪い fix は捨てる
 * - 直前の採用点から [maxJumpMeters] を超えて飛び、かつ [jumpGraceMillis] 未満の
 *   短時間での移動は外れ値として捨てる
 *
 * Android に依存しないため JVM ユニットテストで検証できる。
 */
class LocationFilter(
    private val minAccuracyMeters: Float = 100f,
    private val maxJumpMeters: Double = 1_000.0,
    private val jumpGraceMillis: Long = 10_000L,
) {

    private var lastAccepted: GpsFix? = null

    /** 直前に採用した fix（未採用なら null）。 */
    val lastFix: GpsFix? get() = lastAccepted

    fun reset() {
        lastAccepted = null
    }

    /** fix を検証し、採用する場合は [FilterResult.Accepted] を返す。 */
    fun accept(fix: GpsFix): FilterResult {
        if (!fix.latitude.isFinite() || !fix.longitude.isFinite()) {
            return FilterResult.Rejected("座標が数値ではありません")
        }
        if (fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0) {
            // 座標値そのものはログへ流さない（AppLog のプライバシー方針）。
            return FilterResult.Rejected("座標が範囲外です")
        }
        if (!fix.accuracyMeters.isFinite() || fix.accuracyMeters <= 0f) {
            return FilterResult.Rejected("精度が不明です")
        }
        if (fix.accuracyMeters > minAccuracyMeters) {
            return FilterResult.Rejected(
                "精度が不足しています (${fix.accuracyMeters.roundToInt()}m > ${minAccuracyMeters.roundToInt()}m)",
            )
        }
        val previous = lastAccepted
        if (previous != null) {
            val distance = Geo.distanceMeters(
                previous.latitude,
                previous.longitude,
                fix.latitude,
                fix.longitude,
            )
            val elapsed = fix.timestampMillis - previous.timestampMillis
            if (distance > maxJumpMeters && elapsed in 0 until jumpGraceMillis) {
                return FilterResult.Rejected(
                    "外れ値の可能性 (${distance.roundToInt()}m / ${elapsed}ms)",
                )
            }
        }
        lastAccepted = fix
        return FilterResult.Accepted(fix)
    }
}

/** [LocationFilter.accept] の結果。 */
sealed class FilterResult {
    /** 採用した fix。 */
    data class Accepted(val fix: GpsFix) : FilterResult()

    /** 破棄した理由（ログ表示用）。 */
    data class Rejected(val reason: String) : FilterResult()
}
