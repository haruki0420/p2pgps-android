package jp.haruki0420.p2pgps.protocol

/**
 * 仕様 §2 のバイトオーダー（すべてリトルエンディアン）を扱う小さなヘルパー。
 *
 * `protocol` パッケージは Android に依存しない純粋な Kotlin で実装し、
 * JVM ユニットテストで encode/decode を検証できるようにしている。
 */
internal object Bytes {

    fun u8(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xFF

    fun u16Le(bytes: ByteArray, offset: Int): Int =
        u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)

    fun i16Le(bytes: ByteArray, offset: Int): Int = u16Le(bytes, offset).toShort().toInt()

    fun i32Le(bytes: ByteArray, offset: Int): Int =
        u16Le(bytes, offset) or (u16Le(bytes, offset + 2) shl 16)

    fun u32Le(bytes: ByteArray, offset: Int): Long =
        u16Le(bytes, offset).toLong() or (u16Le(bytes, offset + 2).toLong() shl 16)

    fun putU8(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
    }

    fun putU16Le(bytes: ByteArray, offset: Int, value: Int) {
        putU8(bytes, offset, value)
        putU8(bytes, offset + 1, value ushr 8)
    }

    fun putI16Le(bytes: ByteArray, offset: Int, value: Int) = putU16Le(bytes, offset, value)

    fun putI32Le(bytes: ByteArray, offset: Int, value: Int) {
        putU16Le(bytes, offset, value and 0xFFFF)
        putU16Le(bytes, offset + 2, (value ushr 16) and 0xFFFF)
    }

    fun putU32Le(bytes: ByteArray, offset: Int, value: Long) {
        putU16Le(bytes, offset, (value and 0xFFFF).toInt())
        putU16Le(bytes, offset + 2, ((value ushr 16) and 0xFFFF).toInt())
    }
}
