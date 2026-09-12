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
        data class Confirmation(val node: AccessibilityNodeInfo) : PostClickState
        data object Disabled : PostClickState
    }

    suspend fun execute(packageName: String, onConfirming: () -> Unit): StepResult {
        val candidate = service.awaitRoot(3_000) { navigator.findForceStop(it) } ?: return skipped(
            packageName, "強制停止ボタンが見つかりません",
        )
        val button = NodeFinder.clickable(candidate) ?: return skipped(packageName, "強制停止ボタンを安全に押せません")
        if (!button.isEnabled) return StepResult(StepStatus.SKIPPED, "強制停止済み、またはボタンが無効です")

        AppLog.d("package=$packageName button=force_stop id=${candidate.viewIdResourceName} text=${candidate.text}")
        if (!button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return StepResult(StepStatus.FAILED, "強制停止ボタンの操作に失敗しました")
        }
        onConfirming()

        // Wait for either outcome in one event-driven loop. OEMs which stop immediately no longer
        // pay the full confirmation-dialog timeout before we inspect the disabled button.
        val postClickState = service.awaitRoot(2_500) { root ->
            val positive = NodeFinder.findByResourceSuffix(root, setOf("button1"))
                ?: NodeFinder.findByExactLabel(root, resolver.confirmForceStopLabels)
            val hasForceContext = NodeFinder.containsAnyLabel(root, resolver.forceStopLabels) ||
                NodeFinder.containsAnyLabel(root, setOf("アプリを強制停止しますか？", "Force stop app?"))
            val hasStandardDialogButtons =
                NodeFinder.findByResourceSuffix(root, setOf("button1")) != null &&
                    NodeFinder.findByResourceSuffix(root, setOf("button2")) != null
            val containsDestructiveDataAction = NodeFinder.containsAnyLabel(root, resolver.destructiveDataLabels)
            when {
                positive != null && (hasForceContext || hasStandardDialogButtons) &&
                    !containsDestructiveDataAction -> PostClickState.Confirmation(positive)
                navigator.findForceStop(root)?.let(NodeFinder::clickable)?.isEnabled == false ->
                    PostClickState.Disabled
                else -> null
            }
        }

        when (postClickState) {
            is PostClickState.Confirmation -> {
                val confirmButton = NodeFinder.clickable(postClickState.node)
                    ?: return StepResult(StepStatus.FAILED, "確認ダイアログを安全に操作できません")
                if (!confirmButton.isEnabled || !confirmButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
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

    private fun skipped(packageName: String, reason: String): StepResult {
        AppLog.w("package=$packageName force_stop=SKIP reason=$reason")
        return StepResult(StepStatus.SKIPPED, reason)
    }
}
