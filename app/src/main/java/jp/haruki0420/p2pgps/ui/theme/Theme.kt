package jp.haruki0420.p2pgps.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * 視覚世界「測量標（benchmark）」のトークン。
 *
 * 正本はリポジトリ直下の `DESIGN.md`（プロジェクト共通）。Android では
 * MaterialTheme の colorScheme にマップしつつ、この [P2PColors] を
 * CompositionLocal で提供する。影・グラデーション・カードの山は使わない。
 */
@Immutable
data class P2PColors(
    /** 画面背景。 */
    val paper: Color,
    /** リードアウトプレート・シート。 */
    val surface: Color,
    /** 本文・座標。 */
    val ink: Color,
    /** ラベル・補助。 */
    val inkSecondary: Color,
    /** ヘアライン・区切り。 */
    val line: Color,
    /** 主アクション・接続済み。 */
    val pine: Color,
    /** ライブ状態のテキスト。 */
    val live: Color,
    /** ライブの塗り（白文字と組み合わせる）。 */
    val liveFill: Color,
    /** 警告・stale。 */
    val amber: Color,
    /** エラー。 */
    val danger: Color,
    /** Material の surfaceVariant 用に paper と line の中間として導出した色。 */
    val mist: Color,
)

private val LightP2PColors = P2PColors(
    paper = Color(0xFFF2F4F2),
    surface = Color(0xFFFBFCFB),
    ink = Color(0xFF17252A),
    inkSecondary = Color(0xFF55666A),
    line = Color(0xFFC9D3CE),
    pine = Color(0xFF1E6B5A),
    live = Color(0xFFA8502E),
    liveFill = Color(0xFFB85C38),
    amber = Color(0xFF8A5A12),
    danger = Color(0xFFA32E24),
    mist = Color(0xFFE7ECE8),
)

private val DarkP2PColors = P2PColors(
    paper = Color(0xFF0F1A1E),
    surface = Color(0xFF16252A),
    ink = Color(0xFFE8EEEC),
    inkSecondary = Color(0xFF9FB0B2),
    line = Color(0xFF2A3B40),
    pine = Color(0xFF6FBFA8),
    live = Color(0xFFE08A5F),
    liveFill = Color(0xFFC96A42),
    amber = Color(0xFFE0B25C),
    danger = Color(0xFFE0705F),
    mist = Color(0xFF22333A),
)

private val LocalP2PColors = staticCompositionLocalOf { LightP2PColors }

private val LightColorScheme = lightColorScheme(
    primary = LightP2PColors.pine,
    onPrimary = Color.White,
    secondary = LightP2PColors.inkSecondary,
    onSecondary = LightP2PColors.surface,
    background = LightP2PColors.paper,
    onBackground = LightP2PColors.ink,
    surface = LightP2PColors.surface,
    onSurface = LightP2PColors.ink,
    surfaceVariant = LightP2PColors.mist,
    onSurfaceVariant = LightP2PColors.inkSecondary,
    outline = LightP2PColors.line,
    outlineVariant = LightP2PColors.line,
    error = LightP2PColors.danger,
    onError = Color.White,
    tertiary = LightP2PColors.amber,
    onTertiary = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkP2PColors.pine,
    onPrimary = DarkP2PColors.paper,
    secondary = DarkP2PColors.inkSecondary,
    onSecondary = DarkP2PColors.surface,
    background = DarkP2PColors.paper,
    onBackground = DarkP2PColors.ink,
    surface = DarkP2PColors.surface,
    onSurface = DarkP2PColors.ink,
    surfaceVariant = DarkP2PColors.mist,
    onSurfaceVariant = DarkP2PColors.inkSecondary,
    outline = DarkP2PColors.line,
    outlineVariant = DarkP2PColors.line,
    error = DarkP2PColors.danger,
    onError = DarkP2PColors.paper,
    tertiary = DarkP2PColors.amber,
    onTertiary = DarkP2PColors.paper,
)

/** トークンへ `P2PTheme.colors` でアクセスするための入り口。 */
object P2PTheme {
    val colors: P2PColors
        @Composable
        @ReadOnlyComposable
        get() = LocalP2PColors.current
}

/**
 * タイポグラフィ。
 *
 * 座標・数値は Roboto Mono（[FontFamily.Monospace]）で tabular figures。
 * カスタムフォントは使わない。
 */
object P2PType {
    /** 座標（画面で最も大きい数字）。 */
    val coordinate: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = "tnum",
        )

    /** 補助数値（標高・精度・速度・送信間隔）。 */
    val numeric: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontFeatureSettings = "tnum",
        )

    /** hex / ログ。 */
    val log: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
}

@Composable
fun P2PGpsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalP2PColors provides if (darkTheme) DarkP2PColors else LightP2PColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            content = content,
        )
    }
}
