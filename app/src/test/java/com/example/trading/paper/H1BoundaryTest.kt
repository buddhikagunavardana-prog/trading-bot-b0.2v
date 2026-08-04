package com.example.trading.paper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class H1BoundaryTest {

    @Test
    fun testH1FirstBoundaryRequirement() {
        val sessionStartMs = 1785251120000L // 2026-07-28T15:05:20Z
        val expectedFirstH1CloseMs = 1785254400000L // 2026-07-28T16:00:00Z

        val runtime25MinsMs = 1785252620000L // 15:30:20Z

        assertTrue(runtime25MinsMs < expectedFirstH1CloseMs)
        assertFalse(runtime25MinsMs >= expectedFirstH1CloseMs)
    }

    @Test
    fun testH1ExpectedCountAtRuntime() {
        val sessionStartMs = 1785251120000L
        val currentMs = 1785252620000L // 25 minutes elapsed

        val elapsedMinutes = (currentMs - sessionStartMs) / 60000L
        val expectedH1ClosesPerSymbol = if (elapsedMinutes < 60) 0 else (elapsedMinutes / 60).toInt()

        assertEquals(0, expectedH1ClosesPerSymbol)
    }
}
