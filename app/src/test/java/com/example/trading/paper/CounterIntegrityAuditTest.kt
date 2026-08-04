package com.example.trading.paper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

data class AuditedCandleEvent(
    val symbol: String,
    val timeframe: String,
    val closeTimestamp: Long,
    val origin: EventOrigin,
    val sessionStartTime: Long
) {
    fun isEligibleForLivePerformance(): Boolean {
        return origin == EventOrigin.LIVE_STREAM && closeTimestamp >= sessionStartTime
    }
}

class CounterIntegrityAuditTest {

    @Test
    fun testEventOriginClassificationAndEligibility() {
        val sessionStart = 1785251120000L

        val bootstrapEvent = AuditedCandleEvent("BTC/USDT", "M5", 1785250800000L, EventOrigin.REST_BOOTSTRAP, sessionStart)
        val warmupEvent = AuditedCandleEvent("BTC/USDT", "M5", 1785250800000L, EventOrigin.WARMUP, sessionStart)
        val syntheticEvent = AuditedCandleEvent("BTC/USDT", "M5", 1785251400000L, EventOrigin.SYNTHETIC_TEST, sessionStart)
        val liveEvent = AuditedCandleEvent("BTC/USDT", "M5", 1785251400000L, EventOrigin.LIVE_STREAM, sessionStart)

        assertFalse(bootstrapEvent.isEligibleForLivePerformance())
        assertFalse(warmupEvent.isEligibleForLivePerformance())
        assertFalse(syntheticEvent.isEligibleForLivePerformance())
        assertTrue(liveEvent.isEligibleForLivePerformance())
    }

    @Test
    fun testCounterSeparation() {
        val events = listOf(
            AuditedCandleEvent("BTC/USDT", "M5", 1785250800000L, EventOrigin.REST_BOOTSTRAP, 1785251120000L),
            AuditedCandleEvent("BTC/USDT", "M5", 1785250800000L, EventOrigin.WARMUP, 1785251120000L),
            AuditedCandleEvent("BTC/USDT", "M5", 1785251400000L, EventOrigin.LIVE_STREAM, 1785251120000L),
            AuditedCandleEvent("ETH/USDT", "M5", 1785251400000L, EventOrigin.LIVE_STREAM, 1785251120000L)
        )

        val bootstrapCount = events.count { it.origin == EventOrigin.REST_BOOTSTRAP }
        val warmupCount = events.count { it.origin == EventOrigin.WARMUP }
        val genuineLiveCount = events.count { it.isEligibleForLivePerformance() }

        assertEquals(1, bootstrapCount)
        assertEquals(1, warmupCount)
        assertEquals(2, genuineLiveCount)
    }
}
