package jp.haruki0420.p2pgps.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import jp.haruki0420.p2pgps.BuildConfig
import jp.haruki0420.p2pgps.core.AppLog
import jp.haruki0420.p2pgps.protocol.ControlCommand
import jp.haruki0420.p2pgps.protocol.ControlDecodeResult
import jp.haruki0420.p2pgps.protocol.ExtendedPacket
import jp.haruki0420.p2pgps.protocol.GattUuids
import jp.haruki0420.p2pgps.protocol.LocationPacket
import jp.haruki0420.p2pgps.protocol.StatusError
import jp.haruki0420.p2pgps.protocol.StatusPacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlin.random.Random

/**
 * BLE ペリフェラル（送信側）の実装。
 *
 * - [BluetoothLeAdvertiser] で Service UUID とローカルネームをアドバタイズする（仕様 §1.2）
 * - [BluetoothGattServer] で Location / Control / Status / Extended を提供する（仕様 §1.1）
 * - CCCD の購読状態を管理し、購読中のセントラルにのみ notify する
 *
 * BLE は実機なしでは検証できないため、このクラスはプロトコルの encode/decode を
 * [jp.haruki0420.p2pgps.protocol] に委譲し、状態管理だけを担当する。
 *
 * コールバックは Binder スレッドから呼ばれる。UI 状態は StateFlow 経由で公開する。
 */
