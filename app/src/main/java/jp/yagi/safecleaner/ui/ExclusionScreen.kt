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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.yagi.safecleaner.SafeCleanerApp
import jp.yagi.safecleaner.model.InstalledApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExclusionScreen(app: SafeCleanerApp, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by remember { mutableStateOf("") }
    val excluded by app.exclusionRepository.userExcludedPackages.collectAsState(initial = emptySet())
    val automatic = remember(apps) { app.exclusionRepository.automaticExclusions() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { apps = app.appRepository.getUserInstalledApps() }
    val filtered = remember(apps, query) {
        apps.orEmpty().filter {
            query.isBlank() || it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("除外アプリ設定") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "戻る") } },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "保護をONにしたアプリは一括処理から除外されます。自動保護は変更できません。",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                label = { Text("アプリ名またはパッケージ名で検索") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (apps == null) {
                Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.packageName }) { item ->
                        val autoProtected = item.packageName in automatic
                        val protected = autoProtected || item.packageName in excluded
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AppIcon(item.icon, item.label)
                            Column(Modifier.weight(1f)) {
                                Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    item.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (autoProtected) "自動保護" else if (protected) "一括処理から除外" else "処理対象",
                                    color = if (protected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            if (autoProtected) Icon(Icons.Outlined.Shield, "自動保護")
                            Switch(
                                checked = protected,
                                enabled = !autoProtected,
                                onCheckedChange = { value ->
                                    scope.launch { app.exclusionRepository.setUserExcluded(item.packageName, value) }
                                },
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
