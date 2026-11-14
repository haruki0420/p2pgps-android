package jp.haruki0420.p2pgps.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** ログの重要度。 */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/** 画面に表示する 1 件のログ。 */
data class LogEntry(
    val timestampMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
) {
    /** `HH:mm:ss` 形式の時刻。 */
    fun formattedTime(): String = TIME_FORMATTER.format(
        Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()),
    )

    companion object {
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}

/**
 * アプリ内ログ（直近 200 件）と Logcat の両方へ出力する。
 *
 * 座標そのものはプライバシー保護のためここへは流さない（仕様 §8）。
 */
object AppLog {

    private const val MAX_ENTRIES = 200
    private const val LOGCAT_TAG = "P2PGps"

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** 画面表示用のログ（新しいものが末尾）。 */
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun d(tag: String, message: String) {
        Log.d(LOGCAT_TAG, "[$tag] $message")
        add(LogLevel.DEBUG, tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(LOGCAT_TAG, "[$tag] $message")
        add(LogLevel.INFO, tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(LOGCAT_TAG, "[$tag] $message")
        add(LogLevel.WARN, tag, message)
    }

    fun e(tag: String, message: String) {
        Log.e(LOGCAT_TAG, "[$tag] $message")
        add(LogLevel.ERROR, tag, message)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private fun add(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            timestampMillis = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
        )
        _entries.update { current -> (current + entry).takeLast(MAX_ENTRIES) }
    }
}
