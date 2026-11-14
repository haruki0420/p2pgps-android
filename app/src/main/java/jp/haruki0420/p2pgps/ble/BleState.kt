package jp.haruki0420.p2pgps.ble

/**
 * BLE ペリフェラルの状態。
 */
data class BleState(
    /** アドバタイズ中か。 */
    val advertising: Boolean = false,
    /** 接続中のセントラル数（v1 は 1 対 1 を想定）。 */
    val connectedCentrals: Int = 0,
    /** Location Packet の CCCD が購読されているか。 */
    val locationSubscribed: Boolean = false,
    /** Status の CCCD が購読されているか。 */
    val statusSubscribed: Boolean = false,
    /** Extended Location の CCCD が購読されているか。 */
    val extendedSubscribed: Boolean = false,
    /** 交渉済みの ATT MTU（既定 23）。 */
    val mtu: Int = DEFAULT_MTU,
    /** 直近のエラー（画面表示用）。 */
    val lastError: String? = null,
) {
    /** いずれかの characteristic を購読しているセントラルがあるか。 */
    val hasSubscriber: Boolean
        get() = locationSubscribed || statusSubscribed || extendedSubscribed

    companion object {
        /** BLE 4.0 の既定 ATT MTU。ペイロードは 20 bytes。 */
        const val DEFAULT_MTU = 23
    }
}
