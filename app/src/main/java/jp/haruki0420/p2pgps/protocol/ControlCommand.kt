package jp.haruki0420.p2pgps.protocol

/**
 * Control characteristic のコマンド（受信側 → 送信側）。仕様 §5。
 *
 * 1 コマンド = 1 書き込み。応答は Status notify で返す。
 *
 * ```
 * 0x01 START         uint16 LE interval_ms（0 = 既定値 1000）
 * 0x02 STOP          なし
 * 0x03 SET_INTERVAL  uint16 LE interval_ms
 * 0x04 REQUEST_FIX   なし
 * 0x05 SET_EXTENDED  uint8 enabled (0/1)
 * 0x7F RESET         なし
 * ```
 */
sealed class ControlCommand {

    /** 書き込みバイト列（1〜3 bytes）へエンコードする。 */
    abstract fun encode(): ByteArray

    /** ストリーミング開始。intervalMs は [MIN_INTERVAL_MS]〜[MAX_INTERVAL_MS]。 */
    data class Start(val intervalMs: Int) : ControlCommand() {
        init {
            require(intervalMs in ControlCommand.MIN_INTERVAL_MS..ControlCommand.MAX_INTERVAL_MS) {
                "interval_ms は ${ControlCommand.MIN_INTERVAL_MS}〜${ControlCommand.MAX_INTERVAL_MS} の範囲で指定してください: $intervalMs"
            }
        }

        override fun encode(): ByteArray {
            val out = ByteArray(3)
            Bytes.putU8(out, 0, ControlCommand.OPCODE_START)
            Bytes.putU16Le(out, 1, intervalMs)
            return out
        }
    }

    /** ストリーミング停止（アドバタイズは継続）。 */
    data object Stop : ControlCommand() {
        override fun encode(): ByteArray = byteArrayOf(ControlCommand.OPCODE_STOP.toByte())
    }

    /** 送信間隔の変更（ストリーミング中でも可）。 */
    data class SetInterval(val intervalMs: Int) : ControlCommand() {
        init {
            require(intervalMs in ControlCommand.MIN_INTERVAL_MS..ControlCommand.MAX_INTERVAL_MS) {
                "interval_ms は ${ControlCommand.MIN_INTERVAL_MS}〜${ControlCommand.MAX_INTERVAL_MS} の範囲で指定してください: $intervalMs"
            }
        }

        override fun encode(): ByteArray {
            val out = ByteArray(3)
            Bytes.putU8(out, 0, ControlCommand.OPCODE_SET_INTERVAL)
            Bytes.putU16Le(out, 1, intervalMs)
            return out
        }
    }

    /** 次回 notify を即時送信（1 回）。 */
    data object RequestFix : ControlCommand() {
        override fun encode(): ByteArray = byteArrayOf(ControlCommand.OPCODE_REQUEST_FIX.toByte())
    }

    /** Extended Location の notify 有効化。 */
    data class SetExtended(val enabled: Boolean) : ControlCommand() {
        override fun encode(): ByteArray =
            byteArrayOf(ControlCommand.OPCODE_SET_EXTENDED.toByte(), if (enabled) 1 else 0)
    }

    /** 既定設定へ戻す（interval=1000, extended=off, sequence=0）。 */
    data object Reset : ControlCommand() {
        override fun encode(): ByteArray = byteArrayOf(ControlCommand.OPCODE_RESET.toByte())
    }

