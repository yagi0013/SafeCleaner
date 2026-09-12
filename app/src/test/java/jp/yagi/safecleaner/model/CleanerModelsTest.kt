package jp.yagi.safecleaner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanerModelsTest {
    @Test
    fun `mode capabilities are correct`() {
        assertTrue(CleanerMode.CLEAN_ALL.needsCache)
        assertTrue(CleanerMode.CLEAN_ALL.needsForceStop)
        assertFalse(CleanerMode.FORCE_STOP_ONLY.needsCache)
        assertFalse(CleanerMode.CLEAR_CACHE_ONLY.needsForceStop)
    }

    @Test
    fun `only stopped apps in force stop only mode skip settings navigation`() {
        assertTrue(shouldSkipForceStopBeforeOpeningSettings(CleanerMode.FORCE_STOP_ONLY, true))
        assertFalse(shouldSkipForceStopBeforeOpeningSettings(CleanerMode.FORCE_STOP_ONLY, false))
        assertFalse(shouldSkipForceStopBeforeOpeningSettings(CleanerMode.CLEAN_ALL, true))
        assertFalse(shouldSkipForceStopBeforeOpeningSettings(CleanerMode.CLEAR_CACHE_ONLY, true))
    }

    @Test
    fun `known cache below one MiB skips cache navigation`() {
        assertTrue(shouldSkipCacheBeforeOpeningSettings(CleanerMode.CLEAN_ALL, 0L))
        assertTrue(shouldSkipCacheBeforeOpeningSettings(CleanerMode.CLEAR_CACHE_ONLY, MIN_CACHE_BYTES_TO_CLEAR - 1L))
        assertFalse(shouldSkipCacheBeforeOpeningSettings(CleanerMode.CLEAR_CACHE_ONLY, null))
        assertFalse(shouldSkipCacheBeforeOpeningSettings(CleanerMode.CLEAR_CACHE_ONLY, MIN_CACHE_BYTES_TO_CLEAR))
        assertFalse(shouldSkipCacheBeforeOpeningSettings(CleanerMode.CLEAR_CACHE_ONLY, MIN_CACHE_BYTES_TO_CLEAR + 1L))
        assertFalse(shouldSkipCacheBeforeOpeningSettings(CleanerMode.FORCE_STOP_ONLY, 0L))
    }

    @Test
    fun `fixed target queue is detached from source and contains each package once`() {
        val first = InstalledApp("a", "A", null, isStopped = false, uid = 1)
        val duplicate = InstalledApp("a", "A duplicate", null, isStopped = true, uid = 1)
        val second = InstalledApp("b", "B", null, isStopped = false, uid = 2)
        val source = mutableListOf(first, duplicate, second)

        val snapshot = source.toFixedTargetQueue()
        source += InstalledApp("c", "C", null, isStopped = false, uid = 3)

        assertEquals(listOf("a", "b"), snapshot.map(InstalledApp::packageName))
        assertEquals("A", snapshot.first().label)
    }

    @Test
    fun `clean all combines force stop phase followed by cache phase`() {
        val apps = listOf(
            InstalledApp("a", "A", null, isStopped = false, uid = 1),
            InstalledApp("b", "B", null, isStopped = false, uid = 2),
        )
        val forceResults = listOf(
            AppOperationResult("a", "A", forceStopStatus = StepStatus.SUCCESS),
            AppOperationResult("b", "B", forceStopStatus = StepStatus.SKIPPED, failureReason = "already stopped"),
        )
        val cacheResults = listOf(
            AppOperationResult("a", "A", cacheStatus = StepStatus.SUCCESS),
            AppOperationResult("b", "B", cacheStatus = StepStatus.SKIPPED, failureReason = "below threshold"),
        )

        val combined = mergeCleanAllResults(apps, forceResults, cacheResults)

        assertEquals(StepStatus.SUCCESS, combined[0].forceStopStatus)
        assertEquals(StepStatus.SUCCESS, combined[0].cacheStatus)
        assertEquals(StepStatus.SKIPPED, combined[1].forceStopStatus)
        assertEquals(StepStatus.SKIPPED, combined[1].cacheStatus)
        assertEquals("already stopped / below threshold", combined[1].failureReason)
    }

    @Test
    fun `summary counts an app as successful when either requested operation succeeds`() {
        val summary = RunSummary(
            mode = CleanerMode.CLEAN_ALL,
            startedAtMillis = 0,
            finishedAtMillis = 1,
            targetCount = 3,
            automaticExcludedCount = 2,
            userExcludedCount = 1,
            results = listOf(
                AppOperationResult("a", "A", StepStatus.SUCCESS, StepStatus.SUCCESS),
                AppOperationResult("b", "B", StepStatus.SKIPPED, StepStatus.SUCCESS, "cache skipped"),
                AppOperationResult("c", "C", StepStatus.FAILED, StepStatus.SUCCESS, "cache failed"),
            ),
            availableRamBefore = 1,
            availableRamAfter = 2,
        )
        assertEquals(3, summary.successCount)
        assertEquals(0, summary.skippedCount)
        assertEquals(0, summary.failedCount)
    }

    @Test
    fun `result categories are mutually exclusive and success takes priority`() {
        val success = AppOperationResult("a", "A", StepStatus.SUCCESS, StepStatus.SUCCESS)
        val successAndSkipped = AppOperationResult("b", "B", StepStatus.SKIPPED, StepStatus.SUCCESS)
        val successAndFailed = AppOperationResult("c", "C", StepStatus.FAILED, StepStatus.SUCCESS)
        val failedAndSkipped = AppOperationResult("c", "C", StepStatus.FAILED, StepStatus.SKIPPED)

        assertEquals(AppResultCategory.SUCCESS, success.category)
        assertEquals(AppResultCategory.SUCCESS, successAndSkipped.category)
        assertEquals(AppResultCategory.SUCCESS, successAndFailed.category)
        assertEquals(AppResultCategory.FAILED, failedAndSkipped.category)
    }
}
