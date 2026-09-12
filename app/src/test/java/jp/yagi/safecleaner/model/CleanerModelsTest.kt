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
    fun `only confirmed zero cache can skip cache navigation`() {
        assertTrue(shouldSkipCacheBeforeOpeningSettings(CleanerMode.CLEAN_ALL, 0L))
        assertTrue(shouldSkipCacheBeforeOpeningSettings(CleanerMode.CLEAR_CACHE_ONLY, 0L))
        assertFalse(shouldSkipCacheBeforeOpeningSettings(CleanerMode.CLEAR_CACHE_ONLY, null))
        assertFalse(shouldSkipCacheBeforeOpeningSettings(CleanerMode.CLEAR_CACHE_ONLY, 1L))
        assertFalse(shouldSkipCacheBeforeOpeningSettings(CleanerMode.FORCE_STOP_ONLY, 0L))
    }

    @Test
    fun `clean all skips every setting screen only when stopped and cache is zero`() {
        assertTrue(shouldSkipAllBeforeOpeningSettings(CleanerMode.CLEAN_ALL, true, 0L))
        assertFalse(shouldSkipAllBeforeOpeningSettings(CleanerMode.CLEAN_ALL, false, 0L))
        assertFalse(shouldSkipAllBeforeOpeningSettings(CleanerMode.CLEAN_ALL, true, null))
        assertFalse(shouldSkipAllBeforeOpeningSettings(CleanerMode.CLEAN_ALL, true, 10L))
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
    fun `summary separates skipped and failed`() {
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
        assertEquals(1, summary.successCount)
        assertEquals(1, summary.skippedCount)
        assertEquals(1, summary.failedCount)
    }
}
