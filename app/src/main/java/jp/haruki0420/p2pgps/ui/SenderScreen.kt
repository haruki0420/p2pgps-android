package jp.haruki0420.p2pgps.ui

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import jp.haruki0420.p2pgps.core.LogEntry
import jp.haruki0420.p2pgps.core.LogLevel
import jp.haruki0420.p2pgps.core.SenderState
import jp.haruki0420.p2pgps.ui.theme.P2PTheme
import jp.haruki0420.p2pgps.ui.theme.P2PType
import java.util.Locale
import kotlin.math.roundToInt

/** 送信レートの選択肢（ms）。 */
private val INTERVAL_OPTIONS = listOf(200, 500, 1_000, 2_000, 5_000)

/**
 * 送信側のメイン画面。
 *
 * 視覚世界は「測量標（benchmark）」。座標を主役にしたリードアウトプレート、
 * ヘアラインと余白による構造、pine の主アクション。カードの山・影・グラデーションは使わない。
 * 仕様は `DESIGN.md`（プロジェクト共通）を参照。
 */
@Composable
fun SenderScreen(
    state: SenderState,
    logs: List<LogEntry>,
    permissions: PermissionStatus,
    onToggleSending: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit,
    onIntervalChange: (Int) -> Unit,
    onSimulate: (Double, Double) -> Unit,
    onClearSimulation: () -> Unit,
    onClearLog: () -> Unit,
) {
    val colors = P2PTheme.colors
    val reduceMotion = rememberReduceMotion()

    Scaffold(
        containerColor = colors.paper,
        bottomBar = { PrimaryActionBar(state, onToggleSending, reduceMotion) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            StatusRail(state)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                ReadoutPlate(state, reduceMotion)
                StateSection(state)
                BleSection(state)
                IntervalSection(state, onIntervalChange)
                PermissionSection(permissions, onRequestPermissions)
                DebugSection(state, onSimulate, onClearSimulation)
                LogSection(logs, onClearLog)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Status rail
// ---------------------------------------------------------------------------

@Composable
private fun StatusRail(state: SenderState) {
    val colors = P2PTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LedIndicator(state)
        Spacer(Modifier.width(8.dp))
        Text(
            text = state.localName.ifBlank { "P2P GPS 送信" },
            style = P2PType.numeric,
            color = colors.ink,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "${state.intervalMs} ms",
            style = P2PType.numeric,
            color = colors.inkSecondary,
        )
    }
    HorizontalDivider(color = colors.line, thickness = 1.dp)
}

@Composable
private fun LedIndicator(state: SenderState) {
    val colors = P2PTheme.colors
    val description: String
    val color: Color
    val outlined: Boolean
    when {
        state.ble.lastError != null -> {
            description = "エラー"
            color = colors.danger
            outlined = false
        }

        state.ble.connectedCentrals > 0 -> {
            description = "接続済み"
            color = colors.pine
            outlined = false
        }

        state.ble.advertising -> {
            description = "接続待ち"
            color = colors.amber
            outlined = false
        }

        else -> {
            description = "未接続"
            color = colors.inkSecondary
            outlined = true
        }
    }
    val base = Modifier
        .size(8.dp)
        .semantics { contentDescription = description }
    Box(
        modifier = if (outlined) {
            base.border(1.dp, color, CircleShape)
        } else {
            base.background(color, CircleShape)
        },
    )
}

// ---------------------------------------------------------------------------
// Readout plate（座標が主役）
// ---------------------------------------------------------------------------

@Composable
private fun ReadoutPlate(state: SenderState, reduceMotion: Boolean) {
    val colors = P2PTheme.colors
    val fix = state.lastFix
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoordinateRow(label = "緯度", value = fix?.latitude, reduceMotion = reduceMotion)
        CoordinateRow(label = "経度", value = fix?.longitude, reduceMotion = reduceMotion)
        HorizontalDivider(color = colors.line, thickness = 1.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "精度",
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkSecondary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = fix?.let { "±${it.accuracyMeters.roundToInt()} m" } ?: "—",
                style = P2PType.numeric,
                color = colors.ink,
            )
            Spacer(Modifier.weight(1f))
            FixQualityBars(state)
        }
        val altitude = fix?.altitudeMeters
        val speed = fix?.speedMps
        if (altitude != null || speed != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (altitude != null) {
                    Text(
                        text = "標高 ${altitude.roundToInt()} m",
                        style = P2PType.numeric,
                        color = colors.inkSecondary,
                    )
                }
                if (altitude != null && speed != null) {
                    Spacer(Modifier.width(16.dp))
                }
                if (speed != null) {
                    Text(
                        text = String.format(Locale.US, "速度 %.1f m/s", speed),
                        style = P2PType.numeric,
                        color = colors.inkSecondary,
                    )
                }
            }
        }
        if (state.simulated) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.BugReport,
                    contentDescription = null,
                    tint = colors.live,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "シミュレート位置を送信中",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.live,
                )
            }
        }
    }
    HorizontalDivider(color = colors.line, thickness = 1.dp)
}

