package jp.yagi.safecleaner.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import jp.yagi.safecleaner.model.InstalledApp
import jp.yagi.safecleaner.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class CacheStatsRepository(private val context: Context) {
    private val appOpsManager = context.getSystemService(AppOpsManager::class.java)
    private val storageStatsManager = context.getSystemService(StorageStatsManager::class.java)

    fun hasUsageAccess(): Boolean {
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Queries each UID once; queryStatsForUid is normally faster than per-package calculation.
     * A shared UID may over-report but never causes an unsafe skip: only an exact aggregate zero skips.
     */
    suspend fun attachCacheBytes(apps: List<InstalledApp>): List<InstalledApp> {
        if (!hasUsageAccess() || apps.isEmpty()) return apps
        return withContext(Dispatchers.IO) {
            val semaphore = Semaphore(4)
            val cacheByUid = coroutineScope {
                apps.map { it.uid }.distinct().map { uid ->
                    async {
                        semaphore.withPermit {
                            uid to runCatching {
                                storageStatsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, uid).cacheBytes
                            }.onFailure {
                                AppLog.w("cache_preflight uid=$uid status=UNAVAILABLE", it)
                            }.getOrNull()
                        }
                    }
                }.awaitAll().toMap()
            }
            apps.map { app ->
                val bytes = cacheByUid[app.uid]
                if (bytes != null) AppLog.d("package=${app.packageName} cache_preflight_bytes=$bytes")
                app.copy(cacheBytes = bytes)
            }
        }
    }
}
