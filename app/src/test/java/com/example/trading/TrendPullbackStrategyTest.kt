package com.example.trading

import com.example.trading.analysis.*
import com.example.trading.risk.*
import com.example.trading.strategy.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TrendPullbackStrategyTest {

    private lateinit var strategy: TrendPullbackStrategy
    private val fixedNow = 1700000000000L

    @Before
    fun setUp() {
        strategy = TrendPullbackStrategy(TrendPullbackConfig())
    }

    private fun createCandles(count: Int, basePrice: Double = 60000.0, isBullish: Boolean = true): List<Candle> {
        val candles = mutableListOf<Candle>()
        val startTs = fixedNow - (count * 300000L)
        for (i in 0 until count) {
            val p = if (isBullish) basePrice + (i * 10.0) else basePrice - (i * 10.0)
            candles.add(
                Candle(
                    timestamp = startTs + (i * 300000L),
                    open = p,
                    high = p + 15.0,
                    low = if (isBullish) p - 30.0 else p - 10.0,
                    close = if (isBullish) p + 8.0 else p - 8.0,
                    volume = 100.0
                )
            )
        }
        return candles
    }

    @Test
    fun testValidLongSetup() = runBlocking {
        val h1Candles = createCandles(10, 60000.0, true) // close ~ 60098
        val m15Candles = createCandles(10, 60000.0, true)

        // M5 candles with swing low giving ~0.5% SL distance
        val m5Candles = mutableListOf<Candle>()
        val startTs = fixedNow - (10 * 300000L)
        for (i in 0 until 10) {
            val p = 60000.0 + (i * 10.0)
            val lowPrice = if (i >= 5) 59750.0 else p - 20.0
            m5Candles.add(
                Candle(
                    timestamp = startTs + (i * 300000L),
                    open = p,
                    high = p + 15.0,
                    low = lowPrice,
                    close = p + 8.0,
                    volume = 100.0
                )
            )
        }

        val h1Ind = IndicatorSnapshot(ema50 = 59000.0, ema200 = 57000.0, adx = 30.0, atr = 800.0, atrPercent = 1.3)
        val m15Ind = IndicatorSnapshot(ema21 = 60050.0, ema50 = 59500.0, rsi = 48.0, atr = 600.0, atrPercent = 1.0, supportPrice = 58000.0)
        val m5Ind = IndicatorSnapshot(ema9 = 60020.0, volumeSma20 = 50.0)

        val h1 = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), h1Ind)
        val m15 = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), m15Ind)
        val m5 = MarketSnapshot("BTC/USDT", Timeframe.M5, m5Candles, m5Candles.last(), m5Ind)

        val context = StrategyContext(
            symbol = "BTC/USDT",
            m5Snapshot = m5,
            m15Snapshot = m15,
            h1Snapshot = h1,
            currentMarketRegime = MarketRegime.STRONG_BULL_TREND,
            dataTimestamp = fixedNow
        )

        val signal = strategy.evaluate(context, StrategyConfig())

        assertEquals("BTC/USDT", signal.symbol)
        assertEquals(SignalDirection.LONG, signal.direction)
        assertTrue("SL should be < Entry (${signal.proposedStopLoss} vs ${signal.entryPrice})", signal.proposedStopLoss < signal.entryPrice)
        assertTrue("TP should be > Entry (${signal.proposedTakeProfit} vs ${signal.entryPrice})", signal.proposedTakeProfit > signal.entryPrice)
        assertTrue("R:R should be >= 1.5 (${signal.riskRewardRatio})", signal.riskRewardRatio >= 1.5)
        assertTrue("Final score should be >= 65 (${signal.finalScore})", signal.finalScore >= 65)
        assertTrue(signal.isPaperTradeEligible)
        assertTrue(signal.evidence.any { it.contains("Fingerprint:") })
    }

    @Test
    fun testValidShortSetup() = runBlocking {
        val h1Candles = createCandles(10, 60000.0, false) // close ~ 59902
        val m15Candles = createCandles(10, 60000.0, false)

        val m5Candles = mutableListOf<Candle>()
        val startTs = fixedNow - (10 * 300000L)
        for (i in 0 until 10) {
            val p = 60000.0 - (i * 10.0)
            val highPrice = if (i >= 5) 60250.0 else p + 20.0
            m5Candles.add(
                Candle(
                    timestamp = startTs + (i * 300000L),
                    open = p,
                    high = highPrice,
                    low = p - 20.0,
                    close = p - 8.0,
                    volume = 100.0
                )
            )
        }

        val h1Ind = IndicatorSnapshot(ema50 = 61000.0, ema200 = 63000.0, adx = 32.0, atr = 800.0, atrPercent = 1.3)
        val m15Ind = IndicatorSnapshot(ema21 = 59950.0, ema50 = 60500.0, rsi = 52.0, atr = 600.0, atrPercent = 1.0, resistancePrice = 62000.0)
        val m5Ind = IndicatorSnapshot(ema9 = 59980.0, volumeSma20 = 50.0)

        val h1 = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), h1Ind)
        val m15 = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), m15Ind)
        val m5 = MarketSnapshot("BTC/USDT", Timeframe.M5, m5Candles, m5Candles.last(), m5Ind)

        val context = StrategyContext(
            symbol = "BTC/USDT",
            m5Snapshot = m5,
            m15Snapshot = m15,
            h1Snapshot = h1,
            currentMarketRegime = MarketRegime.STRONG_BEAR_TREND,
            dataTimestamp = fixedNow
        )

        val signal = strategy.evaluate(context, StrategyConfig())

        assertEquals("BTC/USDT", signal.symbol)
        assertEquals(SignalDirection.SHORT, signal.direction)
        assertTrue("SL should be > Entry (${signal.proposedStopLoss} vs ${signal.entryPrice})", signal.proposedStopLoss > signal.entryPrice)
        assertTrue("TP should be < Entry (${signal.proposedTakeProfit} vs ${signal.entryPrice})", signal.proposedTakeProfit < signal.entryPrice)
        assertTrue("R:R should be >= 1.5 (${signal.riskRewardRatio})", signal.riskRewardRatio >= 1.5)
        assertTrue("Final score should be >= 65 (${signal.finalScore})", signal.finalScore >= 65)
        assertTrue(signal.isPaperTradeEligible)
    }

    @Test
    fun testTrendConflictRejection() = runBlocking {
        val h1Candles = createCandles(10, 60000.0, true) // close ~ 60098
        val m15Candles = createCandles(10, 60000.0, true)
        val m5Candles = createCandles(10, 60000.0, true)

        val h1Ind = IndicatorSnapshot(ema50 = 62000.0, ema200 = 63000.0, adx = 30.0)
        val m15Ind = IndicatorSnapshot(ema21 = 60050.0, ema50 = 59500.0, rsi = 50.0)

        val h1 = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), h1Ind)
        val m15 = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), m15Ind)
        val m5 = MarketSnapshot("BTC/USDT", Timeframe.M5, m5Candles, m5Candles.last(), IndicatorSnapshot())

        val context = StrategyContext(
            symbol = "BTC/USDT",
            m5Snapshot = m5,
            m15Snapshot = m15,
            h1Snapshot = h1,
            currentMarketRegime = MarketRegime.STRONG_BULL_TREND,
            dataTimestamp = fixedNow
        )

        val signal = strategy.evaluate(context, StrategyConfig())

        assertEquals(SignalDecision.REJECT, signal.decision)
        assertTrue(signal.rejectionReasons.contains(NoTradeReason.CONFLICTING_TIMEFRAMES))
    }

    @Test
    fun testLowAdxRejection() = runBlocking {
        val h1Candles = createCandles(10, 60000.0, true)
        val m15Candles = createCandles(10, 60000.0, true)
        val m5Candles = createCandles(10, 60000.0, true)

        val h1Ind = IndicatorSnapshot(ema50 = 59000.0, ema200 = 57000.0, adx = 15.0) // Low ADX (< 22)
        val h1 = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), h1Ind)
        val m15 = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), IndicatorSnapshot())
        val m5 = MarketSnapshot("BTC/USDT", Timeframe.M5, m5Candles, m5Candles.last(), IndicatorSnapshot())

        val context = StrategyContext(
            symbol = "BTC/USDT",
            m5Snapshot = m5,
            m15Snapshot = m15,
            h1Snapshot = h1,
            currentMarketRegime = MarketRegime.WEAK_BULL_TREND,
            dataTimestamp = fixedNow
        )

        val signal = strategy.evaluate(context, StrategyConfig())

        assertEquals(SignalDecision.REJECT, signal.decision)
        assertTrue(signal.rejectionReasons.contains(NoTradeReason.LOW_SIGNAL_SCORE))
    }

    @Test
    fun testRangeMarketRejection() = runBlocking {
        val h1Candles = createCandles(10, 60000.0, true)
        val snapshot = MarketSnapshot("BTC/USDT", Timeframe.M15, h1Candles, h1Candles.last(), IndicatorSnapshot())

        val context = StrategyContext(
            symbol = "BTC/USDT",
            m5Snapshot = snapshot,
            m15Snapshot = snapshot,
            h1Snapshot = snapshot,
            currentMarketRegime = MarketRegime.RANGE,
            dataTimestamp = fixedNow
        )

        val signal = strategy.evaluate(context, StrategyConfig())

        assertEquals(SignalDecision.REJECT, signal.decision)
        assertTrue(signal.rejectionReasons.contains(NoTradeReason.UNSUPPORTED_REGIME))
    }

    @Test
    fun testExcessiveSpreadRejection() = runBlocking {
        val candles = createCandles(10, 60000.0, true)
        val snapshot = MarketSnapshot("BTC/USDT", Timeframe.M15, candles, candles.last(), IndicatorSnapshot())

        val context = StrategyContext(
            symbol = "BTC/USDT",
            m5Snapshot = snapshot,
            m15Snapshot = snapshot,
            h1Snapshot = snapshot,
            currentSpreadPercent = 0.8, // Max allowed is 0.3
            currentMarketRegime = MarketRegime.STRONG_BULL_TREND,
            dataTimestamp = fixedNow
        )

        val signal = strategy.evaluate(context, StrategyConfig())

        assertEquals(SignalDecision.REJECT, signal.decision)
        assertTrue(signal.rejectionReasons.contains(NoTradeReason.SPREAD_TOO_HIGH))
    }

    @Test
    fun testScoreDecisionBoundaries() {
        val score49 = SignalScore(trendAlignment = 20, marketStructure = 10, momentum = 10, volumeConfirmation = 9)
        assertEquals(49, score49.totalScore)
        assertEquals(SignalDecision.REJECT, score49.getDecision())

        val score50 = SignalScore(trendAlignment = 20, marketStructure = 10, momentum = 10, volumeConfirmation = 10)
        assertEquals(50, score50.totalScore)
        assertEquals(SignalDecision.WATCHLIST, score50.getDecision())

        val score64 = SignalScore(trendAlignment = 20, marketStructure = 15, momentum = 15, volumeConfirmation = 14)
        assertEquals(64, score64.totalScore)
        assertEquals(SignalDecision.WATCHLIST, score64.getDecision())

        val score65 = SignalScore(trendAlignment = 20, marketStructure = 15, momentum = 15, volumeConfirmation = 15)
        assertEquals(65, score65.totalScore)
        assertEquals(SignalDecision.PAPER_TRADE, score65.getDecision())

        val score79 = SignalScore(trendAlignment = 20, marketStructure = 15, momentum = 15, volumeConfirmation = 15, volatilitySuitability = 10, entryQuality = 4)
        assertEquals(79, score79.totalScore)
        assertEquals(SignalDecision.PAPER_TRADE, score79.getDecision())

        val score80 = SignalScore(trendAlignment = 20, marketStructure = 15, momentum = 15, volumeConfirmation = 15, volatilitySuitability = 10, entryQuality = 5)
        assertEquals(80, score80.totalScore)
        assertEquals(SignalDecision.APPROVED, score80.getDecision())
    }
}
