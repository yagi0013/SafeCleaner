package jp.yagi.safecleaner.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import jp.yagi.safecleaner.model.StepStatus
import jp.yagi.safecleaner.util.AppLog
import jp.yagi.safecleaner.data.CacheStatsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException

class CacheClearHandler(
    private val service: CleanerAccessibilityService,
    private val navigator: SettingsNavigator,
    private val resolver: OemUiResolver,
) {
    private val cacheStats by lazy { CacheStatsRepository(service) }
    suspend fun execute(
        packageName: String,
        onOpeningStorage: () -> Unit,
        onClearing: () -> Unit,
        onReturning: () -> Unit,
    ): StepResult {
        onOpeningStorage()
        val direct = navigator.openAppStorageDirect(packageName)
        AppLog.i("package=$packageName operation=cache stage=storage_navigation direct_launch_accepted=$direct")
        if (!direct) {
            val storageCandidate = navigator.findStorageWithScroll()
                ?: return skipped(packageName, "ストレージ画面への項目が見つかりません")
            val storageButton = NodeFinder.clickable(storageCandidate)
                ?: return skipped(packageName, "ストレージ項目を安全に押せません")
            if (!storageButton.isEnabled) return skipped(packageName, "ストレージ項目が無効です")
            AppLog.d("package=$packageName button=storage id=${storageCandidate.viewIdResourceName} text=${storageCandidate.text}")
            navigator.markNavigation()
            if (!storageButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return recordStep(packageName, "cache", "storage_click", StepResult(StepStatus.FAILED, "ストレージ画面：クリック受付を確認できません"))
            }
            AppLog.i("package=$packageName operation=cache stage=storage_click accepted=true")
        }

        val diagnostics = ButtonDiagnostics(packageName, "cache")
        val cacheCandidate = service.awaitRoot(10_000) { root ->
            diagnostics.enabledCandidate(navigator.isSettingsRoot(root) && navigator.hasFreshSettingsEvent(), findSafeClearCache(root))
        } ?: run {
            onReturning()
            val returned = restoreAppInfo(packageName)
            val reason = buttonUnavailableReason("キャッシュ削除", diagnostics.state) +
                if (returned) "" else " / 画面復帰：アプリ情報画面を確認できません"
            return skipped(packageName, reason)
        }

        val cacheButton = NodeFinder.clickable(cacheCandidate)
        if (cacheButton == null) {
            onReturning(); restoreAppInfo(packageName)
            return skipped(packageName, "キャッシュ削除ボタンを安全に押せません")
        }
        if (!cacheButton.isEnabled) {
            onReturning(); restoreAppInfo(packageName)
            return skipped(packageName, "キャッシュ削除：検出後、ボタンが無効になりました（空とは未確認）")
        }

        onClearing()
        val beforeBytes = cacheStats.cacheBytesForPackage(packageName)
        AppLog.d("package=$packageName button=clear_cache id=${cacheCandidate.viewIdResourceName} text=${cacheCandidate.text}")
        val clickEventVersion = service.settingsEventVersion
        val clicked = service.awaitRoot(3_000) { root ->
            val freshButton = findSafeClearCache(root)?.let(NodeFinder::clickable)
                ?.takeIf { it.isEnabled } ?: return@awaitRoot null
            true.takeIf { freshButton.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        } == true
        AppLog.i("package=$packageName operation=cache stage=click accepted=$clicked")
        val verified = clicked && withTimeoutOrNull(7_000) {
            while (!service.isCancellationRequested) {
                val disabled = findSafeClearCache(service.rootInActiveWindow)
                    ?.let(NodeFinder::clickable)?.isEnabled == false &&
                    service.settingsEventVersion > clickEventVersion
                val afterBytes = cacheStats.cacheBytesForPackage(packageName)
                if (cacheClearVerified(beforeBytes, afterBytes, disabled)) {
                    AppLog.i("package=$packageName cache=VERIFIED before=$beforeBytes after=$afterBytes disabled=$disabled")
                    return@withTimeoutOrNull true
                }
                delay(250)
            }
            false
        } == true
        AppLog.i("package=$packageName operation=cache stage=completion verified=$verified")
        onReturning()
        val returned = restoreAppInfo(packageName)
        return recordStep(packageName, "cache", "result", cacheOperationResult(clicked, verified, returned))
    }

    private suspend fun restoreAppInfo(packageName: String): Boolean {
        val returned = try {
            navigator.returnToAppInfo()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.e("package=$packageName operation=cache stage=return_app_info status=ERROR", error)
            false
        }
        AppLog.i("package=$packageName operation=cache stage=return_app_info confirmed=$returned")
        return returned
    }

    private fun findSafeClearCache(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (!navigator.isSettingsRoot(root)) return null
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
        return recordStep(packageName, "cache", "navigation_or_button", StepResult(StepStatus.SKIPPED, reason))
    }
}
