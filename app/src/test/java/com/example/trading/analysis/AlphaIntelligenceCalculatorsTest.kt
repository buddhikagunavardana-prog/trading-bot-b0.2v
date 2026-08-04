package com.example.trading.analysis

import com.example.model.CryptoTicker
import com.example.trading.history.ClosedTradeResult
import com.example.trading.history.PositionCloseReason
import com.example.trading.history.TradeDirection
import com.example.trading.history.TradeResultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphaIntelligenceCalculatorsTest {

    @Test
    fun testConfidenceCalculator_WhenDataNotReady_ReturnsInsufficientEvidence() {
        val score = AlphaOpportunityScore(
            symbol = "BTC/USDT",
            score = 78.5,
            direction = OpportunityDirection.LONG,
            eligibility = OpportunityEligibility.ELIGIBLE,
            marketRegime = MarketRegime.STRONG_BULL_TREND
        )
        val readinessGate = MarketReadinessGate(
            bootstrapComplete = false,
            warmupComplete = false,
            snapshotComplete = false,
            dataFresh = false,
            websocketConnected = false
        )

        val confidence = AlphaConfidenceCalculator.calculateConfidence(
            score = score,
            ticker = null,
            mtfSnapshot = null,
            readinessGate = readinessGate,
            historicalSampleSize = 0
        )

        assertNull(confidence.confidencePercent)
        assertEquals(CalibrationStatus.INSUFFICIENT_EVIDENCE, confidence.calibrationStatus)
        assertEquals("UNAVAILABLE — insufficient calibrated evidence", confidence.unavailableReason)
    }

    @Test
    fun testConfidenceCalculator_WhenDataReady_ReturnsCalibratedValue() {
        val score = AlphaOpportunityScore(
            symbol = "BTC/USDT",
            score = 82.0,
            direction = OpportunityDirection.LONG,
            eligibility = OpportunityEligibility.ELIGIBLE,
            marketRegime = MarketRegime.STRONG_BULL_TREND,
            componentBreakdown = ScoreBreakdown(
                trendScore = 15.0,
                momentumScore = 15.0,
                structureScore = 15.0,
                freshnessScore = 5.0
            )
        )
        val ticker = CryptoTicker(
            symbol = "BTC/USDT",
            name = "Bitcoin",
            price = 65000.0,
            change24h = 2.5,
            high24h = 66000.0,
            low24h = 64000.0,
            volume = 5000000.0,
            rsi = 55.0,
            aiScore = 80
        )
        val mtf = MultiTimeframeSnapshot(
            symbol = "BTC/USDT",
            m5 = null,
            m15 = null,
            h1 = null
        )
        val readinessGate = MarketReadinessGate(
            bootstrapComplete = true,
            warmupComplete = true,
            snapshotComplete = true,
            dataFresh = true,
            websocketConnected = true
        )

        val confidence = AlphaConfidenceCalculator.calculateConfidence(
            score = score,
            ticker = ticker,
            mtfSnapshot = mtf,
            readinessGate = readinessGate,
            historicalSampleSize = 10
        )

        assertNotNull(confidence.confidencePercent)
        assertTrue(confidence.confidencePercent!! > 50.0)
        assertEquals(CalibrationStatus.CALIBRATED, confidence.calibrationStatus)
        assertNull(confidence.unavailableReason)
    }

    @Test
    fun testTradePlanCalculator_LongAndShortValidations() {
        // LONG Setup: stopLoss < entry < takeProfit
        val longPlan = AlphaTradePlanCalculator.calculateTradePlan(
            symbol = "BTC/USDT",
            direction = OpportunityDirection.LONG,
            price = 60000.0,
            executionDecision = null,
            accountEquity = 10000.0
        )

        assertEquals(TradePlanCalculationStatus.CALCULATED, longPlan.calculationStatus)
        assertEquals(60000.0, longPlan.entryPrice!!, 0.001)
        assertTrue(longPlan.stopLossPrice!! < longPlan.entryPrice!!)
        assertTrue(longPlan.takeProfitPrice!! > longPlan.entryPrice!!)
        assertTrue(longPlan.riskRewardRatio!! > 2.0)
        assertEquals("Calculated Size — Not Authorized", longPlan.authorizationStatusLabel)

        // SHORT Setup: takeProfit < entry < stopLoss
        val shortPlan = AlphaTradePlanCalculator.calculateTradePlan(
            symbol = "BTC/USDT",
            direction = OpportunityDirection.SHORT,
            price = 60000.0,
            executionDecision = null,
            accountEquity = 10000.0
        )

        assertEquals(TradePlanCalculationStatus.CALCULATED, shortPlan.calculationStatus)
        assertTrue(shortPlan.stopLossPrice!! > shortPlan.entryPrice!!)
        assertTrue(shortPlan.takeProfitPrice!! < shortPlan.entryPrice!!)
        assertTrue(shortPlan.riskRewardRatio!! > 2.0)
    }

    @Test
    fun testHistoricalPerformanceCalculator_InsufficientSample_ReturnsInvalidWithReason() {
        val result = HistoricalPerformanceCalculator.calculateHistoricalEvidence(
            strategyId = "TREND_PULLBACK",
            marketRegime = "STRONG_BULL_TREND",
            symbol = "BTC/USDT",
            completedTrades = emptyList(),
            minRequiredSample = 5
        )

        assertFalse(result.valid)
        assertEquals("INSUFFICIENT SAMPLE — n=0", result.unavailableReason)
        assertNull(result.winRatePercent)
    }

    @Test
    fun testHistoricalPerformanceCalculator_SufficientSample_ReturnsCalculatedMetrics() {
        val trades = (1..6).map { i ->
            ClosedTradeResult(
                tradeId = "T_$i",
                positionId = "P_$i",
                sessionId = "S_1",
                symbol = "BTC/USDT",
                direction = TradeDirection.LONG,
                openedAtEpochMs = System.currentTimeMillis() - 100000L,
                closedAtEpochMs = System.currentTimeMillis(),
                holdingDurationMs = 100000L,
                entryPrice = 60000.0,
                exitPrice = 61000.0,
                quantity = 0.1,
                entryNotionalUsdt = 6000.0,
                allocatedCapitalUsdt = 1000.0,
                grossPnlUsdt = if (i <= 4) 100.0 else -50.0,
                netPnlUsdt = if (i <= 4) 100.0 else -50.0,
                pnlPercentOnNotional = 1.5,
                pnlPercentOnAllocatedCapital = 10.0,
                resultType = if (i <= 4) TradeResultType.PROFIT else TradeResultType.LOSS,
                closeReason = PositionCloseReason.TAKE_PROFIT,
                strategyId = "TREND_PULLBACK"
            )
        }

        val result = HistoricalPerformanceCalculator.calculateHistoricalEvidence(
            strategyId = "TREND_PULLBACK",
            marketRegime = "STRONG_BULL_TREND",
            symbol = "BTC/USDT",
            completedTrades = trades,
            minRequiredSample = 5
        )

        assertTrue(result.valid)
        assertNull(result.unavailableReason)
        assertEquals(6, result.sampleSize)
        assertEquals(66.7, result.winRatePercent!!, 0.5)
        assertTrue(result.profitFactor!! > 1.0)
    }

    @Test
    fun testMarketPressureCalculator_GeneratesAuditedSnapshot() {
        val ticker = CryptoTicker(
            symbol = "BTC/USDT",
            name = "Bitcoin",
            price = 60000.0,
            change24h = 2.0,
            high24h = 61000.0,
            low24h = 59000.0,
            volume = 5000000.0,
            rsi = 55.0,
            aiScore = 80
        )
        val pressure = MarketPressureCalculator.calculateMarketPressure(
            provider = "OKX_SWAP_PUBLIC",
            symbol = "BTC/USDT",
            ticker = ticker,
            mtfSnapshot = null
        )

        assertEquals("OKX_SWAP_PUBLIC", pressure.provider)
        assertNotNull(pressure.bidPercent)
        assertNotNull(pressure.askPercent)
        assertEquals("Executed Buy/Sell Flow", pressure.dataOrigin)
        assertTrue(pressure.unavailableReason!!.contains("UNAVAILABLE — Active provider candle endpoint"))
    }

    @Test
    fun testReasonSummaryBuilder_GeneratesExactBlockerForBelowThresholdScore() {
        val score = AlphaOpportunityScore(
            symbol = "ETH/USDT",
            score = 72.0,
            direction = OpportunityDirection.LONG,
            eligibility = OpportunityEligibility.INELIGIBLE_BELOW_THRESHOLD,
            marketRegime = MarketRegime.RANGE,
            componentBreakdown = ScoreBreakdown(
                trendScore = 10.0,
                momentumScore = 10.0,
                structureScore = 10.0
            )
        )

        val summary = AlphaReasonSummaryBuilder.buildReasonSummary(score, null)

        assertTrue(summary.executionBlockers.any { it.contains("Alpha Score 72.0 is below execution threshold 75.0") })
        assertTrue(summary.executionBlockers.any { it.contains("INELIGIBLE_BELOW_THRESHOLD") })
    }
}
