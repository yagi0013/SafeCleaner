package jp.yagi.safecleaner.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import jp.yagi.safecleaner.model.StepStatus
import jp.yagi.safecleaner.util.AppLog

data class StepResult(val status: StepStatus, val reason: String? = null)

class ForceStopHandler(
    private val service: CleanerAccessibilityService,
    private val navigator: SettingsNavigator,
    private val resolver: OemUiResolver,
) {
    private sealed interface PostClickState {
        data object Confirmation : PostClickState
        data object Stopped : PostClickState
    }

    suspend fun execute(packageName: String, onConfirming: () -> Unit): StepResult {
        val alreadyStopped = navigator.isPackageStopped(packageName)
        AppLog.i("package=$packageName operation=force_stop stage=precheck stopped=$alreadyStopped")
        if (alreadyStopped) return skipped(packageName, "強制停止：停止済みを確認しました", "precheck")
        val diagnostics = ButtonDiagnostics(packageName, "force_stop")
        val candidate = service.awaitRoot(5_000) { root ->
            diagnostics.enabledCandidate(navigator.isTargetAppInfo(root), navigator.findForceStop(root))
        } ?: return skipped(
            packageName, buttonUnavailableReason("強制停止", diagnostics.state),
        )
        val button = NodeFinder.clickable(candidate) ?: return skipped(packageName, "強制停止：検出後、クリック可能なノードを確認できません")
        if (!button.isEnabled) return skipped(packageName, "強制停止：検出後、ボタンが無効になりました")

        AppLog.d("package=$packageName button=force_stop id=${candidate.viewIdResourceName} text=${candidate.text}")
        val clicked = service.awaitRoot(2_000) { root ->
            val freshButton = navigator.findForceStop(root)?.let(NodeFinder::clickable)
                ?.takeIf { it.isEnabled } ?: return@awaitRoot null
            true.takeIf { freshButton.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        } == true
        AppLog.i("package=$packageName operation=force_stop stage=click accepted=$clicked")
        if (!clicked) {
            return recordStep(packageName, "force_stop", "click", StepResult(StepStatus.FAILED, "強制停止：クリック受付を確認できません"))
        }
        onConfirming()

        // A disabled node alone may be stale. Verify the requested package's actual stopped flag.
        val postClickState = service.awaitRoot(5_000) { root ->
            when {
                findSafeConfirmation(root) != null -> PostClickState.Confirmation
                navigator.isPackageStopped(packageName) -> PostClickState.Stopped
                else -> null
            }
        }

        AppLog.i("package=$packageName operation=force_stop stage=post_click state=${postClickState ?: "TIMEOUT"}")
        when (postClickState) {
            PostClickState.Confirmation -> {
                // Re-resolve on every attempt because OEM Settings often replaces dialog nodes
                // between the accessibility event and the click.
                val confirmed = service.awaitRoot(3_000) { root ->
                    val confirmButton = findSafeConfirmation(root)?.let(NodeFinder::clickable)
                        ?.takeIf { it.isEnabled } ?: return@awaitRoot null
                    true.takeIf { confirmButton.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                } == true
                AppLog.i("package=$packageName operation=force_stop stage=confirmation accepted=$confirmed")
                if (!confirmed) {
                    return recordStep(packageName, "force_stop", "confirmation", StepResult(StepStatus.FAILED, "強制停止：確認ダイアログのクリック受付を確認できません"))
                }
            }
            PostClickState.Stopped -> Unit
            null -> {
                return recordStep(packageName, "force_stop", "post_click", StepResult(StepStatus.FAILED, "強制停止：確認ダイアログも停止完了も確認できません"))
            }
        }

        val stopped = service.awaitRoot(7_000) {
            Unit.takeIf { navigator.isPackageStopped(packageName) }
        } != null
        AppLog.i("package=$packageName operation=force_stop stage=completion stopped=$stopped")
        if (!stopped) return recordStep(packageName, "force_stop", "completion", StepResult(StepStatus.FAILED, "強制停止：クリック受付後、対象アプリの停止完了を確認できません"))
        AppLog.i("package=$packageName force_stop=SUCCESS")
        return StepResult(StepStatus.SUCCESS)
    }

    private fun findSafeConfirmation(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (!navigator.isSettingsRoot(root)) return null
        if (NodeFinder.containsAnyLabel(root, resolver.destructiveDataLabels)) return null
        val standardPositive = NodeFinder.findByResourceSuffix(root, setOf("button1"))
        val hasStandardButtonPair = standardPositive != null &&
            NodeFinder.findByResourceSuffix(root, setOf("button2")) != null
        if (hasStandardButtonPair) return standardPositive
        if (!NodeFinder.containsAnyLabel(root, resolver.forceStopDialogLabels)) return null
        return NodeFinder.findByExactLabel(root, resolver.confirmForceStopLabels)
    }

    private fun skipped(packageName: String, reason: String, stage: String = "button"): StepResult {
        return recordStep(packageName, "force_stop", stage, StepResult(StepStatus.SKIPPED, reason))
    }
}
