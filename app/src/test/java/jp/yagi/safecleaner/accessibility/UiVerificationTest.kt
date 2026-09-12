package jp.yagi.safecleaner.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import jp.yagi.safecleaner.model.StepStatus

class UiVerificationTest {
    @Test
    fun `each unavailable button state has a distinct explanation`() {
        val reasons = ButtonAvailability.entries.map { buttonUnavailableReason("強制停止", it) }
        assertEquals(ButtonAvailability.entries.size, reasons.toSet().size)
        assertTrue(buttonUnavailableReason("強制停止", ButtonAvailability.DISABLED).contains("無効"))
        assertFalse(buttonUnavailableReason("強制停止", ButtonAvailability.DISABLED).contains("見つかりません"))
    }

    @Test
    fun `return failure never overrides verified cache deletion`() {
        val result = cacheOperationResult(clicked = true, verified = true, returned = false)
        assertEquals(StepStatus.SUCCESS, result.status)
        assertTrue(result.reason.orEmpty().contains("画面復帰"))
    }

    @Test
    fun `click completion and return outcomes remain independent`() {
        assertEquals(StepStatus.SUCCESS, cacheOperationResult(true, true, true).status)
        assertNull(cacheOperationResult(true, true, true).reason)
        val unconfirmed = cacheOperationResult(true, false, false)
        assertEquals(StepStatus.FAILED, unconfirmed.status)
        assertTrue(unconfirmed.reason.orEmpty().contains("処理完了"))
        assertTrue(unconfirmed.reason.orEmpty().contains("画面復帰"))
        assertTrue(cacheOperationResult(false, false, true).reason.orEmpty().contains("クリック受付"))
    }
    @Test
    fun `every incomplete readiness combination stays null rather than returning false`() {
        for (bits in 0 until 16) {
            val ready = appInfoReady(bits and 1 != 0, bits and 2 != 0, bits and 4 != 0, bits and 8 != 0)
            if (bits == 15) assertEquals(Unit, ready) else assertNull("combination=$bits", ready)
        }
    }

    @Test
    fun `old settings event or previous app cannot finish app info wait`() {
        assertNull(appInfoReady(false, true, true, true))
        assertNull(appInfoReady(true, true, false, true))
        assertNull(appInfoReady(true, false, true, true))
        assertNull(appInfoReady(true, true, true, false))
    }

    @Test
    fun `cache success requires a real reduction zero or a disabled button`() {
        assertTrue(cacheClearVerified(100L, 0L, false))
        assertTrue(cacheClearVerified(100L, 20L, false))
        assertTrue(cacheClearVerified(null, 0L, false))
        assertTrue(cacheClearVerified(null, null, true))
        assertFalse(cacheClearVerified(100L, 100L, false))
        assertFalse(cacheClearVerified(100L, 120L, false))
        assertFalse(cacheClearVerified(null, 20L, false))
        assertFalse(cacheClearVerified(100L, null, false))
        assertFalse(cacheClearVerified(100L, -1L, false))
    }
}
