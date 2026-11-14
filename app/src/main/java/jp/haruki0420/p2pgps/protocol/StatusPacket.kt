package jp.haruki0420.p2pgps.protocol

/**
 * Status characteristic（8 bytes 固定）。仕様 §6。
 *
 * ```
 * offset 0 version      uint8   0x01 固定
 * offset 1 state        uint8   0=Idle, 1=Streaming, 2=Paused, 3=Error
 * offset 2 interval_ms  uint16 LE 現在の送信間隔
 * offset 4 battery_pct  uint8   255 = 不明
 * offset 5 status_flags uint8   bit0 extended, bit1 fix, bit2 charging, bit3 low battery
 * offset 6 last_error   uint8   0=none, 1=unknown opcode, 2=bad length, 3=location unavailable, 4=internal
 * offset 7 reserved     uint8   0
 * ```
 */
data class StatusPacket(
    val state: StatusState = StatusState.IDLE,
    val intervalMs: Int = ControlCommand.DEFAULT_INTERVAL_MS,
    val batteryPct: Int = UNKNOWN_U8,
    val statusFlags: Int = 0,
    val lastError: StatusError = StatusError.NONE,
) {

    init {
        require(intervalMs in 0..0xFFFF) { "interval_ms は uint16 の範囲で指定してください: $intervalMs" }
        require(batteryPct in 0..0xFF) { "battery_pct は uint8 の範囲で指定してください: $batteryPct" }
        require(statusFlags in 0..0xFF) { "status_flags は uint8 の範囲で指定してください: $statusFlags" }
    }

    val extendedEnabled: Boolean get() = (statusFlags and FLAG_EXTENDED_ENABLED) != 0
    val fixAvailable: Boolean get() = (statusFlags and FLAG_FIX_AVAILABLE) != 0
    val charging: Boolean get() = (statusFlags and FLAG_CHARGING) != 0
    val lowBattery: Boolean get() = (statusFlags and FLAG_LOW_BATTERY) != 0

    /** 仕様 §6 の 8 bytes へエンコードする。 */
    fun encode(): ByteArray {
        val out = ByteArray(SIZE)
        Bytes.putU8(out, 0, VERSION)
        Bytes.putU8(out, 1, state.code)
        Bytes.putU16Le(out, 2, intervalMs)
        Bytes.putU8(out, 4, batteryPct)
        Bytes.putU8(out, 5, statusFlags)
        Bytes.putU8(out, 6, lastError.code)
        Bytes.putU8(out, 7, 0)
        return out
    }

    companion object {
        const val VERSION: Int = 0x01
        const val SIZE: Int = 8

        const val UNKNOWN_U8: Int = 0xFF

        const val FLAG_EXTENDED_ENABLED: Int = 0x01
        const val FLAG_FIX_AVAILABLE: Int = 0x02
        const val FLAG_CHARGING: Int = 0x04
        const val FLAG_LOW_BATTERY: Int = 0x08

        /**
         * 8 bytes をデコードする。長さと version のみ検証する。
         * 未知の state / last_error は前方互換のため Idle / none として扱う。
         *
         * @return 不正な長さ・未知 version の場合は null
         */
        fun decode(bytes: ByteArray): StatusPacket? {
            if (bytes.size != SIZE) return null
            if (Bytes.u8(bytes, 0) != VERSION) return null
            return StatusPacket(
                state = StatusState.fromCode(Bytes.u8(bytes, 1)) ?: StatusState.IDLE,
                intervalMs = Bytes.u16Le(bytes, 2),
                batteryPct = Bytes.u8(bytes, 4),
                statusFlags = Bytes.u8(bytes, 5),
                lastError = StatusError.fromCode(Bytes.u8(bytes, 6)) ?: StatusError.NONE,
            )
        }
    }
}

/** Status の state 値。仕様 §6。 */
enum class StatusState(val code: Int) {
    IDLE(0),
    STREAMING(1),
    PAUSED(2),
    ERROR(3),
    ;

    companion object {
        fun fromCode(code: Int): StatusState? = entries.firstOrNull { it.code == code }
    }
}

/** Status の last_error 値。仕様 §6。 */
enum class StatusError(val code: Int) {
    NONE(0),
    UNKNOWN_OPCODE(1),
    BAD_LENGTH(2),
    LOCATION_UNAVAILABLE(3),
    INTERNAL(4),
    ;

    companion object {
        fun fromCode(code: Int): StatusError? = entries.firstOrNull { it.code == code }
    }
}
