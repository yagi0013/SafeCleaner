package jp.yagi.safecleaner.data

import android.app.ActivityManager
import android.content.Context
import jp.yagi.safecleaner.model.RamSnapshot

class MemoryRepository(context: Context) {
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    fun snapshot(): RamSnapshot {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return RamSnapshot(totalBytes = info.totalMem, availableBytes = info.availMem)
    }
}
