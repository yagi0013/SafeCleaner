package jp.yagi.safecleaner.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import jp.yagi.safecleaner.MainActivity
import jp.yagi.safecleaner.R
import jp.yagi.safecleaner.model.AppOperationResult
import jp.yagi.safecleaner.model.CleanerMode
import jp.yagi.safecleaner.model.InstalledApp
import jp.yagi.safecleaner.model.OperationStage
import jp.yagi.safecleaner.model.StepStatus
import jp.yagi.safecleaner.model.shouldSkipAllBeforeOpeningSettings
import jp.yagi.safecleaner.model.shouldSkipCacheBeforeOpeningSettings
import jp.yagi.safecleaner.model.shouldSkipForceStopBeforeOpeningSettings
import jp.yagi.safecleaner.model.toFixedTargetQueue
import jp.yagi.safecleaner.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull

class CleanerAccessibilityService : AccessibilityService() {
    private val eventSignal = Channel<Unit>(Channel.CONFLATED)
    private val resolver = OemUiResolver()
    private val navigator by lazy { SettingsNavigator(this, resolver) }
    private val forceStopHandler by lazy { ForceStopHandler(this, navigator, resolver) }
    private val cacheClearHandler by lazy { CacheClearHandler(this, navigator, resolver) }
    @Volatile private var cancelRequested = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        connectedService.value = this
        createNotificationChannel()
        AppLog.i("accessibility=CONNECTED manufacturer=${resolver.manufacturer}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        AppLog.d(
            "event=${AccessibilityEvent.eventTypeToString(event.eventType)} " +
                "package=${event.packageName} class=${event.className}",
        )
        eventSignal.trySend(Unit)
    }

    override fun onInterrupt() {
        AppLog.w("accessibility=INTERRUPTED")
    }

    override fun onDestroy() {
        if (connectedService.value === this) connectedService.value = null
        super.onDestroy()
    }

    fun cancelCurrentRun() {
        cancelRequested = true
        eventSignal.trySend(Unit)
    }

    suspend fun runQueue(
        mode: CleanerMode,
        targets: List<InstalledApp>,
        onProgress: (index: Int, app: InstalledApp, stage: OperationStage) -> Unit,
    ): List<AppOperationResult> {
        cancelRequested = false
        // This is a defensive copy of the controller's frozen queue. No package or process state
        // is queried to add targets during the run, even if an app restarts after being handled.
        val queueSnapshot = targets.toFixedTargetQueue()
        AppLog.i("mode=$mode queue=START fixed_targets=${queueSnapshot.size}")
        val results = mutableListOf<AppOperationResult>()
        val processedPackages = mutableSetOf<String>()
        for ((index, app) in queueSnapshot.withIndex()) {
            if (cancelRequested) break
            if (!processedPackages.add(app.packageName)) {
                AppLog.w("package=${app.packageName} operation=SKIP reason=DUPLICATE_IN_SESSION")
                continue
            }
            val skipCacheFromStats = shouldSkipCacheBeforeOpeningSettings(mode, app.cacheBytes)

            if (shouldSkipAllBeforeOpeningSettings(mode, app.isStopped, app.cacheBytes)) {
                progress(index, queueSnapshot.size, app, OperationStage.COMPLETE, onProgress)
                AppLog.i("package=${app.packageName} operation=SKIP preflight=STOPPED_AND_ZERO_CACHE")
                results += AppOperationResult(
                    packageName = app.packageName,
                    label = app.label,
                    cacheStatus = StepStatus.SKIPPED,
                    forceStopStatus = StepStatus.SKIPPED,
                    failureReason = "キャッシュ0 Bかつ未起動または強制停止済みのため事前スキップ",
                )
                continue
            }

            if (mode == CleanerMode.CLEAR_CACHE_ONLY && skipCacheFromStats) {
                progress(index, queueSnapshot.size, app, OperationStage.CLEARING_CACHE, onProgress)
                AppLog.i("package=${app.packageName} cache=SKIP preflight=ZERO_BYTES")
                results += AppOperationResult(
                    packageName = app.packageName,
                    label = app.label,
                    cacheStatus = StepStatus.SKIPPED,
                    failureReason = "キャッシュ0 Bのため事前スキップ",
                )
                continue
            }

            // FLAG_STOPPED is a public, no-extra-permission signal. For force-stop-only runs this
            // avoids opening Settings at all. CLEAN_ALL still opens storage for cache handling.
            if (shouldSkipForceStopBeforeOpeningSettings(mode, app.isStopped)) {
                progress(index, queueSnapshot.size, app, OperationStage.FORCE_STOPPING, onProgress)
                AppLog.i("package=${app.packageName} force_stop=SKIP preflight=FLAG_STOPPED")
                results += AppOperationResult(
                    packageName = app.packageName,
                    label = app.label,
                    forceStopStatus = StepStatus.SKIPPED,
                    failureReason = "未起動または強制停止済みのため事前スキップ",
                )
                continue
            }
            var cache = if (mode.needsCache) StepStatus.SKIPPED else StepStatus.NOT_REQUESTED
            var force = if (mode.needsForceStop) StepStatus.SKIPPED else StepStatus.NOT_REQUESTED
            val reasons = mutableListOf<String>()
            try {
                progress(index, queueSnapshot.size, app, OperationStage.OPENING_APP_INFO, onProgress)
                navigator.openAppInfo(app.packageName)
                if (!navigator.awaitAppInfo()) {
                    reasons += "アプリ情報画面を確認できません"
                    if (mode.needsCache) cache = StepStatus.FAILED
                    if (mode.needsForceStop) force = StepStatus.FAILED
                } else {
                    if (skipCacheFromStats) {
                        cache = StepStatus.SKIPPED
                        reasons += "キャッシュ0 Bのためキャッシュ画面をスキップ"
                        AppLog.i("package=${app.packageName} cache=SKIP preflight=ZERO_BYTES")
                    } else if (mode.needsCache && !cancelRequested) {
                        val result = cacheClearHandler.execute(
                            packageName = app.packageName,
                            onOpeningStorage = { progress(index, queueSnapshot.size, app, OperationStage.OPENING_STORAGE, onProgress) },
                            onClearing = { progress(index, queueSnapshot.size, app, OperationStage.CLEARING_CACHE, onProgress) },
                            onReturning = { progress(index, queueSnapshot.size, app, OperationStage.RETURNING_TO_APP_INFO, onProgress) },
                        )
                        cache = result.status
                        result.reason?.let(reasons::add)
                    }
                    if (mode.needsForceStop && app.isStopped) {
                        force = StepStatus.SKIPPED
                        reasons += "未起動または強制停止済みのため強制停止をスキップ"
                        AppLog.i("package=${app.packageName} force_stop=SKIP preflight=FLAG_STOPPED")
                    } else if (mode.needsForceStop && !cancelRequested) {
                        progress(index, queueSnapshot.size, app, OperationStage.FORCE_STOPPING, onProgress)
                        val result = forceStopHandler.execute(app.packageName) {
                            progress(index, queueSnapshot.size, app, OperationStage.CONFIRMING_FORCE_STOP, onProgress)
                        }
                        force = result.status
                        result.reason?.let(reasons::add)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AppLog.e("package=${app.packageName} operation=ERROR", error)
                reasons += (error.message ?: "予期しない操作エラー")
                if (mode.needsCache && cache == StepStatus.SKIPPED) cache = StepStatus.FAILED
                if (mode.needsForceStop && force == StepStatus.SKIPPED) force = StepStatus.FAILED
            }
            results += AppOperationResult(
                packageName = app.packageName, label = app.label,
                cacheStatus = cache, forceStopStatus = force,
                failureReason = reasons.takeIf { it.isNotEmpty() }?.joinToString(" / "),
            )
        }
        notificationManager.cancel(PROGRESS_NOTIFICATION_ID)
        return results
    }

    internal suspend fun <T> awaitRoot(timeoutMs: Long, finder: (AccessibilityNodeInfo?) -> T?): T? {
        val deadline = android.os.SystemClock.uptimeMillis() + timeoutMs
        while (!cancelRequested) {
            finder(rootInActiveWindow)?.let { return it }
            val remaining = deadline - android.os.SystemClock.uptimeMillis()
            if (remaining <= 0) return null
            withTimeoutOrNull(minOf(remaining, 350)) { eventSignal.receive() }
        }
        return null
    }

    internal suspend fun awaitEventOrTimeout(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) { eventSignal.receive() }
    }

    private fun progress(
        index: Int,
        total: Int,
        app: InstalledApp,
        stage: OperationStage,
        callback: (Int, InstalledApp, OperationStage) -> Unit,
    ) {
        callback(index, app, stage)
        showProgressNotification(index + 1, total, app.label, stage)
        AppLog.d("package=${app.packageName} mode_stage=$stage progress=${index + 1}/$total")
    }

    private val notificationManager get() = getSystemService(NotificationManager::class.java)

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "クリーン処理", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun showProgressNotification(current: Int, total: Int, label: String, stage: OperationStage) {
        val stageText = when (stage) {
            OperationStage.OPENING_APP_INFO -> "アプリ情報を開いています"
            OperationStage.OPENING_STORAGE -> "ストレージを開いています"
            OperationStage.CLEARING_CACHE -> "キャッシュ削除"
            OperationStage.RETURNING_TO_APP_INFO -> "アプリ情報へ戻っています"
            OperationStage.FORCE_STOPPING -> "強制停止"
            OperationStage.CONFIRMING_FORCE_STOP -> "強制停止を確認"
            else -> "処理中"
        }
        val contentIntent = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("処理中 $current / $total　$label")
            .setContentText(stageText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(total, current, false)
            .setContentIntent(contentIntent)
            .build()
        runCatching { notificationManager.notify(PROGRESS_NOTIFICATION_ID, notification) }
    }

    companion object {
        internal val connectedService = MutableStateFlow<CleanerAccessibilityService?>(null)
        private const val CHANNEL_ID = "cleaner_progress"
        private const val PROGRESS_NOTIFICATION_ID = 1001
    }
}
