package jp.yagi.safecleaner.accessibility

import android.os.Build

/**
 * Settings UI vocabulary is intentionally centralized. Only unambiguous labels are accepted;
 * unknown OEM screens are skipped instead of guessed.
 */
class OemUiResolver {
    val settingsPackages: Set<String> = buildSet {
        add("com.android.settings")
        add("com.samsung.android.settings")
        add("com.miui.securitycenter")
        add("com.coloros.safecenter")
    }

    val forceStopResourceSuffixes = setOf(
        "force_stop_button", "force_stop", "button_force_stop",
    )
    val storageResourceSuffixes = setOf(
        "storage_settings", "storage", "storage_usage", "storage_summary",
    )
    val clearCacheResourceSuffixes = setOf(
        "clear_cache_button", "button_clear_cache", "clear_cache",
    )

    val forceStopLabels = setOf(
        "強制停止", "強制終了", "Force stop", "FORCE STOP",
    )
    val storageLabels = setOf(
        "ストレージとキャッシュ", "ストレージ", "ストレージ使用量", "使用済み容量",
        "Storage & cache", "Storage and cache", "Storage", "Storage usage",
    )
    val clearCacheLabels = setOf(
        "キャッシュを削除", "キャッシュを消去", "Clear cache", "CLEAR CACHE",
    )
    val confirmForceStopLabels = setOf(
        "OK", "強制停止", "強制終了", "Force stop", "FORCE STOP",
    )

    val destructiveDataLabels = setOf(
        "データを削除", "データを消去", "ストレージを消去", "すべてのデータを削除",
        "Clear data", "Clear storage", "Delete data", "Erase all data",
    )

    val manufacturer: String = Build.MANUFACTURER.lowercase()

    fun isTrustedSettingsPackage(packageName: String): Boolean =
        packageName in settingsPackages ||
            packageName.contains("settings", ignoreCase = true) ||
            packageName.contains("securitycenter", ignoreCase = true) ||
            packageName.contains("safecenter", ignoreCase = true) ||
            packageName.contains("systemmanager", ignoreCase = true)
}
