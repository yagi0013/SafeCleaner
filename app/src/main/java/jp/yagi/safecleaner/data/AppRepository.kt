package jp.yagi.safecleaner.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import jp.yagi.safecleaner.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

class AppRepository(private val context: Context) {
    private val packageManager get() = context.packageManager

    suspend fun getUserInstalledApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val apps = if (android.os.Build.VERSION.SDK_INT >= 33) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") packageManager.getInstalledApplications(0)
        }
        val collator = Collator.getInstance(Locale.JAPANESE)
        apps.asSequence()
            .filter(::isUserInstalled)
            .mapNotNull(::toInstalledApp)
            .sortedWith(compareBy(collator) { it.label })
            .toList()
    }

    fun getApp(packageName: String): InstalledApp? = runCatching {
        val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") packageManager.getApplicationInfo(packageName, 0)
        }
        if (isUserInstalled(info)) toInstalledApp(info) else null
    }.getOrNull()

    private fun isUserInstalled(info: ApplicationInfo): Boolean =
        info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 &&
            info.enabled

    private fun toInstalledApp(info: ApplicationInfo): InstalledApp? = runCatching {
        InstalledApp(
            packageName = info.packageName,
            label = packageManager.getApplicationLabel(info).toString(),
            icon = packageManager.getApplicationIcon(info),
            isStopped = info.flags and ApplicationInfo.FLAG_STOPPED != 0,
            uid = info.uid,
        )
    }.getOrNull()
}
