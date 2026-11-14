package jp.haruki0420.p2pgps.protocol

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Location Packet（20 bytes 固定）。仕様 §3。
 *
 * フィールドはすべて仕様の表現（スケール済み整数）で保持する。
 * 度（Double）との変換は [encodeCoordinate] / [decodeCoordinate] を使う。
 *
 * ```
 * offset 0  version   uint8   0x01 固定
 * offset 1  flags     uint8   §3.1
 * offset 2  sequence  uint16 LE
 * offset 4  latitude  int32 LE  緯度 × 10^7
 * offset 8  longitude int32 LE  経度 × 10^7
 * offset 12 altitude  int16 LE  標高 m（切り捨て、無効時 0）
 * offset 14 accuracy  uint8     水平精度 m（255 = 不明）
 * offset 15 speed     uint8     速度 m/s（255 = 不明）
 * offset 16 timestamp uint32 LE Unix エポック秒（UTC）
 * ```
 */
data class LocationPacket(
    val flags: Int = 0,
    val sequence: Int = 0,
    val latitude: Int = 0,
    val longitude: Int = 0,
    val altitudeM: Int = 0,
    val accuracyM: Int = UNKNOWN_U8,
    val speedMps: Int = UNKNOWN_U8,
    val timestamp: Long = 0L,
) {

    init {
        require(flags in 0..0xFF) { "flags は uint8 の範囲で指定してください: $flags" }
        require(sequence in 0..0xFFFF) { "sequence は uint16 の範囲で指定してください: $sequence" }
        require(altitudeM in Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt()) {
            "altitude_m は int16 の範囲で指定してください: $altitudeM"
        }
        require(accuracyM in 0..0xFF) { "accuracy_m は uint8 の範囲で指定してください: $accuracyM" }
        require(speedMps in 0..0xFF) { "speed_mps は uint8 の範囲で指定してください: $speedMps" }
        require(timestamp in 0..MAX_TIMESTAMP) {
            "timestamp は uint32 の範囲で指定してください: $timestamp"
        }
    }

    /** 有効な fix か（flags bit0）。 */
    val hasValidFix: Boolean get() = (flags and FLAG_VALID_FIX) != 0

    /** 標高が有効か（flags bit1）。 */
    val hasAltitude: Boolean get() = (flags and FLAG_ALTITUDE_VALID) != 0

    /** 速度が有効か（flags bit2）。 */
    val hasSpeed: Boolean get() = (flags and FLAG_SPEED_VALID) != 0

    /** シミュレート位置か（flags bit3）。 */
    val isSimulated: Boolean get() = (flags and FLAG_SIMULATED) != 0

    /** 充電中か（flags bit4）。 */
    val isCharging: Boolean get() = (flags and FLAG_CHARGING) != 0

    /** バッテリー残量 20% 未満か（flags bit5）。 */
    val isLowBattery: Boolean get() = (flags and FLAG_LOW_BATTERY) != 0

    /** 移動中か（flags bit6）。 */
    val isMoving: Boolean get() = (flags and FLAG_MOVING) != 0

    /** 仕様 §3.2 の 20 bytes へエンコードする。 */
    fun encode(): ByteArray {
        val out = ByteArray(SIZE)
        Bytes.putU8(out, 0, VERSION)
        Bytes.putU8(out, 1, flags)
        Bytes.putU16Le(out, 2, sequence)
        Bytes.putI32Le(out, 4, latitude)
        Bytes.putI32Le(out, 8, longitude)
        Bytes.putI16Le(out, 12, altitudeM)
        Bytes.putU8(out, 14, accuracyM)
        Bytes.putU8(out, 15, speedMps)
        Bytes.putU32Le(out, 16, timestamp)
        return out
    }

    /** 次の sequence を持つコピー（65535 の次は 0）。 */
    fun nextSequence(): LocationPacket = copy(sequence = (sequence + 1) and 0xFFFF)

    /**
     * 仕様 §3.3 の受信側検証（1〜5）。
     *
     * 長さと version は [decode] が検証済みである前提。
     * 問題がなければ null を返す。
     */
    fun validationError(): ValidationError? {
        if ((flags and FLAG_RESERVED) != 0) return ValidationError.RESERVED_FLAG_SET
        if (latitude !in MIN_LATITUDE..MAX_LATITUDE) return ValidationError.LATITUDE_OUT_OF_RANGE
        if (longitude !in MIN_LONGITUDE..MAX_LONGITUDE) return ValidationError.LONGITUDE_OUT_OF_RANGE
        if (hasValidFix && isNearNullIsland()) return ValidationError.NULL_ISLAND
        return null
    }

    /** (0, 0) 近傍（±0.0001°）か。仕様 §3.3-5 の Null Island 対策。 */
    fun isNearNullIsland(): Boolean =
        abs(latitude) <= NULL_ISLAND_THRESHOLD && abs(longitude) <= NULL_ISLAND_THRESHOLD

    /**
     * timestamp が現在時刻から ±[toleranceSeconds] を超えてずれているか（仕様 §3.3-7）。
     * stale は破棄ではなく表示時の警告対象。
     */
    fun isStale(nowEpochSeconds: Long, toleranceSeconds: Long = STALE_TOLERANCE_SECONDS): Boolean =
        abs(timestamp - nowEpochSeconds) > toleranceSeconds

    companion object {
        const val VERSION: Int = 0x01
        const val SIZE: Int = 20

        const val FLAG_VALID_FIX: Int = 0x01
        const val FLAG_ALTITUDE_VALID: Int = 0x02
        const val FLAG_SPEED_VALID: Int = 0x04
        const val FLAG_SIMULATED: Int = 0x08
        const val FLAG_CHARGING: Int = 0x10
        const val FLAG_LOW_BATTERY: Int = 0x20
        const val FLAG_MOVING: Int = 0x40
        const val FLAG_RESERVED: Int = 0x80

        /** 不明値を表す uint8。 */
        const val UNKNOWN_U8: Int = 0xFF

        /** 座標のスケール（度 × 10^7）。 */
        const val COORDINATE_SCALE: Double = 10_000_000.0

        // 検証範囲は「度 × 10^7」で表す。90° × 10^7 = 900_000_000、
        // 180° × 10^7 = 1_800_000_000。仕様 §3.3-4。
        const val MAX_LATITUDE: Int = 900_000_000
        const val MIN_LATITUDE: Int = -900_000_000
        const val MAX_LONGITUDE: Int = 1_800_000_000
        const val MIN_LONGITUDE: Int = -1_800_000_000

        /** ±0.0001° をスケール済み整数で表したもの。 */
        const val NULL_ISLAND_THRESHOLD: Int = 1_000

        const val MAX_TIMESTAMP: Long = 0xFFFF_FFFFL

        /** 仕様 §3.3-7 の stale 判定許容幅（±24 時間）。 */
        const val STALE_TOLERANCE_SECONDS: Long = 24L * 60L * 60L

        /**
         * 度を仕様の int32（× 10^7）へ変換する。
         *
         * 例: 35.6812360 → 356812360
         */
        fun encodeCoordinate(degrees: Double): Int = (degrees * COORDINATE_SCALE).roundToInt()

        /** 仕様の int32（× 10^7）を度へ変換する。 */
        fun decodeCoordinate(scaled: Int): Double = scaled / COORDINATE_SCALE

        /**
         * 20 bytes をデコードする。仕様 §3.3 の 1〜2（長さ・version）のみ検証し、
         * それ以外の検証は [validationError] で行う。
         *
         * @return 不正な長さ・未知 version の場合は null
         */
        fun decode(bytes: ByteArray): LocationPacket? {
            if (bytes.size != SIZE) return null
            if (Bytes.u8(bytes, 0) != VERSION) return null
            return LocationPacket(
                flags = Bytes.u8(bytes, 1),
                sequence = Bytes.u16Le(bytes, 2),
                latitude = Bytes.i32Le(bytes, 4),
                longitude = Bytes.i32Le(bytes, 8),
                altitudeM = Bytes.i16Le(bytes, 12),
                accuracyM = Bytes.u8(bytes, 14),
                speedMps = Bytes.u8(bytes, 15),
                timestamp = Bytes.u32Le(bytes, 16),
            )
        }
    }
}

/** 仕様 §3.3 の受信側検証で破棄すべき理由。 */
enum class ValidationError {
    /** flags bit7（予約）が 1。 */
    RESERVED_FLAG_SET,

    /** 緯度が ±90° の範囲外。 */
    LATITUDE_OUT_OF_RANGE,

    /** 経度が ±180° の範囲外。 */
    LONGITUDE_OUT_OF_RANGE,

    /** VALID_FIX なのに (0, 0) 近傍（Null Island）。 */
    NULL_ISLAND,
}