@Composable
private fun CoordinateRow(label: String, value: Double?, reduceMotion: Boolean) {
    val colors = P2PTheme.colors
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.inkSecondary,
            modifier = Modifier.width(40.dp),
        )
        CoordinateValue(value = value, reduceMotion = reduceMotion)
    }
}

@Composable
private fun CoordinateValue(value: Double?, reduceMotion: Boolean) {
    val colors = P2PTheme.colors
    val text = value?.let { String.format(Locale.US, "%.5f", it) } ?: "—"
    Crossfade(
        targetState = text,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 120),
    ) { current ->
        Text(
            text = current,
            style = P2PType.coordinate,
            color = colors.ink,
        )
    }
}

@Composable
private fun FixQualityBars(state: SenderState) {
    val colors = P2PTheme.colors
    val accuracy = state.lastFix?.accuracyMeters
    val filled = when {
        accuracy == null -> 0
        accuracy <= 5f -> 4
        accuracy <= 10f -> 3
        accuracy <= 30f -> 2
        else -> 1
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(10.dp)
                        .background(if (index < filled) colors.ink else colors.line),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = when (filled) {
                0 -> "測位なし"
                1 -> "低"
                2 -> "中"
                3 -> "良"
                else -> "高"
            },
            style = MaterialTheme.typography.labelMedium,
            color = colors.inkSecondary,
        )
    }
}

// ---------------------------------------------------------------------------
// State / BLE
// ---------------------------------------------------------------------------

@Composable
private fun StateSection(state: SenderState) {
    val colors = P2PTheme.colors
    val streaming = state.effectiveStreaming
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = if (streaming) colors.pine else colors.inkSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    streaming -> "送信中"
                    state.streaming -> "受信側の接続待ち"
                    else -> "停止中"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (streaming) colors.pine else colors.ink,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${state.intervalMs} ms",
                style = P2PType.numeric,
                color = colors.inkSecondary,
            )
        }
        Text(
            text = when {
                streaming -> "位置を ${state.intervalMs} ms 間隔で送信しています。"
                state.streaming -> "iPad が接続して購読を開始すると送信が始まります。"
                else -> "送信を開始すると、iPad から接続できるようになります。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSecondary,
        )
        val errorMessage = state.lastErrorMessage
        if (errorMessage != null) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.danger,
                )
            }
        }
    }
    HorizontalDivider(color = colors.line, thickness = 1.dp)
}

@Composable
private fun BleSection(state: SenderState) {
    val colors = P2PTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "BLE",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
        )
        BleRow(
            icon = Icons.Outlined.Bluetooth,
            label = "アドバタイズ",
            value = if (state.ble.advertising) "アドバタイズ中" else "停止",
            active = state.ble.advertising,
        )
        BleRow(
            icon = Icons.Outlined.Link,
            label = "接続",
            value = "接続 ${state.ble.connectedCentrals} 台",
            active = state.ble.connectedCentrals > 0,
        )
        BleRow(
            icon = Icons.Outlined.Notifications,
            label = "購読",
            value = "購読 ${if (state.ble.locationSubscribed) 1 else 0} 台",
            active = state.ble.locationSubscribed,
        )
        Text(
            text = "MTU ${state.ble.mtu} bytes",
            style = P2PType.numeric,
            color = colors.inkSecondary,
        )
        val bleError = state.ble.lastError
        if (bleError != null) {
            Text(
                text = bleError,
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
            )
        }
    }
    HorizontalDivider(color = colors.line, thickness = 1.dp)
}

@Composable
private fun BleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    active: Boolean,
) {
    val colors = P2PTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) colors.pine else colors.inkSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkSecondary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
        )
    }
}

// ---------------------------------------------------------------------------
// 送信レート
// ---------------------------------------------------------------------------

@Composable
private fun IntervalSection(state: SenderState, onIntervalChange: (Int) -> Unit) {
    val colors = P2PTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "送信レート（ms）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            INTERVAL_OPTIONS.forEach { option ->
                IntervalOption(
                    label = option.toString(),
                    selected = state.intervalMs == option,
                    enabled = state.serviceRunning,
                    onClick = { onIntervalChange(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = "受信側から START / SET_INTERVAL で変更された場合もここに反映されます。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSecondary,
        )
    }
    HorizontalDivider(color = colors.line, thickness = 1.dp)
}

@Composable
private fun IntervalOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = P2PTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .background(if (selected) colors.pine else colors.surface, shape)
            .border(1.dp, if (selected) colors.pine else colors.line, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = P2PType.numeric,
            color = when {
                selected -> Color.White
                enabled -> colors.ink
                else -> colors.inkSecondary
            },
        )
    }
}

