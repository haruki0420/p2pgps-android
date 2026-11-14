package jp.haruki0420.p2pgps.location

/**
 * フィルタ済みの測位結果。protocol 層へ渡す前のドメインモデル。
 *
 * Android の [android.location.Location] をそのまま持ち回らず、
 * このデータクラスに変換することで LocationFilter を JVM でテストできる。
 */
data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val provider: String,
    val timestampMillis: Long,
    val altitudeMeters: Double? = null,
    val verticalAccuracyMeters: Float? = null,
    val speedMps: Float? = null,
    val bearingDegrees: Float? = null,
    val simulated: Boolean = false,
) {
    val hasAltitude: Boolean get() = altitudeMeters != null
    val hasSpeed: Boolean get() = speedMps != null
    val hasBearing: Boolean get() = bearingDegrees != null

    /** 移動中とみなす閾値（仕様 §3.1 bit6 の例: 0.5 m/s 以上）。 */
    val isMoving: Boolean get() = (speedMps ?: 0f) >= MOVING_SPEED_THRESHOLD_MPS

    companion object {
        const val MOVING_SPEED_THRESHOLD_MPS: Float = 0.5f
    }
}
