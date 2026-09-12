package jp.yagi.safecleaner

import android.app.Application
import jp.yagi.safecleaner.data.AppRepository
import jp.yagi.safecleaner.data.CacheStatsRepository
import jp.yagi.safecleaner.data.ExclusionRepository
import jp.yagi.safecleaner.data.MemoryRepository
import jp.yagi.safecleaner.data.ResultRepository

class SafeCleanerApp : Application() {
    lateinit var appRepository: AppRepository
        private set
    lateinit var exclusionRepository: ExclusionRepository
        private set
    lateinit var memoryRepository: MemoryRepository
        private set
    lateinit var cacheStatsRepository: CacheStatsRepository
        private set
    lateinit var cleanerController: CleanerController
        private set

    override fun onCreate() {
        super.onCreate()
        appRepository = AppRepository(this)
        exclusionRepository = ExclusionRepository(this)
        memoryRepository = MemoryRepository(this)
        cacheStatsRepository = CacheStatsRepository(this)
        cleanerController = CleanerController(
            context = this,
            appRepository = appRepository,
            exclusionRepository = exclusionRepository,
            memoryRepository = memoryRepository,
            resultRepository = ResultRepository(this),
            cacheStatsRepository = cacheStatsRepository,
        )
    }
}