    companion object {
        const val OPCODE_START: Int = 0x01
        const val OPCODE_STOP: Int = 0x02
        const val OPCODE_SET_INTERVAL: Int = 0x03
        const val OPCODE_REQUEST_FIX: Int = 0x04
        const val OPCODE_SET_EXTENDED: Int = 0x05
        const val OPCODE_RESET: Int = 0x7F

        /** v2 で AUTH 用に予約されている opcode（仕様 §8）。v1 では未知として扱う。 */
        const val OPCODE_AUTH_RESERVED: Int = 0x10

        const val DEFAULT_INTERVAL_MS: Int = 1_000
        const val MIN_INTERVAL_MS: Int = 100
        const val MAX_INTERVAL_MS: Int = 60_000

        /**
         * 受信した interval_ms を有効範囲へ補正する。
         *
         * - 0 は既定値 [DEFAULT_INTERVAL_MS]
         * - 100 未満は [MIN_INTERVAL_MS]
         * - 60000 超は [MAX_INTERVAL_MS]
         */
        fun clampInterval(intervalMs: Int): Int = when {
            intervalMs == 0 -> DEFAULT_INTERVAL_MS
            intervalMs < MIN_INTERVAL_MS -> MIN_INTERVAL_MS
            intervalMs > MAX_INTERVAL_MS -> MAX_INTERVAL_MS
            else -> intervalMs
        }

        /**
         * Control characteristic の書き込み値（1〜3 bytes）をデコードする。
         *
         * - 未知 opcode → [ControlDecodeResult.Failure]（[StatusError.UNKNOWN_OPCODE]）
         * - 不正長 → [ControlDecodeResult.Failure]（[StatusError.BAD_LENGTH]）
         * - START / SET_INTERVAL の interval_ms は仕様 §5.1 に従い [clampInterval] で正規化する。
         *
         * 注意: Android 実装は **decode 時に正規化を適用した値**（[Start.intervalMs] /
         * [SetInterval.intervalMs]）を返す。Python / iOS は生値を保持し、適用時に正規化する。
         * 生値（受信側が書き込んだ uint16 LE）が必要な場合は書き込みバイト列の offset 1 を参照すること。
         */
        fun decode(bytes: ByteArray): ControlDecodeResult {
            if (bytes.isEmpty()) return ControlDecodeResult.Failure(StatusError.BAD_LENGTH)
            val opcode = bytes[0].toInt() and 0xFF
            return when (opcode) {
                OPCODE_START -> decodeIntervalCommand(bytes) { Start(it) }
                OPCODE_STOP -> if (bytes.size == 1) {
                    ControlDecodeResult.Success(Stop)
                } else {
                    ControlDecodeResult.Failure(StatusError.BAD_LENGTH)
                }

                OPCODE_SET_INTERVAL -> decodeIntervalCommand(bytes) { SetInterval(it) }
                OPCODE_REQUEST_FIX -> if (bytes.size == 1) {
                    ControlDecodeResult.Success(RequestFix)
                } else {
                    ControlDecodeResult.Failure(StatusError.BAD_LENGTH)
                }

                OPCODE_SET_EXTENDED -> if (bytes.size == 2) {
                    ControlDecodeResult.Success(SetExtended(bytes[1].toInt() != 0))
                } else {
                    ControlDecodeResult.Failure(StatusError.BAD_LENGTH)
                }

                OPCODE_RESET -> if (bytes.size == 1) {
                    ControlDecodeResult.Success(Reset)
                } else {
                    ControlDecodeResult.Failure(StatusError.BAD_LENGTH)
                }

                else -> ControlDecodeResult.Failure(StatusError.UNKNOWN_OPCODE)
            }
        }

        private fun decodeIntervalCommand(
            bytes: ByteArray,
            factory: (Int) -> ControlCommand,
        ): ControlDecodeResult {
            if (bytes.size != 3) return ControlDecodeResult.Failure(StatusError.BAD_LENGTH)
            val raw = Bytes.u16Le(bytes, 1)
            return ControlDecodeResult.Success(factory(clampInterval(raw)))
        }
    }
}

/** Control characteristic のデコード結果。 */
sealed class ControlDecodeResult {
    /** 正常にデコードできたコマンド。 */
    data class Success(val command: ControlCommand) : ControlDecodeResult()

    /** 破棄すべき書き込み。error を Status の last_error に設定する。 */
    data class Failure(val error: StatusError) : ControlDecodeResult()
}
