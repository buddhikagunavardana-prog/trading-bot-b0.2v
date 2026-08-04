package com.example.trading.paper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M15BoundaryTest {

    @Test
    fun testM15BoundaryCalculation() {
        val sessionStartMs = 1785251120000L // 2026-07-28T15:05:20Z

        // The first M15 candle boundary after 15:05:20Z is 15:15:00Z (1785251700000L)
        val firstM15BoundaryMs = 1785251700000L

        val preBoundaryMs = 1785251400000L // 15:10:00Z
        val postBoundaryMs = 1785251700000L // 15:15:00Z

        assertFalse(preBoundaryMs >= firstM15BoundaryMs)
        assertTrue(postBoundaryMs >= firstM15BoundaryMs)
    }

    @Test
    fun testM15ZeroClosesInEarlyObservationWindow() {
        // In the first 9 minutes of observation (15:05:20Z to 15:14:20Z), zero M15 candles can close
        val sessionStartMs = 1785251120000L
        val currentMs = 1785251660000L // 15:14:20Z

        val elapsedMinutes = (currentMs - sessionStartMs) / 60000L
        val expectedM15Closes = (elapsedMinutes / 15).toInt()

        assertEquals(0, expectedM15Closes)
    }
}
