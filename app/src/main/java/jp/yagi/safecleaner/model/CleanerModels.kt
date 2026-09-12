package jp.yagi.safecleaner.model

import android.graphics.drawable.Drawable
import java.util.UUID

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    /** Android package stopped state: never launched since install, or explicitly force-stopped. */
    val isStopped: Boolean,
    val uid: Int,
    /** Null means unavailable or not yet queried; known values below the threshold are skipped. */
    val cacheBytes: Long? = null,
)

const val MIN_CACHE_BYTES_TO_CLEAR: Long = 1024L * 1024L

/**
 * Creates the immutable package queue used for one cleaning session.
 *
 * Package/runtime state is intentionally never refreshed while this snapshot is being consumed.
 * Apps which restart during Settings automation therefore cannot be appended or processed twice.
 */
fun Iterable<InstalledApp>.toFixedTargetQueue(): List<InstalledApp> =
    distinctBy(InstalledApp::packageName).toList()

enum class CleanerMode {
    CLEAN_ALL,
    FORCE_STOP_ONLY,
    CLEAR_CACHE_ONLY;

    val needsCache: Boolean get() = this != FORCE_STOP_ONLY
    val needsForceStop: Boolean get() = this != CLEAR_CACHE_ONLY
}

fun shouldSkipForceStopBeforeOpeningSettings(mode: CleanerMode, isStopped: Boolean): Boolean =
    mode == CleanerMode.FORCE_STOP_ONLY && isStopped

fun shouldSkipCacheBeforeOpeningSettings(mode: CleanerMode, cacheBytes: Long?): Boolean =
    mode.needsCache && cacheBytes != null && cacheBytes < MIN_CACHE_BYTES_TO_CLEAR

enum class CleanerStatus {
    READY,
    ACCESSIBILITY_DISABLED,
    PREPARING,
    RUNNING,
    COMPLETED,
    CANCELLED,
    ERROR,
}

enum class OperationStage {
    PREPARING,
    OPENING_APP_INFO,
    OPENING_STORAGE,
    CLEARING_CACHE,
    RETURNING_TO_APP_INFO,
    FORCE_STOPPING,
    CONFIRMING_FORCE_STOP,
    COMPLETE,
}

enum class StepStatus { NOT_REQUESTED, SUCCESS, SKIPPED, FAILED }

enum class AppResultCategory { SUCCESS, SKIPPED, FAILED }

data class AppOperationResult(
    val packageName: String,
    val label: String,
    val cacheStatus: StepStatus = StepStatus.NOT_REQUESTED,
    val forceStopStatus: StepStatus = StepStatus.NOT_REQUESTED,
    val failureReason: String? = null,
) {
    val succeeded: Boolean
        get() = (cacheStatus == StepStatus.SUCCESS || forceStopStatus == StepStatus.SUCCESS) &&
            cacheStatus != StepStatus.FAILED && forceStopStatus != StepStatus.FAILED &&
            cacheStatus != StepStatus.SKIPPED && forceStopStatus != StepStatus.SKIPPED

    val category: AppResultCategory
        get() = when {
            cacheStatus == StepStatus.FAILED || forceStopStatus == StepStatus.FAILED -> AppResultCategory.FAILED
            succeeded -> AppResultCategory.SUCCESS
            else -> AppResultCategory.SKIPPED
        }
}

fun mergeCleanAllResults(
    queueSnapshot: List<InstalledApp>,
    forceStopResults: List<AppOperationResult>,
    cacheResults: List<AppOperationResult>,
): List<AppOperationResult> {
    val forceByPackage = forceStopResults.associateBy(AppOperationResult::packageName)
    val cacheByPackage = cacheResults.associateBy(AppOperationResult::packageName)
    return queueSnapshot.mapNotNull { app ->
        val force = forceByPackage[app.packageName]
        val cache = cacheByPackage[app.packageName]
        if (force == null && cache == null) return@mapNotNull null
        AppOperationResult(
            packageName = app.packageName,
            label = app.label,
            cacheStatus = cache?.cacheStatus ?: StepStatus.NOT_REQUESTED,
            forceStopStatus = force?.forceStopStatus ?: StepStatus.NOT_REQUESTED,
            failureReason = listOfNotNull(force?.failureReason, cache?.failureReason)
                .distinct()
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" / "),
        )
    }
}

data class RunSummary(
    val id: String = UUID.randomUUID().toString(),
    val mode: CleanerMode,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val targetCount: Int,
    val automaticExcludedCount: Int,
    val userExcludedCount: Int,
    val results: List<AppOperationResult>,
    val availableRamBefore: Long,
    val availableRamAfter: Long,
) {
    val successCount get() = results.count { it.category == AppResultCategory.SUCCESS }
    val forceStopSuccessCount get() = results.count { it.forceStopStatus == StepStatus.SUCCESS }
    val cacheSuccessCount get() = results.count { it.cacheStatus == StepStatus.SUCCESS }
    val skippedCount get() = results.count { it.category == AppResultCategory.SKIPPED }
    val failedCount get() = results.count { it.category == AppResultCategory.FAILED }
}

data class CleanerUiState(
    val status: CleanerStatus = CleanerStatus.READY,
    val mode: CleanerMode? = null,
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val currentApp: InstalledApp? = null,
    val stage: OperationStage? = null,
    val message: String? = null,
    val lastSummary: RunSummary? = null,
) {
    val isRunning get() = status == CleanerStatus.PREPARING || status == CleanerStatus.RUNNING
}

data class TargetPlan(
    val targets: List<InstalledApp>,
    val automaticExcludedCount: Int,
    val userExcludedCount: Int,
)

data class RamSnapshot(
    val totalBytes: Long,
    val availableBytes: Long,
) {
    val usedBytes get() = (totalBytes - availableBytes).coerceAtLeast(0)
}
