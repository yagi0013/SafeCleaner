package jp.yagi.safecleaner

import android.content.Context
import jp.yagi.safecleaner.accessibility.AccessibilityStatus
import jp.yagi.safecleaner.accessibility.CleanerAccessibilityService
import jp.yagi.safecleaner.data.AppRepository
import jp.yagi.safecleaner.data.CacheStatsRepository
import jp.yagi.safecleaner.data.ExclusionRepository
import jp.yagi.safecleaner.data.MemoryRepository
import jp.yagi.safecleaner.data.ResultRepository
import jp.yagi.safecleaner.model.CleanerMode
import jp.yagi.safecleaner.model.CleanerStatus
import jp.yagi.safecleaner.model.CleanerUiState
import jp.yagi.safecleaner.model.OperationStage
import jp.yagi.safecleaner.model.RunSummary
import jp.yagi.safecleaner.model.toFixedTargetQueue
import jp.yagi.safecleaner.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class CleanerController(
    private val context: Context,
    private val appRepository: AppRepository,
    private val exclusionRepository: ExclusionRepository,
    private val memoryRepository: MemoryRepository,
    private val resultRepository: ResultRepository,
    private val cacheStatsRepository: CacheStatsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(CleanerUiState())
    val state: StateFlow<CleanerUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            val last = resultRepository.load()
            mutableState.value = mutableState.value.copy(lastSummary = last)
        }
    }

    fun refreshAccessibilityState() {
        if (mutableState.value.isRunning) return
        mutableState.value = mutableState.value.copy(
            status = if (AccessibilityStatus.isEnabled(context)) CleanerStatus.READY
            else CleanerStatus.ACCESSIBILITY_DISABLED,
        )
    }

    @Synchronized
    fun start(mode: CleanerMode): Boolean {
        if (mutableState.value.isRunning) return false
        if (!AccessibilityStatus.isEnabled(context)) {
            mutableState.value = mutableState.value.copy(
                status = CleanerStatus.ACCESSIBILITY_DISABLED,
                message = "ユーザー補助サービスを有効にしてください",
            )
            return false
        }
        mutableState.value = mutableState.value.copy(
            status = CleanerStatus.PREPARING, mode = mode, currentIndex = 0,
            totalCount = 0, currentApp = null, stage = OperationStage.PREPARING,
            message = "対象アプリを確認しています",
        )
        scope.launch { prepareAndRun(mode) }
        return true
    }

    fun cancel() {
        if (!mutableState.value.isRunning) return
        CleanerAccessibilityService.connectedService.value?.cancelCurrentRun()
        mutableState.value = mutableState.value.copy(status = CleanerStatus.CANCELLED, message = "処理を中止しました")
    }

    private suspend fun prepareAndRun(mode: CleanerMode) {
        try {
            val allApps = appRepository.getUserInstalledApps()
            val basePlan = exclusionRepository.buildTargetPlan(allApps).let { plan ->
                // Freeze package membership once per run. Do not rebuild this plan from changed
                // FLAG_STOPPED/cache/runtime state while Settings automation is in progress.
                plan.copy(targets = plan.targets.toFixedTargetQueue())
            }
            AppLog.i(
                "mode=$mode queue=FROZEN targets=${basePlan.targets.size} " +
                    "packages=${basePlan.targets.joinToString(",") { it.packageName }}",
            )
            mutableState.value = mutableState.value.copy(message = "キャッシュ容量を事前確認しています")
            val plan = if (mode.needsCache && cacheStatsRepository.hasUsageAccess()) {
                basePlan.copy(targets = cacheStatsRepository.attachCacheBytes(basePlan.targets))
            } else {
                basePlan
            }
            val ramBefore = memoryRepository.snapshot().availableBytes
            val service = withTimeoutOrNull(4_000) {
                CleanerAccessibilityService.connectedService.first { it != null }
            }
            if (service == null) {
                mutableState.value = mutableState.value.copy(
                    status = CleanerStatus.ERROR,
                    message = "ユーザー補助サービスへ接続できませんでした",
                )
                return
            }
            if (plan.targets.isEmpty()) {
                finishEmpty(mode, plan.automaticExcludedCount, plan.userExcludedCount, ramBefore)
                return
            }

            mutableState.value = mutableState.value.copy(
                status = CleanerStatus.RUNNING, totalCount = plan.targets.size,
                message = "クリーン処理を開始します",
            )
            val startedAt = System.currentTimeMillis()
            val results = service.runQueue(mode, plan.targets) { index, app, stage ->
                mutableState.value = mutableState.value.copy(
                    status = CleanerStatus.RUNNING,
                    currentIndex = index + 1,
                    totalCount = plan.targets.size,
                    currentApp = app,
                    stage = stage,
                    message = null,
                )
            }
            if (mutableState.value.status == CleanerStatus.CANCELLED) return
            val summary = RunSummary(
                mode = mode, startedAtMillis = startedAt, finishedAtMillis = System.currentTimeMillis(),
                targetCount = plan.targets.size, automaticExcludedCount = plan.automaticExcludedCount,
                userExcludedCount = plan.userExcludedCount, results = results,
                availableRamBefore = ramBefore, availableRamAfter = memoryRepository.snapshot().availableBytes,
            )
            resultRepository.save(summary)
            mutableState.value = mutableState.value.copy(
                status = CleanerStatus.COMPLETED, currentApp = null, stage = OperationStage.COMPLETE,
                message = "処理が完了しました", lastSummary = summary,
            )
            context.startActivity(
                android.content.Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SHOW_RESULT, true)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        } catch (error: Throwable) {
            AppLog.e("controller=ERROR mode=$mode", error)
            mutableState.value = mutableState.value.copy(
                status = CleanerStatus.ERROR, message = error.message ?: "予期しないエラーが発生しました",
            )
        }
    }

    private suspend fun finishEmpty(mode: CleanerMode, automatic: Int, user: Int, ramBefore: Long) {
        val summary = RunSummary(
            mode = mode, startedAtMillis = System.currentTimeMillis(), finishedAtMillis = System.currentTimeMillis(),
            targetCount = 0, automaticExcludedCount = automatic, userExcludedCount = user,
            results = emptyList(), availableRamBefore = ramBefore,
            availableRamAfter = memoryRepository.snapshot().availableBytes,
        )
        resultRepository.save(summary)
        mutableState.value = mutableState.value.copy(
            status = CleanerStatus.COMPLETED, message = "処理対象のアプリはありません", lastSummary = summary,
        )
    }
}
