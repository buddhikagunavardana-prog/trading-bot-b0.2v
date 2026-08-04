package com.example.trading

import com.example.trading.analysis.Candle
import com.example.trading.analysis.IndicatorSnapshot
import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.MarketSnapshot
import com.example.trading.analysis.Timeframe
import com.example.trading.analysis.momentum.ConsolidationType
import com.example.trading.analysis.momentum.ExpansionClassification
import com.example.trading.analysis.momentum.MomentumConsolidationDetector
import com.example.trading.analysis.momentum.MomentumExpansionDetector
import com.example.trading.strategy.MomentumContinuationConfig
import com.example.trading.strategy.MomentumContinuationStrategy
import com.example.trading.strategy.NoTradeReason
import com.example.trading.strategy.SignalDecision
import com.example.trading.strategy.SignalDirection
import com.example.trading.strategy.StrategyContext
import com.example.trading.strategy.StrategyEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentumContinuationStrategyTest {

    private val strategy = MomentumContinuationStrategy()
    private val baseTs = 1700000000000L

    private fun createCandle(
        timestamp: Long,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
        volume: Double = 100.0
    ): Candle = Candle(timestamp, open, high, low, close, volume)

    private fun buildBullishMtfContext(): StrategyContext {
        val h1Candles = listOf(
            createCandle(baseTs - 36000000, 90.0, 92.0, 89.5, 91.5, 400.0),
            createCandle(baseTs - 32400000, 91.5, 93.5, 91.0, 93.0, 420.0),
            createCandle(baseTs - 28800000, 93.0, 95.0, 92.5, 94.5, 450.0),
            createCandle(baseTs - 25200000, 94.5, 97.0, 94.0, 96.5, 480.0),
            createCandle(baseTs - 21600000, 96.5, 99.0, 96.0, 98.5, 500.0),
            createCandle(baseTs - 18000000, 98.5, 101.0, 98.0, 100.0, 500.0),
            createCandle(baseTs - 14400000, 100.0, 105.0, 99.0, 104.0, 500.0),
            createCandle(baseTs - 10800000, 104.0, 108.0, 103.0, 107.0, 500.0),
            createCandle(baseTs - 7200000, 107.0, 112.0, 106.0, 111.0, 600.0),
            createCandle(baseTs - 3600000, 111.0, 115.0, 110.0, 114.0, 600.0)
        )
        val h1Indicators = IndicatorSnapshot(
            ema21 = 110.0,
            ema50 = 105.0,
            ema200 = 95.0,
            adx = 32.0,
            atr = 3.0,
            volumeSma20 = 500.0
        )
        val h1Snapshot = MarketSnapshot(
            symbol = "BTCUSDT",
            timeframe = Timeframe.H1,
            latestCandle = h1Candles.last(),
            indicators = h1Indicators,
            candles = h1Candles
        )

        // M15: 3 strong expansion candles followed by 3 tight consolidation candles
        val m15Candles = listOf(
            createCandle(baseTs - 8400000, 108.0, 109.5, 107.5, 109.0, 400.0),
            createCandle(baseTs - 7500000, 109.0, 109.8, 108.5, 109.5, 420.0),
            createCandle(baseTs - 6600000, 109.5, 110.2, 109.0, 109.8, 450.0),
            createCandle(baseTs - 5700000, 109.8, 110.5, 109.5, 110.0, 480.0),
            createCandle(baseTs - 4800000, 110.0, 113.0, 109.5, 112.5, 800.0), // Expansion 1
            createCandle(baseTs - 3900000, 112.5, 116.0, 112.0, 115.5, 850.0), // Expansion 2
            createCandle(baseTs - 3000000, 115.5, 118.0, 115.0, 117.8, 900.0), // Expansion 3
            createCandle(baseTs - 2100000, 117.8, 118.2, 117.0, 117.2, 300.0), // Consolidation 1
            createCandle(baseTs - 1200000, 117.2, 117.9, 116.8, 117.5, 250.0), // Consolidation 2
            createCandle(baseTs - 300000, 117.5, 118.0, 117.1, 117.6, 280.0)   // Consolidation 3
        )
        val m15Indicators = IndicatorSnapshot(
            ema21 = 115.0,
            ema50 = 112.0,
            rsi = 62.0,
            adx = 30.0,
            atr = 2.5,
            volumeSma20 = 500.0
        )
        val m15Snapshot = MarketSnapshot(
            symbol = "BTCUSDT",
            timeframe = Timeframe.M15,
            latestCandle = m15Candles.last(),
            indicators = m15Indicators,
            candles = m15Candles
        )

        // M5: Breakout above consolidation high (118.2)
        val m5Candles = listOf(
            createCandle(baseTs - 3000000, 116.5, 117.2, 116.2, 117.0, 200.0),
            createCandle(baseTs - 2700000, 117.0, 117.4, 116.8, 117.2, 200.0),
            createCandle(baseTs - 2400000, 117.2, 117.5, 116.9, 117.3, 210.0),
            createCandle(baseTs - 2100000, 117.3, 117.6, 117.0, 117.4, 210.0),
            createCandle(baseTs - 1800000, 117.4, 117.7, 117.1, 117.5, 200.0),
            createCandle(baseTs - 1500000, 117.5, 117.8, 117.2, 117.4, 200.0),
            createCandle(baseTs - 1200000, 117.2, 117.8, 117.0, 117.5, 200.0),
            createCandle(baseTs - 900000, 117.5, 118.0, 117.2, 117.6, 200.0),
            createCandle(baseTs - 600000, 117.6, 118.1, 117.4, 117.8, 220.0),
            createCandle(baseTs - 300000, 117.8, 119.5, 117.6, 119.2, 850.0)  // Strong Breakout candle
        )
        val m5Indicators = IndicatorSnapshot(
            ema21 = 117.5,
            ema50 = 116.0,
            rsi = 65.0,
            atr = 1.5,
            volumeSma20 = 400.0
        )
        val m5Snapshot = MarketSnapshot(
            symbol = "BTCUSDT",
            timeframe = Timeframe.M5,
            latestCandle = m5Candles.last(),
            indicators = m5Indicators,
            candles = m5Candles
        )

        return StrategyContext(
            symbol = "BTCUSDT",
            m5Snapshot = m5Snapshot,
            m15Snapshot = m15Snapshot,
            h1Snapshot = h1Snapshot,
            currentSpreadPercent = 0.0004,
            currentMarketRegime = MarketRegime.STRONG_BULL_TREND
        )
    }

    @Test
    fun testValidBullishMomentumContinuationSetup() = runBlocking {
        val context = buildBullishMtfContext()
        val signal = strategy.evaluate(context, context.config)

        if (signal.decision == SignalDecision.REJECT) {
            println("REJECT REASON: ${signal.rejectionReasons}, EVIDENCE: ${signal.evidence}")
        }

        assertNotNull(signal)
        assertEquals("Signal decision: ${signal.decision}, rejection: ${signal.rejectionReasons}, evidence: ${signal.evidence}", SignalDirection.LONG, signal.direction)
        assertTrue(signal.finalScore >= 65)
        assertTrue(signal.isPaperTradeEligible)
        assertTrue(signal.decision == SignalDecision.PAPER_TRADE || signal.decision == SignalDecision.APPROVED)
        assertTrue(signal.proposedStopLoss < signal.entryPrice)
        assertTrue(signal.proposedTakeProfit > signal.entryPrice)
        assertTrue(signal.riskRewardRatio >= 1.5)
    }

    @Test
    fun testLowAdxRejection() = runBlocking {
        val context = buildBullishMtfContext()
        val lowAdxH1Indicators = context.h1Snapshot!!.indicators.copy(adx = 12.0)
        val updatedContext = context.copy(
            h1Snapshot = context.h1Snapshot!!.copy(indicators = lowAdxH1Indicators)
        )

        val signal = strategy.evaluate(updatedContext, updatedContext.config)

        assertEquals(SignalDecision.REJECT, signal.decision)
        assertTrue(signal.rejectionReasons.contains(NoTradeReason.UNSUPPORTED_REGIME))
    }

    @Test
    fun testExcessiveSpreadRejection() = runBlocking {
        val context = buildBullishMtfContext().copy(currentSpreadPercent = 0.50)
        val signal = strategy.evaluate(context, context.config)

        assertEquals(SignalDecision.REJECT, signal.decision)
        assertTrue(signal.rejectionReasons.contains(NoTradeReason.SPREAD_TOO_HIGH))
    }

    @Test
    fun testMomentumExhaustionRejection() = runBlocking {
        val context = buildBullishMtfContext()
        val extremeRsiM15Ind = context.m15Snapshot!!.indicators.copy(rsi = 88.0)
        val updatedContext = context.copy(
            m15Snapshot = context.m15Snapshot!!.copy(indicators = extremeRsiM15Ind)
        )

        val signal = strategy.evaluate(updatedContext, updatedContext.config)

        assertEquals(SignalDecision.REJECT, signal.decision)
        assertTrue(signal.rejectionReasons.contains(NoTradeReason.INVALID_INDICATORS))
    }

    @Test
    fun testMomentumExpansionDetectorDirectly() {
        val detector = MomentumExpansionDetector()
        val config = MomentumContinuationConfig()
        val candles = listOf(
            createCandle(baseTs - 3600000, 100.0, 103.0, 99.5, 102.5, 800.0),
            createCandle(baseTs - 1800000, 102.5, 106.0, 102.0, 105.8, 900.0)
        )
        val indicators = IndicatorSnapshot(rsi = 65.0, atr = 2.0, volumeSma20 = 500.0)

        val result = detector.detectExpansion(candles, indicators, SignalDirection.LONG, config)

        assertTrue(result.isValid)
        assertEquals(ExpansionClassification.STRONG, result.classification)
        assertEquals(2, result.candleCount)
    }

    @Test
    fun testConsolidationDetectorDirectly() {
        val expansionDetector = MomentumExpansionDetector()
        val consolidationDetector = MomentumConsolidationDetector()
        val config = MomentumContinuationConfig()

        val expansionCandles = listOf(
            createCandle(baseTs - 5400000, 100.0, 103.0, 99.5, 102.5, 800.0),
            createCandle(baseTs - 4500000, 102.5, 106.0, 102.0, 105.8, 900.0)
        )
        val indicators = IndicatorSnapshot(rsi = 65.0, atr = 2.0, volumeSma20 = 500.0)
        val expResult = expansionDetector.detectExpansion(expansionCandles, indicators, SignalDirection.LONG, config)

        val consolidationCandles = listOf(
            createCandle(baseTs - 3600000, 105.8, 106.2, 105.0, 105.2, 300.0),
            createCandle(baseTs - 2700000, 105.2, 105.9, 104.9, 105.5, 250.0),
            createCandle(baseTs - 1800000, 105.5, 106.0, 105.1, 105.6, 280.0)
        )

        val consResult = consolidationDetector.detectConsolidation(consolidationCandles, expResult, indicators, SignalDirection.LONG, config)

        assertTrue(consResult.isPreserved)
        assertTrue(consResult.type != ConsolidationType.INVALID)
    }

    @Test
    fun testStrategyEngineIntegration() = runBlocking {
        val engine = StrategyEngine()
        val context = buildBullishMtfContext()

        val mtf = com.example.trading.analysis.MultiTimeframeSnapshot(
            symbol = "BTCUSDT",
            m5 = context.m5Snapshot,
            m15 = context.m15Snapshot,
            h1 = context.h1Snapshot
        )

        val latestTs = mtf.m5!!.latestCandle.timestamp
        val result = engine.evaluateSymbol(
            mtfSnapshot = mtf,
            spreadPercent = 0.0004,
            currentTimeMs = latestTs
        )

        assertTrue(result.dataQualityResult.isValid)
        assertTrue(result.strategiesEvaluatedCount >= 1)
        assertNotNull(result.bestCandidate)
    }
}
