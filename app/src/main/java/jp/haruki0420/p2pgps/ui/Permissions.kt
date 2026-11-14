package jp.haruki0420.p2pgps.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** 画面表示用の権限状態。 */
data class PermissionStatus(
    val fineLocation: Boolean,
    val coarseLocation: Boolean,
    val bluetoothAdvertise: Boolean,
    val bluetoothConnect: Boolean,
    val bluetoothScan: Boolean,
    val notifications: Boolean,
) {
    /** FINE または COARSE のどちらかがあれば位置情報を取得できる。 */
    val locationGranted: Boolean get() = fineLocation || coarseLocation

    /** Android 12 以降は BLUETOOTH_ADVERTISE / CONNECT が実行時権限。 */
    val bluetoothGranted: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothAdvertise && bluetoothConnect
        } else {
            true
        }

    /** Android 13 以降は POST_NOTIFICATIONS が実行時権限。 */
    val notificationsGranted: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifications
        } else {
            true
        }

    /** 送信開始に必須の権限が揃っているか（通知は必須にしない）。 */
    val canStart: Boolean get() = locationGranted && bluetoothGranted

    val allGranted: Boolean get() = canStart && notificationsGranted
}

/** 権限の要求・確認。Android 12 / 13 の分岐をここへ集約する。 */
object Permissions {

    /** 実行時要求する権限（OS バージョンで変わる）。 */
    fun required(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    // BLUETOOTH_* / POST_NOTIFICATIONS は API 31/33 で追加された定数だが、
    // 値は文字列としてインライン化されるため古い OS で参照しても問題ない。
    @SuppressLint("InlinedApi")
    fun status(context: Context): PermissionStatus = PermissionStatus(
        fineLocation = granted(context, Manifest.permission.ACCESS_FINE_LOCATION),
        coarseLocation = granted(context, Manifest.permission.ACCESS_COARSE_LOCATION),
        bluetoothAdvertise = granted(context, Manifest.permission.BLUETOOTH_ADVERTISE),
        bluetoothConnect = granted(context, Manifest.permission.BLUETOOTH_CONNECT),
        bluetoothScan = granted(context, Manifest.permission.BLUETOOTH_SCAN),
        notifications = granted(context, Manifest.permission.POST_NOTIFICATIONS),
    )

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
