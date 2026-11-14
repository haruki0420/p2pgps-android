package jp.haruki0420.p2pgps.protocol

import java.util.UUID

/**
 * 仕様 §1 の GATT UUID 定数。
 *
 * すべて 128-bit UUID。BLE のバイトオーダー（リトルエンディアン）は
 * Android の Bluetooth API が吸収するため、ここでは文字列表現のみを保持する。
 */
object GattUuids {

    /** P2P GPS Service。 */
    val SERVICE: UUID = UUID.fromString("B1E5A000-9C3D-4E7A-8F20-1A2B3C4D5E6F")

    /** Location Packet characteristic（read / notify、20 bytes 固定）。 */
    val LOCATION: UUID = UUID.fromString("B1E5A001-9C3D-4E7A-8F20-1A2B3C4D5E6F")

    /** Control characteristic（write / write-without-response、1〜3 bytes）。 */
    val CONTROL: UUID = UUID.fromString("B1E5A002-9C3D-4E7A-8F20-1A2B3C4D5E6F")

    /** Status characteristic（read / notify、8 bytes 固定）。 */
    val STATUS: UUID = UUID.fromString("B1E5A003-9C3D-4E7A-8F20-1A2B3C4D5E6F")

    /** Extended Location characteristic（read / notify、8 bytes 固定）。 */
    val EXTENDED: UUID = UUID.fromString("B1E5A004-9C3D-4E7A-8F20-1A2B3C4D5E6F")

    /** Client Characteristic Configuration Descriptor (0x2902)。 */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    /** Device Information Service（標準・任意）。 */
    val DEVICE_INFORMATION_SERVICE: UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")

    /** Manufacturer Name String。 */
    val MANUFACTURER_NAME: UUID = UUID.fromString("00002A29-0000-1000-8000-00805F9B34FB")

    /** Model Number String。 */
    val MODEL_NUMBER: UUID = UUID.fromString("00002A24-0000-1000-8000-00805F9B34FB")

    /** Firmware Revision String。 */
    val FIRMWARE_REVISION: UUID = UUID.fromString("00002A26-0000-1000-8000-00805F9B34FB")

    /** ローカルネームの接頭辞（仕様 §1.2）。 */
    const val LOCAL_NAME_PREFIX = "P2PGPS-"

    /** Android 送信側のプラットフォーム表記。 */
    const val LOCAL_NAME_PLATFORM = "ANDROID"
}
