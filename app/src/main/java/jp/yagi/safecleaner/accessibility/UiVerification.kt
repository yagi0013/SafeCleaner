package jp.yagi.safecleaner.accessibility

import jp.yagi.safecleaner.model.StepStatus

internal enum class ButtonAvailability { SCREEN_UNCONFIRMED, NOT_FOUND, NOT_CLICKABLE, DISABLED, ENABLED }

internal fun buttonUnavailableReason(operation: String, state: ButtonAvailability): String = "$operation：" + when (state) {
    ButtonAvailability.SCREEN_UNCONFIRMED -> "対象画面を確認できません"
    ButtonAvailability.NOT_FOUND -> "ボタンが見つかりません"
    ButtonAvailability.NOT_CLICKABLE -> "ボタンは見つかりましたが、安全にクリックできません"
    ButtonAvailability.DISABLED -> "ボタンは見つかりましたが、無効です"
    ButtonAvailability.ENABLED -> "有効なボタンを確認しました"
}

internal fun cacheOperationResult(clicked: Boolean, verified: Boolean, returned: Boolean): StepResult {
    val status = if (clicked && verified) StepStatus.SUCCESS else StepStatus.FAILED
    val reason = buildList {
        if (!clicked) add("キャッシュ削除：クリック受付を確認できません")
        else if (!verified) add("キャッシュ削除：処理完了を確認できません")
        if (!returned) add("画面復帰：アプリ情報画面を確認できません（削除結果とは別判定）")
    }.takeIf { it.isNotEmpty() }?.joinToString(" / ")
    return StepResult(status, reason)
}

/** A false predicate must remain null so awaitRoot keeps waiting. */
internal fun appInfoReady(
    freshSettingsEvent: Boolean,
    trustedRoot: Boolean,
    matchesTarget: Boolean,
    hasControls: Boolean,
): Unit? = Unit.takeIf { freshSettingsEvent && trustedRoot && matchesTarget && hasControls }

internal fun cacheClearVerified(beforeBytes: Long?, afterBytes: Long?, buttonDisabled: Boolean): Boolean =
    buttonDisabled || (afterBytes != null && afterBytes >= 0 &&
        (afterBytes == 0L || (beforeBytes != null && beforeBytes > 0 && afterBytes < beforeBytes)))

/**
 * Some OEM PackageManagers expose FLAG_STOPPED late (or not at all) even though Settings has
 * completed the operation. A post-click, freshly updated target screen with a disabled force-stop
 * button is an equally specific completion signal. All UI conditions are required together so a
 * stale screen or a different app can never satisfy verification.
 */
internal fun forceStopVerified(
    packageStopped: Boolean,
    freshSettingsEvent: Boolean,
    targetAppInfo: Boolean,
    buttonDisabled: Boolean,
): Boolean = packageStopped || (freshSettingsEvent && targetAppInfo && buttonDisabled)
