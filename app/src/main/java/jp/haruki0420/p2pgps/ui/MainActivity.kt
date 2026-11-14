package jp.haruki0420.p2pgps.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import jp.haruki0420.p2pgps.core.AppLog
import jp.haruki0420.p2pgps.core.SenderStateHolder
import jp.haruki0420.p2pgps.service.NotificationHelper
import jp.haruki0420.p2pgps.service.SenderController
import jp.haruki0420.p2pgps.ui.theme.P2PGpsTheme

/**
 * 送信側のメイン画面。
 *
 * 権限の要求は Activity 側で行い、Compose 画面には状態とコールバックだけを渡す。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannel(this)
        setContent {
            P2PGpsTheme {
                val state by SenderStateHolder.state.collectAsState()
                val logs by AppLog.entries.collectAsState()

                var permissionRefresh by remember { mutableIntStateOf(0) }
                var pendingStart by remember { mutableStateOf(false) }
                val permissionStatus = remember(permissionRefresh) { Permissions.status(this) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    // 結果の Map ではなく現在の付与状態を読み直す（一部許可のケースを扱うため）。
                    permissionRefresh += 1
                    if (pendingStart && Permissions.status(this).canStart) {
                        SenderController.start(this)
                    }
                    pendingStart = false
                }

                SenderScreen(
                    state = state,
                    logs = logs,
                    permissions = permissionStatus,
                    onToggleSending = { enabled ->
                        if (enabled) {
                            if (Permissions.status(this).canStart) {
                                SenderController.start(this)
                            } else {
                                pendingStart = true
                                permissionLauncher.launch(Permissions.required())
                            }
                        } else {
                            SenderController.stop(this)
                        }
                    },
                    onRequestPermissions = {
                        permissionLauncher.launch(Permissions.required())
                    },
                    onIntervalChange = { intervalMs ->
                        SenderController.setInterval(this, intervalMs)
                    },
                    onSimulate = { latitude, longitude ->
                        SenderController.simulate(this, latitude, longitude)
                    },
                    onClearSimulation = {
                        SenderController.clearSimulation(this)
                    },
                    onClearLog = {
                        AppLog.clear()
                    },
                )
            }
        }
    }
}
