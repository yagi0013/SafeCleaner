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
    /** Null means unavailable or not yet queried; only an exact zero is safe to preflight-skip. */
    val cacheBytes: Long? = null,
)

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
    mode.needsCache && cacheBytes == 0L

fun shouldSkipAllBeforeOpeningSettings(mode: CleanerMode, isStopped: Boolean, cacheBytes: Long?): Boolean =
    mode == CleanerMode.CLEAN_ALL && isStopped && cacheBytes == 0L

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
    val successCount get() = results.count(AppOperationResult::succeeded)
    val forceStopSuccessCount get() = results.count { it.forceStopStatus == StepStatus.SUCCESS }
    val cacheSuccessCount get() = results.count { it.cacheStatus == StepStatus.SUCCESS }
    val skippedCount get() = results.count {
        it.cacheStatus == StepStatus.SKIPPED || it.forceStopStatus == StepStatus.SKIPPED
    }
    val failedCount get() = results.count {
        it.cacheStatus == StepStatus.FAILED || it.forceStopStatus == StepStatus.FAILED
    }
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
