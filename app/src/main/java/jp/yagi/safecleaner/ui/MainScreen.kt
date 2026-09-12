package jp.yagi.safecleaner.ui

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import jp.yagi.safecleaner.SafeCleanerApp
import jp.yagi.safecleaner.accessibility.AccessibilityStatus
import jp.yagi.safecleaner.model.CleanerMode
import jp.yagi.safecleaner.model.CleanerUiState
import jp.yagi.safecleaner.model.OperationStage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    app: SafeCleanerApp,
    state: CleanerUiState,
    onOpenAccessibility: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenExclusions: () -> Unit,
    onOpenResult: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var accessibilityEnabled by remember { mutableStateOf(AccessibilityStatus.isEnabled(context)) }
    var usageAccessEnabled by remember { mutableStateOf(app.cacheStatsRepository.hasUsageAccess()) }
    var ram by remember { mutableStateOf(app.memoryRepository.snapshot()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = AccessibilityStatus.isEnabled(context)
                usageAccessEnabled = app.cacheStatsRepository.hasUsageAccess()
                ram = app.memoryRepository.snapshot()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("セーフクリーナー") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Memory, null)
                        Text(" メモリ状態", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("使用中 ${formatBytes(ram.usedBytes)} / 合計 ${formatBytes(ram.totalBytes)}")
                    Text("利用可能 ${formatBytes(ram.availableBytes)}", color = MaterialTheme.colorScheme.primary)
                    LinearProgressIndicator(
                        progress = { if (ram.totalBytes == 0L) 0f else ram.usedBytes.toFloat() / ram.totalBytes },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Memory, null)
                        Text(
                            if (usageAccessEnabled) " 高速キャッシュ判定：有効" else " 高速キャッシュ判定：無効",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (usageAccessEnabled) {
                        Text("キャッシュ0 Bのアプリは設定画面を開く前にスキップします。")
                    } else {
                        Text("使用状況へのアクセスを許可すると、不要な設定画面の遷移を減らせます。")
                        FilledTonalButton(onClick = onOpenUsageAccess, Modifier.fillMaxWidth()) {
                            Text("使用状況へのアクセス設定を開く")
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Security, null)
                        Text(
                            if (accessibilityEnabled) " 操作サービス：有効" else " 操作サービス：無効",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (!accessibilityEnabled) {
                        Text("Androidの設定画面でキャッシュ削除と強制停止を自動操作するために必要です。")
                        Button(onClick = onOpenAccessibility, Modifier.fillMaxWidth()) {
                            Text("ユーザー補助設定を開く")
                        }
                    }
                }
            }

            if (state.isRunning) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("処理中 ${state.currentIndex} / ${state.totalCount}", style = MaterialTheme.typography.titleMedium)
                        Text(state.currentApp?.label ?: "準備中")
                        Text(stageLabel(state.stage), color = MaterialTheme.colorScheme.primary)
                        LinearProgressIndicator(
                            progress = { if (state.totalCount == 0) 0f else state.currentIndex.toFloat() / state.totalCount },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = app.cleanerController::cancel) { Text("処理を中止") }
                    }
                }
            } else {
                Button(
                    onClick = { requestNotificationIfNeeded(context, onRequestNotifications); app.cleanerController.start(CleanerMode.CLEAN_ALL) },
                    enabled = accessibilityEnabled,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                ) {
                    Icon(Icons.Outlined.CleaningServices, null)
                    Text("　一括クリーン")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = { requestNotificationIfNeeded(context, onRequestNotifications); app.cleanerController.start(CleanerMode.FORCE_STOP_ONLY) },
                        enabled = accessibilityEnabled,
                        modifier = Modifier.weight(1f).height(54.dp),
                    ) { Icon(Icons.Outlined.AutoDelete, null); Text("　一括終了") }
                    FilledTonalButton(
                        onClick = { requestNotificationIfNeeded(context, onRequestNotifications); app.cleanerController.start(CleanerMode.CLEAR_CACHE_ONLY) },
                        enabled = accessibilityEnabled,
                        modifier = Modifier.weight(1f).height(54.dp),
                    ) { Icon(Icons.Outlined.DeleteSweep, null); Text("　キャッシュ") }
                }
            }

            TextButton(onClick = onOpenExclusions, Modifier.fillMaxWidth()) {
                Text("除外アプリ設定", Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
            }
            if (state.lastSummary != null) {
                TextButton(onClick = onOpenResult, Modifier.fillMaxWidth()) {
                    Text("前回の処理結果", Modifier.weight(1f))
                    Text("成功 ${state.lastSummary.successCount}件")
                }
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(8.dp))
            Text("安全に特定できない操作はスキップします。", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun requestNotificationIfNeeded(context: android.content.Context, request: () -> Unit) {
    if (Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) request()
}

private fun stageLabel(stage: OperationStage?): String = when (stage) {
    OperationStage.PREPARING -> "対象を確認しています"
    OperationStage.OPENING_APP_INFO -> "アプリ情報を開いています"
    OperationStage.OPENING_STORAGE -> "ストレージ画面を開いています"
    OperationStage.CLEARING_CACHE -> "キャッシュを削除しています"
    OperationStage.RETURNING_TO_APP_INFO -> "アプリ情報へ戻っています"
    OperationStage.FORCE_STOPPING -> "強制停止しています"
    OperationStage.CONFIRMING_FORCE_STOP -> "強制停止を確認しています"
    OperationStage.COMPLETE -> "完了"
    null -> ""
}
