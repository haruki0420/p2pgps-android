package jp.haruki0420.p2pgps.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import jp.haruki0420.p2pgps.ble.GattServerManager
import jp.haruki0420.p2pgps.core.AppLog
import jp.haruki0420.p2pgps.core.BatteryStatus
import jp.haruki0420.p2pgps.core.SenderState
import jp.haruki0420.p2pgps.core.SenderStateHolder
import jp.haruki0420.p2pgps.location.FilterResult
import jp.haruki0420.p2pgps.location.GpsFix
import jp.haruki0420.p2pgps.location.LocationFilter
import jp.haruki0420.p2pgps.location.LocationProvider
import jp.haruki0420.p2pgps.protocol.ControlCommand
import jp.haruki0420.p2pgps.protocol.ExtendedPacket
import jp.haruki0420.p2pgps.protocol.LocationPacket
import jp.haruki0420.p2pgps.protocol.ProviderType
import jp.haruki0420.p2pgps.protocol.StatusError
import jp.haruki0420.p2pgps.protocol.StatusPacket
import jp.haruki0420.p2pgps.protocol.StatusState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 位置情報を BLE で送信する前景サービス。
 *
 * - BLE ペリフェラル（[GattServerManager]）を保持し、画面 OFF でもアドバタイズを継続する
 * - [LocationManager][android.location.LocationManager] 由来の位置を [LocationFilter] で整形し、
 *   [LocationPacket] として 1 Hz（可変）で notify する
 * - Control characteristic の START / STOP / SET_INTERVAL / REQUEST_FIX / SET_EXTENDED / RESET に従う
 * - 購読者がいない間は Status を Idle として通知する（仕様 §7）
 *
 * 前景サービスタイプは connectedDevice（BLE 接続維持）と location（位置取得）を併用する。
 */
class GpsSenderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var gattServer: GattServerManager
    private lateinit var locationProvider: LocationProvider

    private val locationFilter = LocationFilter()

    private var isForeground = false
    private var streaming = false
    private var intervalMs = ControlCommand.DEFAULT_INTERVAL_MS
    private var extendedEnabled = false
    private var sequence = 0
    private var simulatedFix: GpsFix? = null
    private var latestFix: GpsFix? = null
    private var lastSentAtMillis = 0L
    private var sentPackets = 0L
    private var lastError = StatusError.NONE
    private var lastErrorMessage: String? = null
    private var lastNotifiedStatus: ByteArray? = null
    private var lastNotificationAtMillis = 0L

    private var streamJob: Job? = null
    private var locationJob: Job? = null
    private var heartbeatJob: Job? = null
    private var stateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        gattServer = GattServerManager(
            context = this,
            onControlCommand = ::handleControlCommand,
            onControlError = ::handleControlError,
        )
        locationProvider = LocationProvider(this)

        stateJob = scope.launch {
            gattServer.state.collect { ble ->
                SenderStateHolder.update { it.copy(ble = ble) }
                notifyStatus()
                publishState(forceNotification = true)
            }
        }
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(STATUS_HEARTBEAT_MS)
                if (isForeground) notifyStatus(force = true)
            }
        }
        AppLog.i(TAG, "送信サービスを作成しました")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> {
                AppLog.i(TAG, "停止要求を受け取りました")
                stopSender()
                stopSelf()
            }

            ACTION_SET_INTERVAL -> {
                val requested = intent.getIntExtra(EXTRA_INTERVAL_MS, intervalMs)
                intervalMs = ControlCommand.clampInterval(requested)
                AppLog.i(TAG, "送信間隔を変更しました: ${intervalMs}ms")
                notifyStatus(force = true)
                publishState(forceNotification = true)
            }

            ACTION_SIMULATE -> {
                val latitude = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN)
                val longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN)
                if (latitude.isFinite() && longitude.isFinite() &&
                    latitude in -90.0..90.0 && longitude in -180.0..180.0
                ) {
                    simulatedFix = GpsFix(
                        latitude = latitude,
                        longitude = longitude,
                        accuracyMeters = 1f,
                        provider = "simulated",
                        timestampMillis = System.currentTimeMillis(),
                        simulated = true,
                    )
                    AppLog.i(TAG, "シミュレート位置を設定しました")
                    if (streaming) sendLocationPacket()
                    publishState(forceNotification = true)
                } else {
                    AppLog.w(TAG, "シミュレート位置が不正です")
                    lastErrorMessage =
                        "シミュレート位置が不正です。緯度は -90〜90、経度は -180〜180 の数値で入力してください。"
                    publishState(forceNotification = true)
                }
            }

            ACTION_CLEAR_SIMULATION -> {
                simulatedFix = null
                AppLog.i(TAG, "シミュレート位置を解除しました")
                publishState(forceNotification = true)
            }

            else -> startSender()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AppLog.i(TAG, "送信サービスを終了します")
        streaming = false
        streamJob?.cancel()
        locationJob?.cancel()
        heartbeatJob?.cancel()
        stateJob?.cancel()
        if (this::gattServer.isInitialized) {
            gattServer.close()
        }
        scope.cancel()
        isForeground = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        SenderStateHolder.reset()
        super.onDestroy()
    }

    private fun enterForeground(): Boolean {
        if (isForeground) return true
        return try {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_ID,
                NotificationHelper.build(
                    this,
                    SenderStateHolder.state.value.copy(serviceRunning = true),
                ),
                foregroundServiceType(),
            )
            isForeground = true
            SenderStateHolder.update { it.copy(serviceRunning = true) }
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "前景サービスを開始できませんでした: ${e.message}")
            lastErrorMessage =
                "前景サービスを開始できませんでした。位置情報と Bluetooth の権限を確認してください。"
            false
        }
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }

    private fun startSender() {
        AppLog.i(TAG, "送信を開始します")
        if (!locationProvider.hasPermission()) {
            lastErrorMessage =
                "位置情報の権限がありません。権限を許可してから、もう一度送信を開始してください。"
            publishState(forceNotification = true)
            return
        }
        if (!gattServer.start()) {
            lastErrorMessage = gattServer.state.value.lastError
            publishState(forceNotification = true)
            return
        }
        startLocationUpdates()
        streaming = true
        ensureStreamLoop()
        notifyStatus(force = true)
        publishState(forceNotification = true)
    }

    private fun stopSender() {
        streaming = false
        streamJob?.cancel()
        streamJob = null
        locationJob?.cancel()
        locationJob = null
        if (this::gattServer.isInitialized) {
            gattServer.stopAdvertising()
        }
        publishState(forceNotification = true)
    }

    private fun startLocationUpdates() {
        if (locationJob?.isActive == true) return
        latestFix = locationProvider.lastKnownFix()?.let { fix ->
            when (val result = locationFilter.accept(fix)) {
                is FilterResult.Accepted -> result.fix
                is FilterResult.Rejected -> null
            }
        }
        locationJob = scope.launch {
            locationProvider.fixes()
                .catch { e -> AppLog.e(TAG, "位置情報の取得に失敗しました: ${e.message}") }
                .collect { fix ->
                    when (val result = locationFilter.accept(fix)) {
                        is FilterResult.Accepted -> {
                            latestFix = result.fix
                            publishState()
                        }

                        is FilterResult.Rejected -> AppLog.d(TAG, "位置を破棄しました: ${result.reason}")
                    }
                }
        }
    }

    private fun ensureStreamLoop() {
        if (streamJob?.isActive == true) return
        streamJob = scope.launch {
            while (isActive && streaming) {
                val waitMs = intervalMs
                    .coerceIn(ControlCommand.MIN_INTERVAL_MS, ControlCommand.MAX_INTERVAL_MS)
                    .toLong()
                delay(waitMs)
                if (!streaming) break
                if (!gattServer.state.value.locationSubscribed) {
                    // 購読者がいない間は送信せず Idle を維持する（仕様 §7）。
                    publishState()
                    continue
                }
                sendLocationPacket()
            }
        }
    }

    private fun sendLocationPacket() {
        val fix = simulatedFix ?: latestFix
        if (fix == null) {
            setError(
                StatusError.LOCATION_UNAVAILABLE,
                "まだ測位できていません。屋外で待つか、デバッグのシミュレート位置を使用してください。",
            )
            return
        }
        val battery = BatteryStatus.read(this)
        val packet = buildLocationPacket(fix, battery)
        gattServer.notifyLocation(packet)
        if (extendedEnabled) {
            gattServer.notifyExtended(buildExtendedPacket(fix, battery))
        }
        sequence = (sequence + 1) and 0xFFFF
        sentPackets += 1
        lastSentAtMillis = System.currentTimeMillis()
        if (lastError == StatusError.LOCATION_UNAVAILABLE) {
            lastError = StatusError.NONE
            lastErrorMessage = null
        }
        publishState()
        notifyStatus()
    }

    private fun buildLocationPacket(fix: GpsFix, battery: BatteryStatus): LocationPacket {
        var flags = LocationPacket.FLAG_VALID_FIX
        val altitude = fix.altitudeMeters
            ?.roundToInt()
            ?.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        if (altitude != null) {
            flags = flags or LocationPacket.FLAG_ALTITUDE_VALID
        }
        val speed = fix.speedMps
            ?.takeIf { it.isFinite() && it >= 0f }
            ?.roundToInt()
            ?.coerceIn(0, LocationPacket.UNKNOWN_U8)
        if (speed != null) {
            flags = flags or LocationPacket.FLAG_SPEED_VALID
        }
        if (fix.simulated) {
            flags = flags or LocationPacket.FLAG_SIMULATED
        }
        if (battery.charging) {
            flags = flags or LocationPacket.FLAG_CHARGING
        }
        if (battery.isLow) {
            flags = flags or LocationPacket.FLAG_LOW_BATTERY
        }
        if (fix.isMoving) {
            flags = flags or LocationPacket.FLAG_MOVING
        }
        val accuracy = if (fix.accuracyMeters.isFinite() && fix.accuracyMeters >= 0f) {
            fix.accuracyMeters.roundToInt().coerceIn(0, LocationPacket.UNKNOWN_U8)
        } else {
            LocationPacket.UNKNOWN_U8
        }
        return LocationPacket(
            flags = flags,
            sequence = sequence,
            latitude = LocationPacket.encodeCoordinate(fix.latitude.coerceIn(-90.0, 90.0)),
            longitude = LocationPacket.encodeCoordinate(fix.longitude.coerceIn(-180.0, 180.0)),
            altitudeM = altitude ?: 0,
            accuracyM = accuracy,
            speedMps = speed ?: LocationPacket.UNKNOWN_U8,
            timestamp = (fix.timestampMillis / 1_000L).coerceIn(0L, LocationPacket.MAX_TIMESTAMP),
        )
    }

    private fun buildExtendedPacket(fix: GpsFix, battery: BatteryStatus): ExtendedPacket {
        val provider = if (fix.simulated) {
            ProviderType.MANUAL
        } else {
            ProviderType.fromProviderName(fix.provider)
        }
        val course = fix.bearingDegrees
            ?.takeIf { it.isFinite() }
            ?.let { ExtendedPacket.encodeCourse(it.toDouble()) }
            ?: ExtendedPacket.UNKNOWN_COURSE
        val verticalAccuracy = fix.verticalAccuracyMeters
            ?.takeIf { it.isFinite() && it >= 0f }
            ?.roundToInt()
            ?.takeIf { it in 0..254 }
            ?: ExtendedPacket.UNKNOWN_U8
        return ExtendedPacket(
            provider = provider.code,
            batteryPct = if (battery.isUnknown) ExtendedPacket.UNKNOWN_U8 else battery.percent,
            satellites = ExtendedPacket.UNKNOWN_U8,
            course = course,
            verticalAccuracyM = verticalAccuracy,
        )
    }

    private fun buildStatusPacket(battery: BatteryStatus): StatusPacket {
        var statusFlags = 0
        if (extendedEnabled) {
            statusFlags = statusFlags or StatusPacket.FLAG_EXTENDED_ENABLED
        }
        if ((simulatedFix ?: latestFix) != null) {
            statusFlags = statusFlags or StatusPacket.FLAG_FIX_AVAILABLE
        }
        if (battery.charging) {
            statusFlags = statusFlags or StatusPacket.FLAG_CHARGING
        }
        if (battery.isLow) {
            statusFlags = statusFlags or StatusPacket.FLAG_LOW_BATTERY
        }
        val state = when {
            lastError == StatusError.LOCATION_UNAVAILABLE ||
                lastError == StatusError.INTERNAL -> StatusState.ERROR

            streaming && gattServer.state.value.locationSubscribed -> StatusState.STREAMING
            else -> StatusState.IDLE
        }
        return StatusPacket(
            state = state,
            intervalMs = intervalMs.coerceIn(0, 0xFFFF),
            batteryPct = if (battery.isUnknown) StatusPacket.UNKNOWN_U8 else battery.percent,
            statusFlags = statusFlags,
            lastError = lastError,
        )
    }

    private fun notifyStatus(force: Boolean = false) {
        if (!this::gattServer.isInitialized) return
        val packet = buildStatusPacket(BatteryStatus.read(this))
        val bytes = packet.encode()
        if (!force && lastNotifiedStatus?.contentEquals(bytes) == true) return
        lastNotifiedStatus = bytes
        gattServer.notifyStatus(packet)
    }

    private fun publishState(forceNotification: Boolean = false) {
        if (!this::gattServer.isInitialized) return
        val battery = BatteryStatus.read(this)
        val fix = simulatedFix ?: latestFix
        val state = SenderState(
            serviceRunning = isForeground,
            streaming = streaming,
            ble = gattServer.state.value,
            localName = gattServer.localName(),
            intervalMs = intervalMs,
            extendedEnabled = extendedEnabled,
            sequence = sequence,
            simulated = simulatedFix != null,
            fixAvailable = fix != null,
            lastFix = fix,
            lastSentAtMillis = lastSentAtMillis,
            sentPackets = sentPackets,
            batteryPct = battery.percent,
            charging = battery.charging,
            lowBattery = battery.isLow,
            lastError = lastError,
            lastErrorMessage = lastErrorMessage,
        )
        SenderStateHolder.update { state }
        updateNotification(state, force = forceNotification)
    }

    private fun updateNotification(state: SenderState, force: Boolean = false) {
        if (!isForeground) return
        // 位置 fix ごと（1 Hz）の通知更新はバッテリーを消費するため 2 秒に 1 回へ間引く。
        // 状態変化（START/STOP/エラー/接続変化）は force で即時反映する。
        val now = System.currentTimeMillis()
        if (!force && now - lastNotificationAtMillis < NOTIFICATION_UPDATE_INTERVAL_MS) return
        lastNotificationAtMillis = now
        try {
            NotificationManagerCompat.from(this).notify(
                NotificationHelper.NOTIFICATION_ID,
                NotificationHelper.build(this, state),
            )
        } catch (e: SecurityException) {
            AppLog.w(TAG, "通知を更新できませんでした: ${e.message}")
        }
    }

    /** 成功した Control コマンドで last_error をクリアする（仕様 §6）。 */
    private fun clearError() {
        lastError = StatusError.NONE
        lastErrorMessage = null
    }

    private fun setError(error: StatusError, message: String) {
        if (lastError == error) return
        lastError = error
        lastErrorMessage = message
        AppLog.w(TAG, message)
        notifyStatus(force = true)
        publishState(forceNotification = true)
    }

    private fun handleControlCommand(command: ControlCommand) {
        scope.launch { applyControlCommand(command) }
    }

    private fun applyControlCommand(command: ControlCommand) {
        when (command) {
            is ControlCommand.Start -> {
                intervalMs = command.intervalMs
                streaming = true
                clearError()
                ensureStreamLoop()
                AppLog.i(TAG, "START: interval=${intervalMs}ms")
                notifyStatus(force = true)
                publishState(forceNotification = true)
            }

            is ControlCommand.Stop -> {
                streaming = false
                clearError()
                AppLog.i(TAG, "STOP")
                notifyStatus(force = true)
                publishState(forceNotification = true)
            }

            is ControlCommand.SetInterval -> {
                intervalMs = command.intervalMs
                clearError()
                AppLog.i(TAG, "SET_INTERVAL: ${intervalMs}ms")
                notifyStatus(force = true)
                publishState(forceNotification = true)
            }

            is ControlCommand.RequestFix -> {
                clearError()
                AppLog.i(TAG, "REQUEST_FIX")
                sendLocationPacket()
            }

            is ControlCommand.SetExtended -> {
                extendedEnabled = command.enabled
                clearError()
                AppLog.i(TAG, "SET_EXTENDED: ${command.enabled}")
                if (command.enabled) {
                    val fix = simulatedFix ?: latestFix
                    if (fix != null) {
                        gattServer.notifyExtended(
                            buildExtendedPacket(fix, BatteryStatus.read(this)),
                        )
                    }
                }
                notifyStatus(force = true)
                publishState(forceNotification = true)
            }

            is ControlCommand.Reset -> {
                intervalMs = ControlCommand.DEFAULT_INTERVAL_MS
                extendedEnabled = false
                sequence = 0
                clearError()
                AppLog.i(TAG, "RESET")
                notifyStatus(force = true)
                publishState(forceNotification = true)
            }
        }
    }

    private fun handleControlError(error: StatusError) {
        scope.launch {
            lastError = error
            lastErrorMessage = null
            AppLog.w(TAG, "Control コマンドのエラー: $error")
            notifyStatus(force = true)
            publishState(forceNotification = true)
        }
    }

    companion object {
        private const val TAG = "GpsSenderService"
        private const val STATUS_HEARTBEAT_MS = 10_000L

        /** 位置 fix ごとの通知更新を間引く間隔。 */
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 2_000L

        const val ACTION_START = "jp.haruki0420.p2pgps.action.START"
        const val ACTION_STOP = "jp.haruki0420.p2pgps.action.STOP"
        const val ACTION_SET_INTERVAL = "jp.haruki0420.p2pgps.action.SET_INTERVAL"
        const val ACTION_SIMULATE = "jp.haruki0420.p2pgps.action.SIMULATE"
        const val ACTION_CLEAR_SIMULATION = "jp.haruki0420.p2pgps.action.CLEAR_SIMULATION"

        const val EXTRA_INTERVAL_MS = "jp.haruki0420.p2pgps.extra.INTERVAL_MS"
        const val EXTRA_LATITUDE = "jp.haruki0420.p2pgps.extra.LATITUDE"
        const val EXTRA_LONGITUDE = "jp.haruki0420.p2pgps.extra.LONGITUDE"
    }
}
