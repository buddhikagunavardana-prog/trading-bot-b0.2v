package com.example.trading

import com.example.trading.analysis.Candle
import com.example.trading.analysis.IndicatorSnapshot
import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.MarketSnapshot
import com.example.trading.analysis.Timeframe
import com.example.trading.analysis.range.BoundaryRejectionDetector
import com.example.trading.analysis.range.FalseBreakoutDetector
import com.example.trading.analysis.range.FalseBreakoutType
import com.example.trading.analysis.range.MeanReversionAnalyzer
import com.example.trading.analysis.range.RangeDetector
import com.example.trading.analysis.range.RangeQualityAnalyzer
import com.example.trading.strategy.NoTradeReason
import com.example.trading.strategy.RangeReversalConfig
import com.example.trading.strategy.RangeReversalStrategy
import com.example.trading.strategy.SignalDecision
import com.example.trading.strategy.SignalDirection
import com.example.trading.strategy.StrategyConfig
import com.example.trading.strategy.StrategyContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeReversalStrategyTest {

    private val fixedNow = 1700000000000L

    private fun createCandles(
        count: Int,
        startPrice: Double,
        step: Double,
        highOffset: Double = 2.0,
        lowOffset: Double = 2.0,
        startTs: Long = fixedNow - (count * 900000L)
    ): List<Candle> {
        val list = mutableListOf<Candle>()
        var price = startPrice
        for (i in 0 until count) {
            val ts = startTs + (i * 900000L)
            list.add(
                Candle(
                    timestamp = ts,
                    open = price,
                    high = price + highOffset,
                    low = price - lowOffset,
                    close = price + step,
                    volume = 100.0
                )
            )
            price += step
        }
        return list
    }

    private fun createOscillatingCandles(
        count: Int,
        lowLevel: Double = 90.0,
        highLevel: Double = 110.0,
        startTs: Long = fixedNow - (count * 900000L)
    ): List<Candle> {
        val list = mutableListOf<Candle>()
        var goingUp = true
        var price = (lowLevel + highLevel) / 2.0

        for (i in 0 until count) {
            val ts = startTs + (i * 900000L)
            if (price >= highLevel) goingUp = false
            if (price <= lowLevel) goingUp = true

            val open = price
            val close = if (goingUp) Math.min(price + 4.0, highLevel) else Math.max(price - 4.0, lowLevel)
            val high = Math.max(open, close) + (if (close >= highLevel - 1.0) 0.5 else 1.0)
            val low = Math.min(open, close) - (if (close <= lowLevel + 1.0) 0.5 else 1.0)

            list.add(Candle(ts, open, high, low, close, 100.0))
            price = close
        }
        return list
    }

    // 1. Range Detection & Quality Test
    @Test
    fun testRangeDetectionAndQualityScore() {
        val detector = RangeDetector()
        val candles = createOscillatingCandles(20, lowLevel = 90.0, highLevel = 110.0)

        val range = detector.detectRange(
            symbol = "BTC/USDT",
            timeframe = Timeframe.M15,
            candles = candles,
            adx = 15.0,
            atr = 2.0
        )

        assertNotNull(range)
        assertTrue(range!!.upperTouchCount >= 2)
        assertTrue(range.lowerTouchCount >= 2)
        assertTrue(range.qualityScore >= 50.0)
        assertEquals(Timeframe.M15, range.timeframe)
    }

    // 2. Valid LONG Reversal Setup
    @Test
    fun testValidLongReversalSetup() = runBlocking {
        val strategy = RangeReversalStrategy()
        val baseTs = fixedNow

        // H1 snapshot: low ADX, range regime
        val h1Candles = createCandles(10, 100.0, 0.0, startTs = baseTs - 10 * 3600000L)
        val h1Snap = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), IndicatorSnapshot(adx = 15.0, atr = 2.0))

        // M15 snapshot: oscillating range between 90 and 110, ending near lower boundary (~90)
        val m15Candles = createOscillatingCandles(20, lowLevel = 90.0, highLevel = 110.0, startTs = baseTs - 20 * 900000L).toMutableList()
        // Add lower boundary rejection candle
        m15Candles.add(Candle(baseTs - 900000, 91.0, 92.0, 88.0, 90.5, 150.0)) // touched 88, closed 90.5
        val m15Snap = MarketSnapshot(
            "BTC/USDT",
            Timeframe.M15,
            m15Candles,
            m15Candles.last(),
            IndicatorSnapshot(rsi = 32.0, atr = 2.0, bbLower = 90.5, supportPrice = 90.0, resistancePrice = 110.0)
        )

        // M5 snapshot: bullish confirmation near lower boundary
        val m5Candles = listOf(
            Candle(baseTs - 600000, 89.5, 90.0, 89.0, 89.8, 50.0),
            Candle(baseTs - 300000, 89.8, 91.0, 89.7, 90.8, 80.0) // Bullish close at 90.8
        )
        val m5Snap = MarketSnapshot("BTC/USDT", Timeframe.M5, m5Candles, m5Candles.last(), IndicatorSnapshot(atr = 1.0))

        val context = StrategyContext(
            symbol = "BTC/USDT",
            m5Snapshot = m5Snap,
            m15Snapshot = m15Snap,
            h1Snapshot = h1Snap,
            currentSpreadPercent = 0.05,
            dataTimestamp = baseTs,
            currentMarketRegime = MarketRegime.RANGE,
            config = StrategyConfig(minScoreForWatchlist = 50, minScoreForPaperTrade = 65, minScoreForApproved = 80)
        )

        val signal = strategy.evaluate(context, context.config)

        assertNotNull(signal)
        assertEquals(SignalDirection.LONG, signal.direction)
        assertTrue(signal.finalScore >= 65)
        assertTrue(signal.isPaperTradeEligible)
        assertTrue(signal.decision == SignalDecision.PAPER_TRADE || signal.decision == SignalDecision.APPROVED)
        assertTrue(signal.proposedStopLoss < signal.entryPrice)
        assertTrue(signal.proposedTakeProfit > signal.entryPrice)
    }

    // 3. Valid SHORT Reversal Setup
    @Test
    fun testValidShortReversalSetup() = runBlocking {
        val strategy = RangeReversalStrategy()
        val baseTs = fixedNow

        val h1Candles = createCandles(10, 100.0, 0.0, startTs = baseTs - 10 * 3600000L)
        val h1Snap = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), IndicatorSnapshot(adx = 16.0, atr = 2.0))

        val m15Candles = createOscillatingCandles(20, lowLevel = 90.0, highLevel = 110.0, startTs = baseTs - 20 * 900000L).toMutableList()
        // Add upper boundary rejection candle (touched 112, closed 109.0)
        m15Candles.add(Candle(baseTs - 900000, 108.0, 112.0, 107.5, 109.0, 150.0))
        val m15Snap = MarketSnapshot(
            "BTC/USDT",
            Timeframe.M15,
            m15Candles,
            m15Candles.last(),
            IndicatorSnapshot(rsi = 68.0, atr = 2.0, bbUpper = 109.5, supportPrice = 90.0, resistancePrice = 110.0)
        )

        // M5 snapshot: bearish confirmation near upper boundary
        val m5Candles = listOf(
            Candle(baseTs - 600000, 109.5, 110.0, 109.0, 109.2, 50.0),
            Candle(baseTs - 300000, 109.2, 109.5, 108.0, 108.2, 80.0) // Bearish close at 108.2
        )
        val m5Snap = MarketSnapshot("BTC/USDT", Timeframe.M5, m5Candles, m5Candles.last(), IndicatorSnapshot(atr = 1.0))

        val context = StrategyContext(
            symbol = "BTC/USDT",
            m5Snapshot = m5Snap,
            m15Snapshot = m15Snap,
            h1Snapshot = h1Snap,
            currentSpreadPercent = 0.05,
            dataTimestamp = baseTs,
            currentMarketRegime = MarketRegime.RANGE,
            config = StrategyConfig(minScoreForWatchlist = 50, minScoreForPaperTrade = 65, minScoreForApproved = 80)
        )

        val signal = strategy.evaluate(context, context.config)

        assertNotNull(signal)
        assertEquals(SignalDirection.SHORT, signal.direction)
        assertTrue(signal.finalScore >= 65)
        assertTrue(signal.proposedStopLoss > signal.entryPrice)
        assertTrue(signal.proposedTakeProfit < signal.entryPrice)
    }

    // 4. Rejection on True Breakout
    @Test
    fun testRejectionOnTrueBreakout() = runBlocking {
        val strategy = RangeReversalStrategy()
        val baseTs = fixedNow

        val h1Candles = createCandles(10, 100.0, 0.0)
        val h1Snap = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), IndicatorSnapshot(adx = 15.0, atr = 2.0))

        // M15 candles break down far below lower boundary 90.0 (closing at 82.0)
        val m15Candles = createOscillatingCandles(20, lowLevel = 90.0, highLevel = 110.0).toMutableList()
        m15Candles.add(Candle(baseTs - 900000, 89.0, 89.5, 80.0, 82.0, 300.0))
        val m15Snap = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), IndicatorSnapshot(atr = 2.0))

        val m5Candles = createCandles(5, 82.0, -0.5)
        val m5Snap = MarketSnapshot("BTC/USDT", Timeframe.M5, m5Candles, m5Candles.last(), IndicatorSnapshot(atr = 1.0))

        val context = StrategyContext(
            symbol = "BTC/USDT",
            m5Snapshot = m5Snap,
            m15Snapshot = m15Snap,
            h1Snapshot = h1Snap,
            dataTimestamp = baseTs,
            currentMarketRegime = MarketRegime.RANGE
        )

        val signal = strategy.evaluate(context, context.config)
        assertEquals(SignalDecision.REJECT, signal.decision)
    }

    // 5. Rejection on Strong H1 Trend or High ADX
    @Test
    fun testRejectionOnHighAdxOrStrongTrend() = runBlocking {
        val strategy = RangeReversalStrategy()
        val baseTs = fixedNow

        val h1Candles = createCandles(10, 100.0, 2.0)
        val h1Snap = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), IndicatorSnapshot(adx = 35.0, atr = 2.0))

        val m15Candles = createOscillatingCandles(20)
        val m15Snap = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), IndicatorSnapshot(atr = 2.0))

        val m5Candles = createCandles(5, 95.0, 0.5)
        val m5Snap = MarketSnapshot("BTC/USDT", Timeframe.M5, m5Candles, m5Candles.last(), IndicatorSnapshot(atr = 1.0))

        val context = StrategyContext(
            symbol = "BTC/USDT",
            m5Snapshot = m5Snap,
            m15Snapshot = m15Snap,
            h1Snapshot = h1Snap,
            dataTimestamp = baseTs,
            currentMarketRegime = MarketRegime.STRONG_BULL_TREND
        )

        val signal = strategy.evaluate(context, context.config)
        assertEquals(SignalDecision.REJECT, signal.decision)
        assertTrue(signal.rejectionReasons.contains(NoTradeReason.UNSUPPORTED_REGIME))
    }

    // 6. Rejection when Price is near Range Midpoint
    @Test
    fun testRejectionNearRangeMidpoint() = runBlocking {
        val strategy = RangeReversalStrategy()
        val baseTs = fixedNow

        val h1Candles = createCandles(10, 100.0, 0.0)
        val h1Snap = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), IndicatorSnapshot(adx = 15.0, atr = 2.0))

        // Range 90 to 110 -> Midpoint is 100. Price ending at 100.0
        val m15Candles = createOscillatingCandles(20, lowLevel = 90.0, highLevel = 110.0).toMutableList()
        m15Candles.add(Candle(baseTs - 900000, 99.0, 101.0, 98.5, 100.0, 100.0))
        val m15Snap = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), IndicatorSnapshot(atr = 2.0))

        val m5Candles = createCandles(5, 100.0, 0.0)
        val m5Snap = MarketSnapshot("BTC/USDT", Timeframe.M5, m5Candles, m5Candles.last(), IndicatorSnapshot(atr = 1.0))

        val context = StrategyContext(
            symbol = "BTC/USDT",
            m5Snapshot = m5Snap,
            m15Snapshot = m15Snap,
            h1Snapshot = h1Snap,
            dataTimestamp = baseTs,
            currentMarketRegime = MarketRegime.RANGE
        )

        val signal = strategy.evaluate(context, context.config)
        assertEquals(SignalDecision.REJECT, signal.decision)
    }

    // 7. Decision Threshold Tests
    @Test
    fun testDecisionThresholds() {
        val score = com.example.trading.strategy.SignalScore(
            trendAlignment = 15,
            marketStructure = 10,
            momentum = 15,
            volumeConfirmation = 10,
            volatilitySuitability = 10,
            entryQuality = 10,
            riskRewardQuality = 10
        )
        assertEquals(80, score.totalScore)
        assertEquals(SignalDecision.APPROVED, score.getDecision(50, 65, 80))

        val paperScore = score.copy(trendAlignment = 5) // Total 70
        assertEquals(70, paperScore.totalScore)
        assertEquals(SignalDecision.PAPER_TRADE, paperScore.getDecision(50, 65, 80))

        val watchScore = score.copy(trendAlignment = 0, marketStructure = 0) // Total 55
        assertEquals(55, watchScore.totalScore)
        assertEquals(SignalDecision.WATCHLIST, watchScore.getDecision(50, 65, 80))

        val rejectScore = score.copy(trendAlignment = 0, marketStructure = 0, momentum = 0) // Total 40
        assertEquals(40, rejectScore.totalScore)
        assertEquals(SignalDecision.REJECT, rejectScore.getDecision(50, 65, 80))
    }
}
