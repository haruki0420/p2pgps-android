package jp.haruki0420.p2pgps.protocol

/**
 * 受信した Location Packet の sequence を監視する。
 *
 * 仕様 §3.3-6: 連続受信の sequence が巻き戻った場合はログに記録するが、
 * パケット自体は受理する（再送・並び替え耐性）。
 */
class SequenceTracker {

    private var previous: Int? = null

    /** 直前に観測した sequence（未観測なら null）。 */
    val lastSequence: Int? get() = previous

    fun reset() {
        previous = null
    }

    /**
     * sequence を 1 つ観測する。
     *
     * @return 巻き戻り・欠番の情報。巻き戻りでもパケットは受理すること。
     */
    fun observe(sequence: Int): SequenceObservation {
        require(sequence in 0..0xFFFF) { "sequence は uint16 の範囲で指定してください: $sequence" }
        val prev = previous
        previous = sequence
        if (prev == null) return SequenceObservation(sequence, rollback = false, gap = 0)
        val expected = (prev + 1) and 0xFFFF
        if (sequence == expected) return SequenceObservation(sequence, rollback = false, gap = 0)
        // 65535 → 0 は正常なラップであり巻き戻りではない。
        val isRollback = sequence < prev && !(prev == 0xFFFF && sequence == 0)
        val gap = if (isRollback) 0 else (sequence - expected) and 0xFFFF
        return SequenceObservation(sequence, rollback = isRollback, gap = gap)
    }
}

/** [SequenceTracker.observe] の結果。 */
data class SequenceObservation(
    val sequence: Int,
    /** sequence が巻き戻ったか（ログ記録のみで受理する）。 */
    val rollback: Boolean,
    /** 欠番の数（0 なら連続）。 */
    val gap: Int,
)
