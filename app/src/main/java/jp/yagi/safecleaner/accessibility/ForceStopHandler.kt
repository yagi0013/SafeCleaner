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
        data object Disabled : PostClickState
    }

    suspend fun execute(packageName: String, onConfirming: () -> Unit): StepResult {
        val candidate = service.awaitRoot(5_000) { navigator.findForceStop(it) } ?: return skipped(
            packageName, "強制停止ボタンが見つかりません",
        )
        val button = NodeFinder.clickable(candidate) ?: return skipped(packageName, "強制停止ボタンを安全に押せません")
        if (!button.isEnabled) return StepResult(StepStatus.SKIPPED, "強制停止済み、またはボタンが無効です")

        AppLog.d("package=$packageName button=force_stop id=${candidate.viewIdResourceName} text=${candidate.text}")
        val clicked = service.awaitRoot(2_000) { root ->
            val freshButton = navigator.findForceStop(root)?.let(NodeFinder::clickable)
                ?.takeIf { it.isEnabled } ?: return@awaitRoot null
            true.takeIf { freshButton.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        } == true
        if (!clicked) {
            return StepResult(StepStatus.FAILED, "強制停止ボタンの操作に失敗しました")
        }
        onConfirming()

        // Wait for either outcome in one event-driven loop. OEMs which stop immediately no longer
        // pay the full confirmation-dialog timeout before we inspect the disabled button.
        val postClickState = service.awaitRoot(5_000) { root ->
            when {
                findSafeConfirmation(root) != null -> PostClickState.Confirmation
                navigator.findForceStop(root)?.let(NodeFinder::clickable)?.isEnabled == false ->
                    PostClickState.Disabled
                else -> null
            }
        }

        when (postClickState) {
            PostClickState.Confirmation -> {
                // Re-resolve on every attempt because OEM Settings often replaces dialog nodes
                // between the accessibility event and the click.
                val confirmed = service.awaitRoot(3_000) { root ->
                    val confirmButton = findSafeConfirmation(root)?.let(NodeFinder::clickable)
                        ?.takeIf { it.isEnabled } ?: return@awaitRoot null
                    true.takeIf { confirmButton.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                } == true
                if (!confirmed) {
                    return StepResult(StepStatus.FAILED, "強制停止の確認に失敗しました")
                }
            }
            PostClickState.Disabled -> Unit
            null -> {
                return StepResult(StepStatus.FAILED, "強制停止の完了を確認できません")
            }
        }

        service.awaitEventOrTimeout(400)
        AppLog.i("package=$packageName force_stop=SUCCESS")
        return StepResult(StepStatus.SUCCESS)
    }

    private fun findSafeConfirmation(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (NodeFinder.containsAnyLabel(root, resolver.destructiveDataLabels)) return null
        val standardPositive = NodeFinder.findByResourceSuffix(root, setOf("button1"))
        val hasStandardButtonPair = standardPositive != null &&
            NodeFinder.findByResourceSuffix(root, setOf("button2")) != null
        if (hasStandardButtonPair) return standardPositive
        if (!NodeFinder.containsAnyLabel(root, resolver.forceStopDialogLabels)) return null
        return NodeFinder.findByExactLabel(root, resolver.confirmForceStopLabels)
    }

    private fun skipped(packageName: String, reason: String): StepResult {
        AppLog.w("package=$packageName force_stop=SKIP reason=$reason")
        return StepResult(StepStatus.SKIPPED, reason)
    }
}
