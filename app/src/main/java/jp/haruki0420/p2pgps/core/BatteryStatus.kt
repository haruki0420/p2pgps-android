package jp.haruki0420.p2pgps.core

import android.content.Context
import android.os.BatteryManager

/** バッテリー残量と充電状態。 */
data class BatteryStatus(
    val percent: Int,
    val charging: Boolean,
) {
    val isUnknown: Boolean get() = percent !in 0..100

    /** 仕様 §3.1 bit5: バッテリー残量 20% 未満。 */
    val isLow: Boolean get() = percent in 0..19

    companion object {
        /** 不明を表す値（仕様 §6 の battery_pct = 255）。 */
        const val UNKNOWN_PERCENT = 255

        /** 現在のバッテリー状態を読む（BatteryManager の同期 API のみ使用）。 */
        fun read(context: Context): BatteryStatus {
            val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                ?: return BatteryStatus(UNKNOWN_PERCENT, charging = false)
            val percent = try {
                manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } catch (e: Exception) {
                -1
            }
            val charging = try {
                manager.isCharging
            } catch (e: Exception) {
                false
            }
            return BatteryStatus(
                percent = if (percent in 0..100) percent else UNKNOWN_PERCENT,
                charging = charging,
            )
        }
    }
}
