package jp.yagi.safecleaner.accessibility

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import jp.yagi.safecleaner.model.OperationStage
import jp.yagi.safecleaner.util.AppLog

class SettingsNavigator(
    private val service: CleanerAccessibilityService,
    private val resolver: OemUiResolver,
) {
    private val packageManager get() = service.packageManager
    private var targetPackage: String? = null
    private var targetLabel: String? = null
    private var navigationEventVersion = 0L
    var lastAppInfoFailureReason: String = "アプリ情報画面：未確認"
        private set
    private val labelOwners by lazy {
        @Suppress("DEPRECATION")
        packageManager.getInstalledApplications(0).groupBy {
            packageManager.getApplicationLabel(it).toString().trim()
        }.mapValues { (_, apps) -> apps.map { it.packageName }.toSet() }
    }

    fun openAppInfo(packageName: String, label: String) {
        targetPackage = packageName
        targetLabel = label.trim()
        markNavigation()
        AppLog.d("package=$packageName stage=${OperationStage.OPENING_APP_INFO}")
        service.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    suspend fun awaitAppInfo(timeoutMs: Long = 7_000): Boolean {
        var fresh = false
        var trusted = false
        var identity = false
        var controls = false
        AppLog.i("package=$targetPackage operation=navigation stage=app_info_wait status=START")
        val ready = service.awaitRoot(timeoutMs) { root ->
            fresh = hasFreshSettingsEvent()
            trusted = isSettingsRoot(root)
            identity = trusted && matchesTarget(root)
            controls = (findForceStop(root) != null ||
                findStorage(root)?.let(NodeFinder::clickable) != null) &&
                NodeFinder.findByResourceSuffix(root, resolver.clearCacheResourceSuffixes) == null &&
                NodeFinder.findByExactLabel(root, resolver.clearCacheLabels) == null
            appInfoReady(
                freshSettingsEvent = fresh,
                trustedRoot = trusted,
                matchesTarget = identity,
                hasControls = controls,
            )
        } != null
        lastAppInfoFailureReason = when {
            ready -> ""
            !trusted -> "アプリ情報画面：Settingsの画面を確認できません"
            !fresh -> "アプリ情報画面：遷移後のSettingsイベントを確認できません"
            !identity -> "アプリ情報画面：対象アプリの一致を確認できません"
            else -> "アプリ情報画面：対象アプリは一致しましたが、操作項目を確認できません"
        }
        AppLog.i("package=$targetPackage operation=navigation stage=app_info_wait confirmed=$ready fresh_event=$fresh trusted_root=$trusted target_match=$identity controls=$controls reason=${lastAppInfoFailureReason.ifEmpty { "none" }}")
        return ready
    }

    fun markNavigation() { navigationEventVersion = service.settingsEventVersion }
    fun hasFreshSettingsEvent(): Boolean = service.settingsEventVersion > navigationEventVersion

    /** Package text, or a unique installed-app label, must identify the requested app. */
    private fun matchesTarget(root: AccessibilityNodeInfo?): Boolean {
        val packageName = targetPackage ?: return false
        if (NodeFinder.findByExactLabel(root, setOf(packageName)) != null) return true
        val label = targetLabel?.takeIf { it.isNotBlank() } ?: return false
        return labelOwners[label] == setOf(packageName) &&
            NodeFinder.findByExactLabel(root, setOf(label)) != null
    }

    fun isTargetAppInfo(root: AccessibilityNodeInfo?): Boolean =
        isSettingsRoot(root) && matchesTarget(root)

    fun isPackageStopped(packageName: String): Boolean {
        @Suppress("DEPRECATION")
        return runCatching {
            packageManager.getApplicationInfo(packageName, 0).flags and
                android.content.pm.ApplicationInfo.FLAG_STOPPED != 0
        }.getOrDefault(false)
    }

    fun findStorage(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        if (!isTargetAppInfo(root)) null else
            NodeFinder.findByResourceSuffix(root, resolver.storageResourceSuffixes)
                ?: NodeFinder.findByExactLabel(root, resolver.storageLabels)

    /**
     * Android 15/16 AOSP exposes a dedicated exported storage activity. It is not a public SDK
     * constant, so it is used only when PackageManager positively resolves it to a Settings app.
     */
    fun openAppStorageDirect(packageName: String): Boolean {
        val intent = Intent(ACTION_APP_STORAGE_SETTINGS, Uri.parse("package:$packageName"))
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolved = if (android.os.Build.VERSION.SDK_INT >= 33) {
            packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION") packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } ?: return false
        val settingsPackage = resolved.activityInfo?.packageName ?: return false
        if (!resolver.isTrustedSettingsPackage(settingsPackage)) return false
        intent.setPackage(settingsPackage)
        return runCatching {
            markNavigation()
            service.startActivity(intent)
            AppLog.d("package=$packageName navigation=direct_app_storage settings=$settingsPackage")
            true
        }.getOrElse {
            AppLog.w("package=$packageName navigation=direct_app_storage_failed", it)
            false
        }
    }

    suspend fun findStorageWithScroll(): AccessibilityNodeInfo? {
        repeat(4) { attempt ->
            service.awaitRoot(if (attempt == 0) 700 else 450) { findStorage(it) }?.let { return it }
            val scrolled = service.rootInActiveWindow?.let(NodeFinder::firstScrollable)
                ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
            if (!scrolled) return null
            service.awaitEventOrTimeout(350)
        }
        return null
    }

    fun findForceStop(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        if (!isTargetAppInfo(root)) null else
            NodeFinder.findByResourceSuffix(root, resolver.forceStopResourceSuffixes)
                ?: NodeFinder.findByExactLabel(root, resolver.forceStopLabels)

    suspend fun returnToAppInfo(): Boolean {
        markNavigation()
        val accepted = service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        AppLog.i("package=$targetPackage operation=navigation stage=back accepted=$accepted")
        val confirmed = accepted && awaitAppInfo(5_000)
        AppLog.i("package=$targetPackage operation=navigation stage=return_app_info confirmed=$confirmed")
        return confirmed
    }

    internal fun isSettingsRoot(root: AccessibilityNodeInfo?): Boolean {
        val packageName = root?.packageName?.toString() ?: return false
        return resolver.isTrustedSettingsPackage(packageName)
    }

    companion object {
        const val ACTION_APP_STORAGE_SETTINGS = "com.android.settings.APP_STORAGE_SETTINGS"
    }
}
