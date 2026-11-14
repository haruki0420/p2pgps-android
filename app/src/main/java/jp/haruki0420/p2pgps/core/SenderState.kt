package jp.haruki0420.p2pgps.core

import jp.haruki0420.p2pgps.ble.BleState
import jp.haruki0420.p2pgps.location.GpsFix
import jp.haruki0420.p2pgps.protocol.ControlCommand
import jp.haruki0420.p2pgps.protocol.StatusError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 画面表示用の送信状態。
 *
 * サービス（前景サービス）が更新し、Compose 画面が購読する。
 */
data class SenderState(
    val serviceRunning: Boolean = false,
    val streaming: Boolean = false,
    val ble: BleState = BleState(),
    /** アドバタイズ中のローカルネーム（例: `P2PGPS-ANDROID-A1B2`）。 */
    val localName: String = "",
    val intervalMs: Int = ControlCommand.DEFAULT_INTERVAL_MS,
    val extendedEnabled: Boolean = false,
    val sequence: Int = 0,
    val simulated: Boolean = false,
    val fixAvailable: Boolean = false,
    val lastFix: GpsFix? = null,
    val lastSentAtMillis: Long = 0L,
    val sentPackets: Long = 0L,
    val batteryPct: Int = BatteryStatus.UNKNOWN_PERCENT,
    val charging: Boolean = false,
    val lowBattery: Boolean = false,
    val lastError: StatusError = StatusError.NONE,
    val lastErrorMessage: String? = null,
) {
    /** 受信側へ実際に届く状態（購読者がいなければ Idle）。 */
    val effectiveStreaming: Boolean get() = streaming && ble.locationSubscribed
}

/**
 * アプリ全体で共有する送信状態のホルダー。
 *
 * 小規模アプリのため DI は使わず、サービスと画面が同じ StateFlow を購読する。
 */
object SenderStateHolder {

    private val _state = MutableStateFlow(SenderState())

    val state: StateFlow<SenderState> = _state.asStateFlow()

    fun update(transform: (SenderState) -> SenderState) {
        _state.update(transform)
    }

    fun reset() {
        _state.value = SenderState()
    }
}
