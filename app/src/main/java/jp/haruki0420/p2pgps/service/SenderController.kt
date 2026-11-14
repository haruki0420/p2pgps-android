package jp.haruki0420.p2pgps.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * 画面から前景サービスへ指示を送る小さなファサード。
 *
 * サービスが停止中に SET_INTERVAL / SIMULATE を送るとサービスが起動してしまうため、
 * 画面側ではサービス稼働中のみこれらの操作を有効にしている。
 */
object SenderController {

    /** 送信を開始する（前景サービスを起動）。 */
    fun start(context: Context) {
        ContextCompat.startForegroundService(
            context,
            intent(context, GpsSenderService.ACTION_START),
        )
    }

    /** 送信を停止する（前景サービスを終了）。 */
    fun stop(context: Context) {
        context.startService(intent(context, GpsSenderService.ACTION_STOP))
    }

    /** 送信間隔を変更する（100〜60000ms に補正される）。 */
    fun setInterval(context: Context, intervalMs: Int) {
        context.startService(
            intent(context, GpsSenderService.ACTION_SET_INTERVAL)
                .putExtra(GpsSenderService.EXTRA_INTERVAL_MS, intervalMs),
        )
    }

    /** デバッグ用のシミュレート位置を設定する。 */
    fun simulate(context: Context, latitude: Double, longitude: Double) {
        context.startService(
            intent(context, GpsSenderService.ACTION_SIMULATE)
                .putExtra(GpsSenderService.EXTRA_LATITUDE, latitude)
                .putExtra(GpsSenderService.EXTRA_LONGITUDE, longitude),
        )
    }

    /** シミュレート位置を解除し、実測位置に戻す。 */
    fun clearSimulation(context: Context) {
        context.startService(intent(context, GpsSenderService.ACTION_CLEAR_SIMULATION))
    }

    private fun intent(context: Context, action: String): Intent =
        Intent(context, GpsSenderService::class.java).setAction(action)
}
