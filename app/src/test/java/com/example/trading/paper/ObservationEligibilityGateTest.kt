package com.example.trading.paper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationEligibilityGateTest {

    private val sessionStartMs = 1785251120000L // 2026-07-28T15:05:20Z
    private val defaultContext = SessionEligibilityContext(
        sessionId = "SESS_LIVE_PAPER_20260728_150520_UTC",
        sessionStartEpoch = sessionStartMs,
        tradingMode = "PAPER"
    )

    @Test
    fun testEligibleLiveEvent() {
        val eventEpoch = 1785251400000L
        val result = ObservationEligibilityGate.isObservationEligible(
            symbol = "BTC/USDT",
            timeframe = "M5",
            eventOrigin = EventOrigin.LIVE_STREAM,
            eventEpoch = eventEpoch,
            candleOpenTime = 1785251100000L,
            candleCloseTime = eventEpoch,
            isClosed = true,
            candleId = "BTC_M5_1785251400000",
            context = defaultContext,
            currentTimeEpochMs = eventEpoch + 1000
        )

        assertTrue(result.eligible)
        assertEquals("ELIGIBLE_LIVE_EVENT", result.reasonCode)
    }

    @Test
    fun testPreSessionEventRejection() {
        val eventEpoch = 1785250800000L
        val result = ObservationEligibilityGate.isObservationEligible(
            symbol = "BTC/USDT",
            timeframe = "M5",
            eventOrigin = EventOrigin.LIVE_STREAM,
            eventEpoch = eventEpoch,
            candleOpenTime = 1785250500000L,
            candleCloseTime = eventEpoch,
            isClosed = true,
            candleId = "BTC_M5_1785250800000",
            context = defaultContext,
            currentTimeEpochMs = eventEpoch + 1000
        )

        assertFalse(result.eligible)
        assertEquals("REJECTED_PRE_SESSION_EVENT", result.reasonCode)
    }

    @Test
    fun testWarmupOriginRejection() {
        val eventEpoch = 1785251400000L
        val result = ObservationEligibilityGate.isObservationEligible(
            symbol = "BTC/USDT",
            timeframe = "M5",
            eventOrigin = EventOrigin.WARMUP,
            eventEpoch = eventEpoch,
            candleOpenTime = 1785251100000L,
            candleCloseTime = eventEpoch,
            isClosed = true,
            candleId = "BTC_M5_1785251400000",
            context = defaultContext,
            currentTimeEpochMs = eventEpoch + 1000
        )

        assertFalse(result.eligible)
        assertEquals("REJECTED_NON_LIVE_ORIGIN_WARMUP", result.reasonCode)
    }

    @Test
    fun testDuplicateCandleRejection() {
        val eventEpoch = 1785251400000L
        val contextWithProcessed = defaultContext.copy(processedCandleIds = setOf("BTC_M5_1785251400000"))

        val result = ObservationEligibilityGate.isObservationEligible(
            symbol = "BTC/USDT",
            timeframe = "M5",
            eventOrigin = EventOrigin.LIVE_STREAM,
            eventEpoch = eventEpoch,
            candleOpenTime = 1785251100000L,
            candleCloseTime = eventEpoch,
            isClosed = true,
            candleId = "BTC_M5_1785251400000",
            context = contextWithProcessed,
            currentTimeEpochMs = eventEpoch + 1000
        )

        assertFalse(result.eligible)
        assertEquals("REJECTED_DUPLICATE_CANDLE", result.reasonCode)
        assertTrue(result.isDuplicate)
    }
}