@SuppressLint("MissingPermission")
class GattServerManager(
    private val context: Context,
    /** Control characteristic へ書き込まれたコマンド。 */
    private val onControlCommand: (ControlCommand) -> Unit,
    /** デコードできなかった Control 書き込み（Status の last_error に反映する）。 */
    private val onControlError: (StatusError) -> Unit,
) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _state = MutableStateFlow(BleState())

    val state: StateFlow<BleState> = _state.asStateFlow()

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var previousAdapterName: String? = null
    private var advertisedName: String = ""

    private var locationCharacteristic: BluetoothGattCharacteristic? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private var extendedCharacteristic: BluetoothGattCharacteristic? = null

    private val connectedDevices = linkedSetOf<BluetoothDevice>()
    private val locationSubscribers = linkedSetOf<BluetoothDevice>()
    private val statusSubscribers = linkedSetOf<BluetoothDevice>()
    private val extendedSubscribers = linkedSetOf<BluetoothDevice>()

    /** Device Information Service など静的な read 値。 */
    private val staticValues = mutableMapOf<UUID, ByteArray>()

    private var lastLocationBytes: ByteArray = LocationPacket().encode()
    private var lastStatusBytes: ByteArray = StatusPacket().encode()
    private var lastExtendedBytes: ByteArray = ExtendedPacket().encode()

    /** アドバタイズ中のローカルネーム（例: `P2PGPS-ANDROID-A1B2`）。 */
    fun localName(): String = advertisedName

    /** この端末が BLE に対応しているか。 */
    fun isBluetoothSupported(): Boolean = adapter != null

    /** Bluetooth が ON か（権限がない場合は false）。 */
    fun isBluetoothEnabled(): Boolean = try {
        adapter?.isEnabled == true
    } catch (e: SecurityException) {
        false
    }

    /** Android 12 以降の実行時 Bluetooth 権限を持っているか。 */
    fun hasPermissions(): Boolean = requiredPermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * GATT サーバーを開いてサービスを登録し、アドバタイズを開始する。
     *
     * @return 開始できた場合 true（Bluetooth OFF・権限不足などは false）
     */
    fun start(): Boolean {
        if (!hasPermissions()) {
            reportError("Bluetooth の権限がありません。Android の設定で権限を許可してから再試行してください。")
            return false
        }
        val manager = bluetoothManager
        if (manager == null || adapter == null) {
            reportError("この端末は Bluetooth LE に対応していません。BLE 対応の端末で実行してください。")
            return false
        }
        if (!isBluetoothEnabled()) {
            reportError("Bluetooth が OFF です。端末の設定で Bluetooth を ON にしてから再試行してください。")
            return false
        }
        if (gattServer == null) {
            val server = try {
                manager.openGattServer(context, serverCallback)
            } catch (e: SecurityException) {
                reportError("GATT サーバーを開けませんでした。Bluetooth を OFF/ON してから再試行してください。")
                return false
            }
            if (server == null) {
                reportError("GATT サーバーを開けませんでした。Bluetooth を OFF/ON してから再試行してください。")
                return false
            }
            if (!addServices(server)) {
                try {
                    server.close()
                } catch (e: SecurityException) {
                    // 解放できなくても致命的ではない。
                }
                reportError("GATT サービスを登録できませんでした。アプリを再起動してから再試行してください。")
                return false
            }
            gattServer = server
            AppLog.i(TAG, "GATT サーバーを開始しました")
        }
        startAdvertising()
        return true
    }

    /** アドバタイズを停止し、端末名を元に戻す。 */
    fun stopAdvertising() {
        val currentAdvertiser = advertiser
        val callback = advertiseCallback
        if (currentAdvertiser != null && callback != null) {
            try {
                currentAdvertiser.stopAdvertising(callback)
            } catch (e: SecurityException) {
                AppLog.w(TAG, "アドバタイズを停止できませんでした: ${e.message}")
            }
        }
        advertiser = null
        advertiseCallback = null
        restoreAdapterName()
        _state.update { it.copy(advertising = false) }
    }

    /** GATT サーバーとアドバタイズを完全に終了する。 */
    fun close() {
        stopAdvertising()
        val server = gattServer
        gattServer = null
        if (server != null) {
            try {
                server.clearServices()
            } catch (e: SecurityException) {
                // 無視する。
            }
            try {
                server.close()
            } catch (e: SecurityException) {
                // 無視する。
            }
        }
        connectedDevices.clear()
        locationSubscribers.clear()
        statusSubscribers.clear()
        extendedSubscribers.clear()
        _state.value = BleState()
    }

    /** Location Packet を保持し、購読中のセントラルへ notify する。 */
    fun notifyLocation(packet: LocationPacket) {
        lastLocationBytes = packet.encode()
        notifySubscribers(locationCharacteristic, locationSubscribers, lastLocationBytes)
    }

    /** Status を保持し、購読中のセントラルへ notify する。 */
    fun notifyStatus(packet: StatusPacket) {
        lastStatusBytes = packet.encode()
        notifySubscribers(statusCharacteristic, statusSubscribers, lastStatusBytes)
    }

    /** Extended Location を保持し、購読中のセントラルへ notify する。 */
    fun notifyExtended(packet: ExtendedPacket) {
        lastExtendedBytes = packet.encode()
        notifySubscribers(extendedCharacteristic, extendedSubscribers, lastExtendedBytes)
    }

    private fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun addServices(server: BluetoothGattServer): Boolean {
        val service = BluetoothGattService(
            GattUuids.SERVICE,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )

        val location = BluetoothGattCharacteristic(
            GattUuids.LOCATION,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        location.addDescriptor(cccdDescriptor())

        val control = BluetoothGattCharacteristic(
            GattUuids.CONTROL,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )

        val status = BluetoothGattCharacteristic(
            GattUuids.STATUS,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        status.addDescriptor(cccdDescriptor())

        val extended = BluetoothGattCharacteristic(
            GattUuids.EXTENDED,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        extended.addDescriptor(cccdDescriptor())

        service.addCharacteristic(location)
        service.addCharacteristic(control)
        service.addCharacteristic(status)
        service.addCharacteristic(extended)

        // Device Information Service（仕様 §1 の任意項目）。
        val deviceInformation = BluetoothGattService(
            GattUuids.DEVICE_INFORMATION_SERVICE,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )
        deviceInformation.addCharacteristic(
            staticStringCharacteristic(GattUuids.MANUFACTURER_NAME, "P2PGPS"),
        )
        deviceInformation.addCharacteristic(
            staticStringCharacteristic(GattUuids.MODEL_NUMBER, "Android"),
        )
        deviceInformation.addCharacteristic(
            staticStringCharacteristic(GattUuids.FIRMWARE_REVISION, BuildConfig.VERSION_NAME),
        )

        val added = server.addService(service)
        if (added) {
            server.addService(deviceInformation)
        }
        if (added) {
            locationCharacteristic = location
            statusCharacteristic = status
            extendedCharacteristic = extended
        }
        return added
    }

    private fun cccdDescriptor(): BluetoothGattDescriptor = BluetoothGattDescriptor(
        GattUuids.CCCD,
        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
    )

    private fun staticStringCharacteristic(
        uuid: UUID,
        value: String,
    ): BluetoothGattCharacteristic {
        staticValues[uuid] = value.toByteArray(Charsets.UTF_8)
        return BluetoothGattCharacteristic(
            uuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
    }

    private fun startAdvertising() {
        val currentAdapter = adapter ?: return
        if (_state.value.advertising) return
        val currentAdvertiser = try {
            currentAdapter.bluetoothLeAdvertiser
        } catch (e: SecurityException) {
            null
        }
        if (currentAdvertiser == null) {
            reportError("この端末は BLE アドバタイズに対応していません。BLE 対応の端末で実行してください。")
            return
        }
        advertiser = currentAdvertiser

        // 仕様 §1.2 のローカルネームを載せるため、端末名を一時的に変更する。
        // Android にはアドバタイズデータへ任意のローカルネームを入れる API が無く、
        // setIncludeDeviceName(true) は BluetoothAdapter の名前を参照するため。
        advertisedName = buildLocalName()
        previousAdapterName = try {
            currentAdapter.name
        } catch (e: SecurityException) {
            null
        }
        try {
            currentAdapter.setName(advertisedName)
        } catch (e: SecurityException) {
            AppLog.w(TAG, "端末名を変更できませんでした: ${e.message}")
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        // メインのアドバタイズには Service UUID のみを載せる（31 bytes 制限のため）。
        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(GattUuids.SERVICE))
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        // ローカルネームは Scan Response に載せる（仕様 §1.2）。
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                _state.update { it.copy(advertising = true, lastError = null) }
                AppLog.i(TAG, "アドバタイズを開始しました: $advertisedName")
            }

            override fun onStartFailure(errorCode: Int) {
                _state.update {
                    it.copy(
                        advertising = false,
                        lastError = "アドバタイズを開始できませんでした (code=$errorCode)",
                    )
                }
                AppLog.e(TAG, "アドバタイズの開始に失敗しました: code=$errorCode")
            }
        }
        advertiseCallback = callback
        try {
            currentAdvertiser.startAdvertising(settings, advertiseData, scanResponse, callback)
        } catch (e: SecurityException) {
            reportError("アドバタイズを開始できませんでした。Bluetooth を OFF/ON してから再試行してください。")
        }
    }

    private fun restoreAdapterName() {
        val currentAdapter = adapter ?: return
        val previousName = previousAdapterName ?: return
        try {
            currentAdapter.setName(previousName)
        } catch (e: SecurityException) {
            AppLog.w(TAG, "端末名を元に戻せませんでした: ${e.message}")
        }
        previousAdapterName = null
    }

    private fun buildLocalName(): String {
        val suffix = Random.nextInt(0x10000).toString(16).uppercase().padStart(4, '0')
        return "${GattUuids.LOCAL_NAME_PREFIX}${GattUuids.LOCAL_NAME_PLATFORM}-$suffix"
    }

    @Suppress("DEPRECATION")
    private fun notifySubscribers(
        characteristic: BluetoothGattCharacteristic?,
        devices: Set<BluetoothDevice>,
        value: ByteArray,
    ) {
        val server = gattServer ?: return
        if (characteristic == null || devices.isEmpty()) return
        for (device in devices.toList()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    server.notifyCharacteristicChanged(device, characteristic, false, value)
                } else {
                    characteristic.value = value
                    server.notifyCharacteristicChanged(device, characteristic, false)
                }
            } catch (e: SecurityException) {
                AppLog.w(TAG, "notify に失敗しました: ${e.message}")
            }
        }
    }

    private fun valueFor(uuid: UUID): ByteArray? = when (uuid) {
        GattUuids.LOCATION -> lastLocationBytes
        GattUuids.STATUS -> lastStatusBytes
        GattUuids.EXTENDED -> lastExtendedBytes
        else -> staticValues[uuid]
    }

    private fun sendResponse(
        device: BluetoothDevice,
        requestId: Int,
        status: Int,
        offset: Int,
        value: ByteArray?,
    ) {
        try {
            gattServer?.sendResponse(device, requestId, status, offset, value)
        } catch (e: SecurityException) {
            AppLog.w(TAG, "GATT 応答を送れませんでした: ${e.message}")
        }
    }

    private fun updateConnectionState() {
        _state.update {
            it.copy(
                connectedCentrals = connectedDevices.size,
                locationSubscribed = locationSubscribers.isNotEmpty(),
                statusSubscribed = statusSubscribers.isNotEmpty(),
                extendedSubscribed = extendedSubscribers.isNotEmpty(),
            )
        }
    }

    private fun reportError(message: String) {
        AppLog.e(TAG, message)
        _state.update { it.copy(lastError = message) }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    connectedDevices.add(device)
                    AppLog.i(TAG, "セントラルが接続しました（接続数 ${connectedDevices.size}）")
                }

                BluetoothGatt.STATE_DISCONNECTED -> {
                    connectedDevices.remove(device)
                    locationSubscribers.remove(device)
                    statusSubscribers.remove(device)
                    extendedSubscribers.remove(device)
                    AppLog.i(TAG, "セントラルが切断しました")
                }
            }
            updateConnectionState()
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                AppLog.i(TAG, "GATT サービスを登録しました: ${service.uuid}")
            } else {
                AppLog.e(TAG, "GATT サービスの登録に失敗しました: status=$status")
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (descriptor.uuid != GattUuids.CCCD) {
                if (responseNeeded) {
                    sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                }
                return
            }
            val enabled = value != null && value.isNotEmpty() && (value[0].toInt() and 0x01) != 0
            when (descriptor.characteristic?.uuid) {
                GattUuids.LOCATION ->
                    if (enabled) locationSubscribers.add(device) else locationSubscribers.remove(device)

                GattUuids.STATUS ->
                    if (enabled) statusSubscribers.add(device) else statusSubscribers.remove(device)

                GattUuids.EXTENDED ->
                    if (enabled) extendedSubscribers.add(device) else extendedSubscribers.remove(device)
            }
            if (responseNeeded) {
                sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
            updateConnectionState()
            AppLog.i(TAG, "CCCD 書き込み: ${descriptor.characteristic?.uuid} enabled=$enabled")
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (characteristic.uuid != GattUuids.CONTROL || preparedWrite) {
                if (responseNeeded) {
                    sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                }
                return
            }
            when (val result = ControlCommand.decode(value ?: ByteArray(0))) {
                is ControlDecodeResult.Success -> {
                    AppLog.i(TAG, "Control コマンドを受信しました: ${result.command}")
                    onControlCommand(result.command)
                    if (responseNeeded) {
                        sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                    }
                }

                is ControlDecodeResult.Failure -> {
                    AppLog.w(TAG, "Control コマンドを破棄しました: ${result.error}")
                    onControlError(result.error)
                    if (responseNeeded) {
                        sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                    }
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = valueFor(characteristic.uuid)
            if (value == null) {
                sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                return
            }
            // 20 bytes 固定なので MTU 23（ペイロード 20 bytes）でも分割不要。
            if (offset != 0) {
                sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                return
            }
            sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            _state.update { it.copy(mtu = mtu) }
            AppLog.i(TAG, "MTU を更新しました: $mtu")
        }
    }

    companion object {
        private const val TAG = "GattServer"
    }
}
