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

    fun openAppInfo(packageName: String) {
        AppLog.d("package=$packageName stage=${OperationStage.OPENING_APP_INFO}")
        service.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    suspend fun awaitAppInfo(timeoutMs: Long = 7_000): Boolean = service.awaitRoot(timeoutMs) { root ->
        isSettingsRoot(root) && (
            findForceStop(root) != null || findStorage(root) != null
        )
    } != null

    fun findStorage(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
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
        NodeFinder.findByResourceSuffix(root, resolver.forceStopResourceSuffixes)
            ?: NodeFinder.findByExactLabel(root, resolver.forceStopLabels)

    suspend fun returnToAppInfo(): Boolean {
        service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        return awaitAppInfo(5_000)
    }

    private fun isSettingsRoot(root: AccessibilityNodeInfo?): Boolean {
        val packageName = root?.packageName?.toString() ?: return false
        return resolver.isTrustedSettingsPackage(packageName)
    }

    companion object {
        const val ACTION_APP_STORAGE_SETTINGS = "com.android.settings.APP_STORAGE_SETTINGS"
    }
}
