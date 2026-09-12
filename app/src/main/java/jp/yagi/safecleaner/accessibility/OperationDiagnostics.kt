package jp.yagi.safecleaner.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import jp.yagi.safecleaner.model.StepStatus
import jp.yagi.safecleaner.util.AppLog

internal class ButtonDiagnostics(private val packageName: String, private val operation: String) {
    var state = ButtonAvailability.SCREEN_UNCONFIRMED
        private set
    private var loggedState: ButtonAvailability? = null

    fun enabledCandidate(screenConfirmed: Boolean, candidate: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        val button = NodeFinder.clickable(candidate)
        state = when {
            !screenConfirmed -> ButtonAvailability.SCREEN_UNCONFIRMED
            candidate == null -> ButtonAvailability.NOT_FOUND
            button == null -> ButtonAvailability.NOT_CLICKABLE
            !button.isEnabled -> ButtonAvailability.DISABLED
            else -> ButtonAvailability.ENABLED
        }
        if (state != loggedState) {
            AppLog.i("package=$packageName operation=$operation stage=button state=$state id=${candidate?.viewIdResourceName}")
            loggedState = state
        }
        return candidate.takeIf { state == ButtonAvailability.ENABLED }
    }
}

internal fun recordStep(packageName: String, operation: String, stage: String, result: StepResult): StepResult {
    val message = "package=$packageName operation=$operation stage=$stage status=${result.status} reason=${result.reason ?: "none"}"
    if (result.status == StepStatus.SUCCESS) AppLog.i(message) else AppLog.w(message)
    return result
}
