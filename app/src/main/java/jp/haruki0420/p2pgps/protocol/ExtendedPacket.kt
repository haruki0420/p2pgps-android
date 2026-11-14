package jp.haruki0420.p2pgps.protocol

import kotlin.math.roundToInt

/**
 * Extended Location Packet（8 bytes 固定、任意）。仕様 §4。
 *
 * 送信側が対応している場合のみ notify する。受信側は無くても動作する。
 *
 * ```
 * offset 0 version           uint8   0x01 固定
 * offset 1 provider          uint8   0=unknown, 1=GPS, 2=Network, 3=Fused, 4=Manual/Simulated
 * offset 2 battery_pct       uint8   0-100、255 = 不明
 * offset 3 satellites        uint8   衛星数、255 = 不明
 * offset 4 course            uint16 LE 進行方向 × 100（0-35999）、65535 = 不明
 * offset 6 vertical_accuracy uint8   垂直精度 m、255 = 不明
 * offset 7 reserved          uint8   0
 * ```
 */
data class ExtendedPacket(
    val provider: Int = ProviderType.UNKNOWN.code,
    val batteryPct: Int = UNKNOWN_U8,
    val satellites: Int = UNKNOWN_U8,
    val course: Int = UNKNOWN_COURSE,
    val verticalAccuracyM: Int = UNKNOWN_U8,
) {

    init {
        require(provider in 0..0xFF) { "provider は uint8 の範囲で指定してください: $provider" }
        require(batteryPct in 0..0xFF) { "battery_pct は uint8 の範囲で指定してください: $batteryPct" }
        require(satellites in 0..0xFF) { "satellites は uint8 の範囲で指定してください: $satellites" }
        require(course in 0..0xFFFF) { "course は uint16 の範囲で指定してください: $course" }
        require(verticalAccuracyM in 0..0xFF) {
            "vertical_accuracy_m は uint8 の範囲で指定してください: $verticalAccuracyM"
        }
    }

    /** 仕様 §4 の 8 bytes へエンコードする。 */
    fun encode(): ByteArray {
        val out = ByteArray(SIZE)
        Bytes.putU8(out, 0, VERSION)
        Bytes.putU8(out, 1, provider)
        Bytes.putU8(out, 2, batteryPct)
        Bytes.putU8(out, 3, satellites)
        Bytes.putU16Le(out, 4, course)
        Bytes.putU8(out, 6, verticalAccuracyM)
        Bytes.putU8(out, 7, 0)
        return out
    }

    companion object {
        const val VERSION: Int = 0x01
        const val SIZE: Int = 8

        /** 不明値を表す uint8。 */
        const val UNKNOWN_U8: Int = 0xFF

        /** 進行方向が不明であることを表す uint16。 */
        const val UNKNOWN_COURSE: Int = 0xFFFF

        /** 進行方向のスケール（度 × 100）。 */
        const val COURSE_SCALE: Double = 100.0

        /** 進行方向の最大値（359.99°）。 */
        const val MAX_COURSE: Int = 35_999

        /** 度を仕様の uint16（× 100、0〜35999）へ変換する。 */
        fun encodeCourse(degrees: Double): Int {
            if (!degrees.isFinite()) return UNKNOWN_COURSE
            val normalized = ((degrees % 360.0) + 360.0) % 360.0
            // 浮動小数の誤差（123.45 × 100 = 12344.999...）を丸めてから範囲へ収める。
            return (normalized * COURSE_SCALE).roundToInt().coerceIn(0, MAX_COURSE)
        }

        /**
         * 8 bytes をデコードする。長さと version のみ検証する。
         * reserved は将来の拡張のため読み捨てる。
         *
         * @return 不正な長さ・未知 version の場合は null
         */
        fun decode(bytes: ByteArray): ExtendedPacket? {
            if (bytes.size != SIZE) return null
            if (Bytes.u8(bytes, 0) != VERSION) return null
            return ExtendedPacket(
                provider = Bytes.u8(bytes, 1),
                batteryPct = Bytes.u8(bytes, 2),
                satellites = Bytes.u8(bytes, 3),
                course = Bytes.u16Le(bytes, 4),
                verticalAccuracyM = Bytes.u8(bytes, 6),
            )
        }
    }
}

/** Extended Packet の provider 値。仕様 §4。 */
enum class ProviderType(val code: Int) {
    UNKNOWN(0),
    GPS(1),
    NETWORK(2),
    FUSED(3),
    MANUAL(4),
    ;

    companion object {
        /** 未定義の値は UNKNOWN として扱う（前方互換）。 */
        fun fromCode(code: Int): ProviderType = entries.firstOrNull { it.code == code } ?: UNKNOWN

        /** Android の Location.getProvider() の文字列から変換する。 */
        fun fromProviderName(name: String?): ProviderType = when (name?.lowercase()) {
            "gps" -> GPS
            "network" -> NETWORK
            "fused" -> FUSED
            "simulated", "manual", "mock" -> MANUAL
            else -> UNKNOWN
        }
    }
}
