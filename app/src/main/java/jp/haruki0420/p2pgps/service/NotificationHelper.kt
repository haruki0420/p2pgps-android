package jp.haruki0420.p2pgps.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import jp.haruki0420.p2pgps.R
import jp.haruki0420.p2pgps.core.SenderState
import jp.haruki0420.p2pgps.protocol.StatusError
import jp.haruki0420.p2pgps.ui.MainActivity
import java.util.Locale

/**
 * 前景サービスの常駐通知。
 *
 * 通知には現在座標・送信レート・接続状態と「停止」ボタンを表示する（architecture §3）。
 * 座標はロック画面に表示しないよう VISIBILITY_PRIVATE にしている（仕様 §8）。
 */
object NotificationHelper {

    const val CHANNEL_ID = "p2pgps_sender"
    const val NOTIFICATION_ID = 1001

    /** 通知チャンネルを作成する（既にあれば何もしない）。 */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** 現在の状態から常駐通知を組み立てる。 */
    @Suppress("DEPRECATION")
    fun build(context: Context, state: SenderState): Notification {
        val stopIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, GpsSenderService::class.java).setAction(GpsSenderService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = buildBody(state)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setContentTitle(buildTitle(state))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent)
            .addAction(0, context.getString(R.string.notification_stop), stopIntent)
            .build()
    }

    private fun buildTitle(state: SenderState): String = when {
        state.lastError == StatusError.LOCATION_UNAVAILABLE -> "P2P GPS: 測位待ち"
        state.effectiveStreaming -> "P2P GPS: 送信中"
        state.streaming -> "P2P GPS: 受信側の接続待ち"
        else -> "P2P GPS: 停止中"
    }

    private fun buildBody(state: SenderState): String {
        val fix = state.lastFix
        val location = if (fix != null) {
            String.format(
                Locale.US,
                "%.5f, %.5f（±%.0fm）",
                fix.latitude,
                fix.longitude,
                fix.accuracyMeters,
            )
        } else {
            "位置情報を待っています"
        }
        val connection = when {
            state.ble.connectedCentrals == 0 -> "未接続"
            state.ble.locationSubscribed -> "接続中"
            else -> "接続中・受信待ち"
        }
        val interval = if (state.intervalMs % 1000 == 0) {
            "${state.intervalMs / 1000} 秒ごと"
        } else {
            String.format(Locale.US, "%.1f 秒ごと", state.intervalMs / 1000.0)
        }
        return "$location / $interval / $connection"
    }
}
