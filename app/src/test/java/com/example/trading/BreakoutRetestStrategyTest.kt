package com.example.trading

import com.example.trading.analysis.*
import com.example.trading.risk.*
import com.example.trading.strategy.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BreakoutRetestStrategyTest {

    private lateinit var strategy: BreakoutRetestStrategy
    private val fixedNow = 1700000000000L

    @Before
    fun setUp() {
        strategy = BreakoutRetestStrategy(BreakoutRetestConfig())
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
                    low = p - 10.0,
                    close = if (isBullish) p + 8.0 else p - 8.0,
                    volume = 100.0
                )
            )
        }
        return candles
    }

    @Test
    fun testValidBullishBreakout() = runBlocking {
        val h1Candles = createCandles(10, 60000.0, true)
        val h1Ind = IndicatorSnapshot(ema50 = 59000.0, ema200 = 57000.0, adx = 30.0, atr = 800.0, atrPercent = 1.3)
        val h1 = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), h1Ind)

        // M15 with resistance = 60000.0
        val m15Candles = mutableListOf<Candle>()
        val startTs = fixedNow - (10 * 900000L)
        for (i in 0 until 6) {
            m15Candles.add(Candle(startTs + (i * 900000L), 59800.0, 59950.0, 59700.0, 59900.0, 100.0))
        }
        // Breakout candle at index 6
        m15Candles.add(Candle(startTs + (6 * 900000L), 59900.0, 60250.0, 59850.0, 60200.0, 250.0))
        // Retest candle at index 7-8
        m15Candles.add(Candle(startTs + (7 * 900000L), 60200.0, 60220.0, 60005.0, 60080.0, 80.0))
        m15Candles.add(Candle(startTs + (8 * 900000L), 60080.0, 60150.0, 60010.0, 60120.0, 90.0))

        val m15Ind = IndicatorSnapshot(resistancePrice = 60000.0, volumeSma20 = 100.0, atr = 500.0, atrPercent = 1.0)
        val m15 = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), m15Ind)

        // M5 confirmation
        val m5Candles = mutableListOf<Candle>()
        val startM5Ts = fixedNow - (10 * 300000L)
        for (i in 0 until 9) {
            val lowP = if (i >= 5) 59750.0 else 60000.0
            m5Candles.add(Candle(startM5Ts + (i * 300000L), 60050.0, 60150.0, lowP, 60100.0, 100.0))
        }
        m5Candles.add(Candle(fixedNow, 60100.0, 60200.0, 59750.0, 60180.0, 160.0))

        val m5Ind = IndicatorSnapshot(ema9 = 60100.0, volumeSma20 = 100.0)
        val m5 = MarketSnapshot("BTC/USDT", Timeframe.M5, m5Candles, m5Candles.last(), m5Ind)

        val context = StrategyContext(
            symbol = "BTC/USDT",
            m5Snapshot = m5,
            m15Snapshot = m15,
            h1Snapshot = h1,
            currentMarketRegime = MarketRegime.BREAKOUT,
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
    fun testValidBearishBreakout() = runBlocking {
        val h1Candles = createCandles(10, 60000.0, false)
        val h1Ind = IndicatorSnapshot(ema50 = 61000.0, ema200 = 63000.0, adx = 32.0, atr = 800.0, atrPercent = 1.3)
        val h1 = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), h1Ind)

        // M15 with support = 60000.0
        val m15Candles = mutableListOf<Candle>()
        val startTs = fixedNow - (10 * 900000L)
        for (i in 0 until 6) {
            m15Candles.add(Candle(startTs + (i * 900000L), 60200.0, 60300.0, 60050.0, 60100.0, 100.0))
        }
        // Breakout candle at index 6 (drops below 60000)
        m15Candles.add(Candle(startTs + (6 * 900000L), 60100.0, 60150.0, 59750.0, 59800.0, 250.0))
        // Retest candle
        m15Candles.add(Candle(startTs + (7 * 900000L), 59800.0, 59995.0, 59780.0, 59900.0, 80.0))

        val m15Ind = IndicatorSnapshot(supportPrice = 60000.0, volumeSma20 = 100.0, atr = 500.0, atrPercent = 1.0)
        val m15 = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), m15Ind)

        // M5 confirmation
        val m5Candles = mutableListOf<Candle>()
        val startM5Ts = fixedNow - (10 * 300000L)
        for (i in 0 until 9) {
            val highP = if (i >= 5) 60250.0 else 60000.0
            m5Candles.add(Candle(startM5Ts + (i * 300000L), 59950.0, highP, 59850.0, 59900.0, 100.0))
        }
        m5Candles.add(Candle(fixedNow, 59900.0, 60250.0, 59750.0, 59820.0, 160.0))

        val m5Ind = IndicatorSnapshot(ema9 = 59900.0, volumeSma20 = 100.0)
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
    fun testFailedRetest() = runBlocking {
        val h1Candles = createCandles(10, 60000.0, true)
        val h1Ind = IndicatorSnapshot(ema50 = 59000.0, ema200 = 57000.0, adx = 30.0)
        val h1 = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), h1Ind)

        val m15Candles = mutableListOf<Candle>()
        val startTs = fixedNow - (10 * 900000L)
        m15Candles.add(Candle(startTs, 59800.0, 59950.0, 59700.0, 59900.0, 100.0))
        // Breakout candle
        m15Candles.add(Candle(startTs + 900000L, 59900.0, 60250.0, 59850.0, 60200.0, 250.0))
        // Failed retest (closes deep inside range at 59200)
        m15Candles.add(Candle(startTs + 1800000L, 60200.0, 60220.0, 59100.0, 59200.0, 180.0))

        val m15Ind = IndicatorSnapshot(resistancePrice = 60000.0, volumeSma20 = 100.0)
        val m15 = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), m15Ind)
        val m5 = MarketSnapshot("BTC/USDT", Timeframe.M5, createCandles(10, 59200.0, true), m15Candles.last(), IndicatorSnapshot())

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
        assertTrue(signal.rejectionReasons.contains(NoTradeReason.LOW_SIGNAL_SCORE))
    }

    @Test
    fun testFakeBreakout() = runBlocking {
        val h1Candles = createCandles(10, 60000.0, true)
        val h1Ind = IndicatorSnapshot(ema50 = 59000.0, ema200 = 57000.0, adx = 30.0)
        val h1 = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), h1Ind)

        // M15 without any candle closing beyond resistance
        val m15Candles = createCandles(10, 59800.0, true)
        val m15Ind = IndicatorSnapshot(resistancePrice = 62000.0, volumeSma20 = 100.0) // Resistance way higher than closes
        val m15 = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), m15Ind)
        val m5 = MarketSnapshot("BTC/USDT", Timeframe.M5, createCandles(10, 59800.0, true), m15Candles.last(), IndicatorSnapshot())

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
        assertTrue(signal.rejectionReasons.contains(NoTradeReason.LOW_SIGNAL_SCORE))
    }

    @Test
    fun testLowVolumeBreakout() = runBlocking {
        val h1Candles = createCandles(10, 60000.0, true)
        val h1Ind = IndicatorSnapshot(ema50 = 59000.0, ema200 = 57000.0, adx = 30.0)
        val h1 = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), h1Ind)

        val m15Candles = mutableListOf<Candle>()
        val startTs = fixedNow - (10 * 900000L)
        m15Candles.add(Candle(startTs, 59800.0, 59950.0, 59700.0, 59900.0, 100.0))
        m15Candles.add(Candle(startTs + 900000L, 59850.0, 59960.0, 59750.0, 59920.0, 100.0))
        // Breakout candle with very low volume (10.0 vs avg 100.0)
        m15Candles.add(Candle(startTs + 1800000L, 59900.0, 60250.0, 59850.0, 60200.0, 10.0))

        val m15Ind = IndicatorSnapshot(resistancePrice = 60000.0, volumeSma20 = 100.0)
        val m15 = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), m15Ind)
        val m5CandlesList = createCandles(10, 60200.0, true)
        val m5 = MarketSnapshot("BTC/USDT", Timeframe.M5, m5CandlesList, m5CandlesList.last(), IndicatorSnapshot())

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
        assertTrue(signal.rejectionReasons.contains(NoTradeReason.LOW_SIGNAL_SCORE))
    }

    @Test
    fun testInvalidStopLoss() = runBlocking {
        val customStrategy = BreakoutRetestStrategy(BreakoutRetestConfig(minSlDistancePercent = 10.0)) // Unreasonably high min SL distance

        val h1Candles = createCandles(10, 60000.0, true)
        val h1Ind = IndicatorSnapshot(ema50 = 59000.0, ema200 = 57000.0, adx = 30.0)
        val h1 = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), h1Ind)

        val m15Candles = mutableListOf<Candle>()
        val startTs = fixedNow - (10 * 900000L)
        m15Candles.add(Candle(startTs, 59800.0, 59950.0, 59700.0, 59900.0, 100.0))
        m15Candles.add(Candle(startTs + 900000L, 59900.0, 60250.0, 59850.0, 60200.0, 250.0))
        m15Candles.add(Candle(startTs + 1800000L, 60200.0, 60220.0, 60005.0, 60080.0, 80.0))

        val m15Ind = IndicatorSnapshot(resistancePrice = 60000.0, volumeSma20 = 100.0, atr = 100.0)
        val m15 = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), m15Ind)
        val m5CandlesList = createCandles(10, 60100.0, true)
        val m5 = MarketSnapshot("BTC/USDT", Timeframe.M5, m5CandlesList, m5CandlesList.last(), IndicatorSnapshot(ema9 = 60050.0, volumeSma20 = 50.0))

        val context = StrategyContext(
            symbol = "BTC/USDT",
            m5Snapshot = m5,
            m15Snapshot = m15,
            h1Snapshot = h1,
            currentMarketRegime = MarketRegime.STRONG_BULL_TREND,
            dataTimestamp = fixedNow
        )

        val signal = customStrategy.evaluate(context, StrategyConfig())

        assertEquals(SignalDecision.REJECT, signal.decision)
        assertTrue(signal.rejectionReasons.contains(NoTradeReason.RISK_ENGINE_REJECTED))
    }

    @Test
    fun testPoorRiskReward() = runBlocking {
        val customStrategy = BreakoutRetestStrategy(BreakoutRetestConfig(minRiskRewardRatio = 5.0)) // High R:R requirement

        val h1Candles = createCandles(10, 60000.0, true)
        val h1Ind = IndicatorSnapshot(ema50 = 59000.0, ema200 = 57000.0, adx = 30.0)
        val h1 = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), h1Ind)

        val m15Candles = mutableListOf<Candle>()
        val startTs = fixedNow - (10 * 900000L)
        m15Candles.add(Candle(startTs, 59800.0, 59950.0, 59700.0, 59900.0, 100.0))
        m15Candles.add(Candle(startTs + 900000L, 59900.0, 60250.0, 59850.0, 60200.0, 250.0))
        m15Candles.add(Candle(startTs + 1800000L, 60200.0, 60220.0, 60005.0, 60080.0, 80.0))

        val m15Ind = IndicatorSnapshot(resistancePrice = 60000.0, volumeSma20 = 100.0, atr = 500.0)
        val m15 = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), m15Ind)
        val m5CandlesList = createCandles(10, 60100.0, true)
        val m5 = MarketSnapshot("BTC/USDT", Timeframe.M5, m5CandlesList, m5CandlesList.last(), IndicatorSnapshot(ema9 = 60050.0, volumeSma20 = 50.0))

        val context = StrategyContext(
            symbol = "BTC/USDT",
            m5Snapshot = m5,
            m15Snapshot = m15,
            h1Snapshot = h1,
            currentMarketRegime = MarketRegime.STRONG_BULL_TREND,
            dataTimestamp = fixedNow
        )

        val signal = customStrategy.evaluate(context, StrategyConfig())

        assertEquals(SignalDecision.REJECT, signal.decision)
        assertTrue(signal.rejectionReasons.contains(NoTradeReason.POOR_RISK_REWARD))
    }

    @Test
    fun testDuplicateSignalFingerprint() = runBlocking {
        val h1Candles = createCandles(10, 60000.0, true)
        val h1Ind = IndicatorSnapshot(ema50 = 59000.0, ema200 = 57000.0, adx = 30.0, atr = 800.0)
        val h1 = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), h1Ind)

        val m15Candles = mutableListOf<Candle>()
        val startTs = fixedNow - (10 * 900000L)
        m15Candles.add(Candle(startTs, 59800.0, 59950.0, 59700.0, 59900.0, 100.0))
        m15Candles.add(Candle(startTs + 900000L, 59900.0, 60250.0, 59850.0, 60200.0, 250.0))
        m15Candles.add(Candle(startTs + 1800000L, 60200.0, 60220.0, 60005.0, 60080.0, 80.0))

        val m15Ind = IndicatorSnapshot(resistancePrice = 60000.0, volumeSma20 = 100.0, atr = 500.0)
        val m15 = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), m15Ind)
        val m5CandlesList = createCandles(10, 60100.0, true)
        val m5 = MarketSnapshot("BTC/USDT", Timeframe.M5, m5CandlesList, m5CandlesList.last(), IndicatorSnapshot(ema9 = 60050.0, volumeSma20 = 50.0))

        val context = StrategyContext(
            symbol = "BTC/USDT",
            m5Snapshot = m5,
            m15Snapshot = m15,
            h1Snapshot = h1,
            currentMarketRegime = MarketRegime.BREAKOUT,
            dataTimestamp = fixedNow
        )

        val signal1 = strategy.evaluate(context, StrategyConfig())
        val signal2 = strategy.evaluate(context, StrategyConfig())

        val fp1 = signal1.evidence.firstOrNull { it.contains("Fingerprint:") }
        val fp2 = signal2.evidence.firstOrNull { it.contains("Fingerprint:") }

        assertNotNull(fp1)
        assertNotNull(fp2)
        assertEquals(fp1, fp2)
        assertTrue(fp1!!.contains("breakout_retest_mtf_v1_BTC/USDT_LONG_"))
    }

    @Test
    fun testBoundaryScoreTests() {
        val score49 = SignalScore(trendAlignment = 20, marketStructure = 10, momentum = 10, volumeConfirmation = 9)
        assertEquals(49, score49.totalScore)
        assertEquals(SignalDecision.REJECT, score49.getDecision())

        val score50 = SignalScore(trendAlignment = 20, marketStructure = 10, momentum = 10, volumeConfirmation = 10)
        assertEquals(50, score50.totalScore)
        assertEquals(SignalDecision.WATCHLIST, score50.getDecision())

        val score65 = SignalScore(trendAlignment = 20, marketStructure = 15, momentum = 15, volumeConfirmation = 15)
        assertEquals(65, score65.totalScore)
        assertEquals(SignalDecision.PAPER_TRADE, score65.getDecision())

        val score80 = SignalScore(trendAlignment = 20, marketStructure = 15, momentum = 15, volumeConfirmation = 15, volatilitySuitability = 10, entryQuality = 5)
        assertEquals(80, score80.totalScore)
        assertEquals(SignalDecision.APPROVED, score80.getDecision())
    }
}
