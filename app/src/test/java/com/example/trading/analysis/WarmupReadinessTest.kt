package com.example.trading.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WarmupReadinessTest {

    private lateinit var tracker: WarmupReadinessTracker

    @Before
    fun setUp() {
        tracker = WarmupReadinessTracker()
    }

    @Test
    fun testWarmupReadinessProgression() {
        val symbol = "BTC/USDT"

        // Initially zero
        tracker.updateCounts(symbol, 5, 2, 1)
        assertFalse(tracker.isSymbolReady(symbol))

        // Fully warm (requires 250 candles per timeframe)
        tracker.updateCounts(symbol, 250, 250, 250)
        assertTrue(tracker.isSymbolReady(symbol))

        val pct = tracker.getOverallWarmupPercentage(listOf(symbol))
        assertEquals(100, pct)
    }
}
