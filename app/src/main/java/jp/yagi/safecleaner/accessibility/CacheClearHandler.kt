package jp.yagi.safecleaner.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import jp.yagi.safecleaner.model.StepStatus
import jp.yagi.safecleaner.util.AppLog

class CacheClearHandler(
    private val service: CleanerAccessibilityService,
    private val navigator: SettingsNavigator,
    private val resolver: OemUiResolver,
) {
    suspend fun execute(
        packageName: String,
        onOpeningStorage: () -> Unit,
        onClearing: () -> Unit,
        onReturning: () -> Unit,
    ): StepResult {
        onOpeningStorage()
        if (!navigator.openAppStorageDirect(packageName)) {
            val storageCandidate = navigator.findStorageWithScroll()
                ?: return skipped(packageName, "ストレージ画面への項目が見つかりません")
            val storageButton = NodeFinder.clickable(storageCandidate)
                ?: return skipped(packageName, "ストレージ項目を安全に押せません")
            if (!storageButton.isEnabled) return skipped(packageName, "ストレージ項目が無効です")
            AppLog.d("package=$packageName button=storage id=${storageCandidate.viewIdResourceName} text=${storageCandidate.text}")
            if (!storageButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return StepResult(StepStatus.FAILED, "ストレージ画面を開けません")
            }
        }

        val cacheCandidate = service.awaitRoot(10_000, ::findSafeClearCache) ?: run {
            onReturning()
            navigator.returnToAppInfo()
            return skipped(packageName, "キャッシュ削除ボタンが見つかりません")
        }

        val cacheButton = NodeFinder.clickable(cacheCandidate)
        if (cacheButton == null) {
            onReturning(); navigator.returnToAppInfo()
            return skipped(packageName, "キャッシュ削除ボタンを安全に押せません")
        }
        if (!cacheButton.isEnabled) {
            onReturning(); navigator.returnToAppInfo()
            return StepResult(StepStatus.SKIPPED, "キャッシュは空、またはボタンが無効です")
        }

        onClearing()
        AppLog.d("package=$packageName button=clear_cache id=${cacheCandidate.viewIdResourceName} text=${cacheCandidate.text}")
        val clicked = service.awaitRoot(3_000) { root ->
            val freshButton = findSafeClearCache(root)?.let(NodeFinder::clickable)
                ?.takeIf { it.isEnabled } ?: return@awaitRoot null
            true.takeIf { freshButton.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        } == true
        service.awaitEventOrTimeout(500)
        onReturning()
        val returned = navigator.returnToAppInfo()
        return when {
            !clicked -> StepResult(StepStatus.FAILED, "キャッシュ削除ボタンの操作に失敗しました")
            !returned -> StepResult(StepStatus.FAILED, "アプリ情報画面へ戻れません")
            else -> StepResult(StepStatus.SUCCESS).also { AppLog.i("package=$packageName cache=SUCCESS") }
        }
    }

    private fun findSafeClearCache(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        val candidate = NodeFinder.findByResourceSuffix(root, resolver.clearCacheResourceSuffixes)
            ?: NodeFinder.findByExactLabel(root, resolver.clearCacheLabels)
            ?: return null
        // The selected node itself must identify cache-only; parent text is never accepted.
        val positivelyCacheOnly = candidate.viewIdResourceName?.substringAfterLast('/') in
            resolver.clearCacheResourceSuffixes ||
            candidate.text?.toString()?.trim() in resolver.clearCacheLabels ||
            candidate.contentDescription?.toString()?.trim() in resolver.clearCacheLabels
        return candidate.takeIf {
            positivelyCacheOnly && !NodeFinder.containsAnyLabel(candidate, resolver.destructiveDataLabels)
        }
    }

    private fun skipped(packageName: String, reason: String): StepResult {
        AppLog.w("package=$packageName cache=SKIP reason=$reason")
        return StepResult(StepStatus.SKIPPED, reason)
    }
}
