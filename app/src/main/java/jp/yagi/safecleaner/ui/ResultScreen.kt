package jp.yagi.safecleaner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.yagi.safecleaner.data.AppRepository
import jp.yagi.safecleaner.model.AppOperationResult
import jp.yagi.safecleaner.model.AppResultCategory
import jp.yagi.safecleaner.model.RunSummary
import jp.yagi.safecleaner.model.StepStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    summary: RunSummary?,
    appRepository: AppRepository,
    onOpenAppInfo: (String) -> Unit,
    onBack: () -> Unit,
) {
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

        val tabs = listOf(
            ResultTab("成功", AppResultCategory.SUCCESS, summary.successCount),
            ResultTab("スキップ", AppResultCategory.SKIPPED, summary.skippedCount),
            ResultTab("失敗", AppResultCategory.FAILED, summary.failedCount),
        )
        var selectedTabIndex by rememberSaveable(summary.id) { mutableStateOf(0) }
        val selectedTab = tabs[selectedTabIndex]
        val displayedResults = summary.results.filter { it.category == selectedTab.category }

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
            item {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text("${tab.label} ${tab.count}") },
                        )
                    }
                }
            }
            if (displayedResults.isEmpty()) {
                item {
                    Text(
                        "${selectedTab.label}したアプリはありません。",
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(displayedResults, key = { it.packageName }) { result ->
                    val app = appRepository.getApp(result.packageName)
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpenAppInfo(result.packageName) },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AppIcon(app?.icon, result.label)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(result.label, style = MaterialTheme.typography.titleMedium)
                                Text(result.detailText(), style = MaterialTheme.typography.bodySmall)
                                Text(result.packageName, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ResultTab(val label: String, val category: AppResultCategory, val count: Int)

private fun AppOperationResult.detailText(): String {
    return buildList {
        if (forceStopStatus != StepStatus.NOT_REQUESTED) add("強制停止：${forceStopStatus.label}")
        if (cacheStatus != StepStatus.NOT_REQUESTED) add("キャッシュ：${cacheStatus.label}")
        failureReason?.let { add("詳細：$it") }
    }.joinToString(" / ").ifEmpty { "処理情報なし" }
}

private val StepStatus.label: String
    get() = when (this) {
        StepStatus.NOT_REQUESTED -> "対象外"
        StepStatus.SUCCESS -> "成功"
        StepStatus.SKIPPED -> "スキップ"
        StepStatus.FAILED -> "失敗"
    }

@Composable
private fun ResultRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text("$value 件", color = MaterialTheme.colorScheme.primary)
    }
}
