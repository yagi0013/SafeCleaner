package jp.yagi.safecleaner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.yagi.safecleaner.model.CleanerStatus
import jp.yagi.safecleaner.ui.ExclusionScreen
import jp.yagi.safecleaner.ui.MainScreen
import jp.yagi.safecleaner.ui.ResultScreen
import jp.yagi.safecleaner.ui.SafeCleanerTheme

class MainActivity : ComponentActivity() {
    private var showResultRequest by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showResultRequest = intent.getBooleanExtra(EXTRA_SHOW_RESULT, false)
        setContent {
            SafeCleanerTheme {
                val app = application as SafeCleanerApp
                val cleanerState by app.cleanerController.state.collectAsStateWithLifecycle()
                var screen by remember { mutableStateOf(if (showResultRequest) Screen.RESULT else Screen.MAIN) }
                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }

                LaunchedEffect(showResultRequest, cleanerState.status) {
                    if (showResultRequest || cleanerState.status == CleanerStatus.COMPLETED) {
                        if (cleanerState.lastSummary != null) screen = Screen.RESULT
                        showResultRequest = false
                    }
                }

                when (screen) {
                    Screen.MAIN -> MainScreen(
                        app = app,
                        state = cleanerState,
                        onOpenAccessibility = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onOpenUsageAccess = {
                            val usageIntent = Intent(
                                Settings.ACTION_USAGE_ACCESS_SETTINGS,
                                Uri.parse("package:$packageName"),
                            )
                            runCatching { startActivity(usageIntent) }.onFailure {
                                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                        },
                        onOpenExclusions = { screen = Screen.EXCLUSIONS },
                        onOpenResult = { screen = Screen.RESULT },
                        onRequestNotifications = {
                            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    )
                    Screen.EXCLUSIONS -> ExclusionScreen(app = app, onBack = { screen = Screen.MAIN })
                    Screen.RESULT -> ResultScreen(
                        summary = cleanerState.lastSummary,
                        appRepository = app.appRepository,
                        onBack = { screen = Screen.MAIN },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as SafeCleanerApp).cleanerController.refreshAccessibilityState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SHOW_RESULT, false)) showResultRequest = true
    }

    private enum class Screen { MAIN, EXCLUSIONS, RESULT }

    companion object {
        const val EXTRA_SHOW_RESULT = "show_result"
        const val EXTRA_ACCESSIBILITY_REQUIRED = "accessibility_required"
    }
}