// ---------------------------------------------------------------------------
// 権限
// ---------------------------------------------------------------------------

@Composable
private fun PermissionSection(
    permissions: PermissionStatus,
    onRequestPermissions: () -> Unit,
) {
    val colors = P2PTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "権限",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
        )
        PermissionRow("位置情報（正確）", permissions.fineLocation)
        PermissionRow("位置情報（おおよそ）", permissions.coarseLocation)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionRow("Bluetooth アドバタイズ", permissions.bluetoothAdvertise)
            PermissionRow("Bluetooth 接続", permissions.bluetoothConnect)
        } else {
            Text(
                text = "Android 11 以前の Bluetooth 権限はインストール時に付与されます。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSecondary,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow("通知", permissions.notifications)
        }
        if (!permissions.allGranted) {
            OutlinedButton(
                onClick = onRequestPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("権限を許可する")
            }
        }
    }
    HorizontalDivider(color = colors.line, thickness = 1.dp)
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    val colors = P2PTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
            contentDescription = null,
            tint = if (granted) colors.pine else colors.danger,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkSecondary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (granted) "許可済み" else "未許可",
            style = MaterialTheme.typography.bodyMedium,
            color = if (granted) colors.pine else colors.danger,
        )
    }
}

// ---------------------------------------------------------------------------
// デバッグ（シミュレート位置）
// ---------------------------------------------------------------------------

@Composable
private fun DebugSection(
    state: SenderState,
    onSimulate: (Double, Double) -> Unit,
    onClearSimulation: () -> Unit,
) {
    val colors = P2PTheme.colors
    var latitudeText by remember { mutableStateOf("35.6812360") }
    var longitudeText by remember { mutableStateOf("139.7671250") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "デバッグ",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
        )
        Text(
            text = "実測値の代わりに固定座標を送ります。動作確認用です。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = latitudeText,
                onValueChange = { latitudeText = it },
                label = { Text("緯度") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = longitudeText,
                onValueChange = { longitudeText = it },
                label = { Text("経度") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val latitude = latitudeText.toDoubleOrNull()
                    val longitude = longitudeText.toDoubleOrNull()
                    if (latitude != null && longitude != null) {
                        onSimulate(latitude, longitude)
                    }
                },
                enabled = state.serviceRunning,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("シミュレートを開始")
            }
            OutlinedButton(
                onClick = onClearSimulation,
                enabled = state.serviceRunning && state.simulated,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("解除")
            }
        }
    }
    HorizontalDivider(color = colors.line, thickness = 1.dp)
}

// ---------------------------------------------------------------------------
// ログ
// ---------------------------------------------------------------------------

@Composable
private fun LogSection(logs: List<LogEntry>, onClearLog: () -> Unit) {
    val colors = P2PTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "ログ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClearLog) {
                Text("クリア")
            }
        }
        if (logs.isEmpty()) {
            Text(
                text = "ログはありません",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSecondary,
            )
        } else {
            LazyColumn(modifier = Modifier.height(200.dp)) {
                items(logs) { entry ->
                    LogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val colors = P2PTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = entry.formattedTime(),
            style = P2PType.log,
            color = colors.inkSecondary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = entry.level.label,
            style = P2PType.log,
            color = logColor(entry.level),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = entry.message,
            style = P2PType.log,
            color = colors.ink,
        )
    }
}

@Composable
private fun logColor(level: LogLevel): Color {
    val colors = P2PTheme.colors
    return when (level) {
        LogLevel.DEBUG -> colors.inkSecondary
        LogLevel.INFO -> colors.pine
        LogLevel.WARN -> colors.amber
        LogLevel.ERROR -> colors.danger
    }
}

private val LogLevel.label: String
    get() = when (this) {
        LogLevel.DEBUG -> "debug"
        LogLevel.INFO -> "info"
        LogLevel.WARN -> "warn"
        LogLevel.ERROR -> "error"
    }

// ---------------------------------------------------------------------------
// 主アクション（下部固定）
// ---------------------------------------------------------------------------

@Composable
private fun PrimaryActionBar(
    state: SenderState,
    onToggleSending: (Boolean) -> Unit,
    reduceMotion: Boolean,
) {
    val colors = P2PTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.98f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "primary-press",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.paper)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        HorizontalDivider(color = colors.line, thickness = 1.dp)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onToggleSending(!state.serviceRunning) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.pine,
                contentColor = Color.White,
            ),
            interactionSource = interactionSource,
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            Text(
                text = if (state.serviceRunning) "送信を停止" else "送信を開始",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------

@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        } catch (e: Exception) {
            false
        }
    }
}
