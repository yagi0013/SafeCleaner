package jp.yagi.safecleaner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.yagi.safecleaner.data.AppRepository
import jp.yagi.safecleaner.model.RunSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(summary: RunSummary?, appRepository: AppRepository, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("処理結果") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "戻る") } },
        )
    }) { padding ->
        if (summary == null) {
            Text("処理結果はまだありません。", Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        val issues = summary.results.filter { !it.succeeded || it.failureReason != null }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("完了", style = MaterialTheme.typography.headlineSmall)
                        ResultRow("対象アプリ", summary.targetCount)
                        ResultRow("処理成功", summary.successCount)
                        ResultRow("強制停止成功", summary.forceStopSuccessCount)
                        ResultRow("キャッシュ削除成功", summary.cacheSuccessCount)
                        ResultRow("自動除外", summary.automaticExcludedCount)
                        ResultRow("ユーザー除外", summary.userExcludedCount)
                        ResultRow("スキップ", summary.skippedCount)
                        ResultRow("失敗", summary.failedCount)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("利用可能RAMの変化（参考値）", style = MaterialTheme.typography.titleMedium)
                        Text("${formatBytes(summary.availableRamBefore)} → ${formatBytes(summary.availableRamAfter)}")
                        Text("Androidのメモリ管理により常に変動します。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (issues.isNotEmpty()) item { Text("スキップ・失敗の詳細", style = MaterialTheme.typography.titleMedium) }
            items(issues, key = { it.packageName }) { result ->
                val app = appRepository.getApp(result.packageName)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (app != null) AppIcon(app.icon, result.label) else Icon(Icons.Outlined.ErrorOutline, null)
                    Column(Modifier.weight(1f)) {
                        Text(result.label)
                        Text(result.failureReason ?: "スキップ", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text("$value 件", color = MaterialTheme.colorScheme.primary)
    }
}
