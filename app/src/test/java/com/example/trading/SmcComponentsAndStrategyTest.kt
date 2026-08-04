package com.example.trading

import com.example.trading.analysis.Candle
import com.example.trading.analysis.IndicatorSnapshot
import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.MarketSnapshot
import com.example.trading.analysis.Timeframe
import com.example.trading.analysis.smc.DealingRangeAnalyzer
import com.example.trading.analysis.smc.FairValueGapDetector
import com.example.trading.analysis.smc.FvgLifecycle
import com.example.trading.analysis.smc.LiquidityPoolDetector
import com.example.trading.analysis.smc.LiquiditySweepDetector
import com.example.trading.analysis.smc.LiquidityType
import com.example.trading.analysis.smc.MarketStructureAnalyzer
import com.example.trading.analysis.smc.MarketStructureType
import com.example.trading.analysis.smc.OrderBlockDetector
import com.example.trading.analysis.smc.OrderBlockLifecycle
import com.example.trading.analysis.smc.PremiumDiscountAnalyzer
import com.example.trading.analysis.smc.SweepType
import com.example.trading.analysis.smc.SwingDetector
import com.example.trading.analysis.smc.SwingPolicy
import com.example.trading.analysis.smc.SwingType
import com.example.trading.strategy.NoTradeReason
import com.example.trading.strategy.SignalDecision
import com.example.trading.strategy.SignalDirection
import com.example.trading.strategy.SmcLiquiditySweepConfig
import com.example.trading.strategy.SmcLiquiditySweepStrategy
import com.example.trading.strategy.StrategyConfig
import com.example.trading.strategy.StrategyContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmcComponentsAndStrategyTest {

    private val fixedNow = 1700000000000L

    private fun createCandles(count: Int, startPrice: Double, step: Double, startTs: Long = fixedNow - (count * 900000L)): List<Candle> {
        val list = mutableListOf<Candle>()
        var price = startPrice
        for (i in 0 until count) {
            val ts = startTs + (i * 900000L)
            list.add(Candle(ts, price, price + Math.abs(step) + 5.0, price - Math.abs(step) - 5.0, price + step, 100.0))
            price += step
        }
        return list
    }

    // 1. Swing Detection Tests
    @Test
    fun testConfirmedSwingHighAndLow() {
        val detector = SwingDetector()
        val baseTs = fixedNow
        val candles = listOf(
            Candle(baseTs + 0, 100.0, 102.0, 99.0, 101.0, 50.0),
            Candle(baseTs + 900000, 101.0, 103.0, 100.0, 102.0, 50.0),
            Candle(baseTs + 1800000, 102.0, 110.0, 101.0, 109.0, 100.0), // Peak at index 2
            Candle(baseTs + 2700000, 109.0, 108.0, 104.0, 105.0, 50.0),
            Candle(baseTs + 3600000, 105.0, 106.0, 102.0, 103.0, 50.0) // Confirms index 2 as Swing High at 3600000
        )

        val swings = detector.detectSwings(candles, leftBars = 2, rightBars = 2, policy = SwingPolicy.STRICT)
        assertEquals(1, swings.size)
        val sh = swings.first()
        assertEquals(SwingType.SWING_HIGH, sh.type)
        assertEquals(110.0, sh.price, 0.001)
        assertEquals(baseTs + 3600000, sh.confirmationTimestamp)
    }

    @Test
    fun testUnconfirmedPivotRejected() {
        val detector = SwingDetector()
        val baseTs = fixedNow
        val candles = listOf(
            Candle(baseTs + 0, 100.0, 102.0, 99.0, 101.0, 50.0),
            Candle(baseTs + 900000, 101.0, 103.0, 100.0, 102.0, 50.0),
            Candle(baseTs + 1800000, 102.0, 120.0, 101.0, 119.0, 100.0) // Unconfirmed peak at current tip
        )

        val swings = detector.detectSwings(candles, leftBars = 2, rightBars = 2, policy = SwingPolicy.STRICT)
        assertTrue(swings.isEmpty()) // Unconfirmed pivot must NOT be returned
    }

    // 2. Market Structure Tests
    @Test
    fun testBullishBosAndChoch() {
        val analyzer = MarketStructureAnalyzer()
        val detector = SwingDetector()

        val baseTs = fixedNow
        val candles = mutableListOf(
            Candle(baseTs + 0, 100.0, 102.0, 99.0, 101.0, 50.0),
            Candle(baseTs + 900000, 101.0, 103.0, 100.0, 102.0, 50.0),
            Candle(baseTs + 1800000, 102.0, 110.0, 101.0, 109.0, 100.0), // High at 110
            Candle(baseTs + 2700000, 109.0, 108.0, 104.0, 105.0, 50.0),
            Candle(baseTs + 3600000, 105.0, 106.0, 102.0, 103.0, 50.0),
            // Bullish break candle
            Candle(baseTs + 4500000, 103.0, 115.0, 103.0, 114.0, 200.0)
        )

        val swings = detector.detectSwings(candles, leftBars = 2, rightBars = 2)
        val result = analyzer.analyzeStructure(candles, swings, Timeframe.M15, atr = 2.0)

        assertTrue(result.recentEvents.isNotEmpty())
        val lastEvent = result.recentEvents.last()
        assertTrue(lastEvent.type == MarketStructureType.BULLISH_BOS || lastEvent.type == MarketStructureType.BULLISH_CHOCH)
        assertEquals("BULLISH", result.currentBias)
    }

    // 3. Liquidity Pool & Sweep Tests
    @Test
    fun testLiquiditySweepDetection() {
        val poolDetector = LiquidityPoolDetector()
        val sweepDetector = LiquiditySweepDetector()
        val baseTs = fixedNow

        val candles = mutableListOf(
            Candle(baseTs + 0, 100.0, 102.0, 95.0, 101.0, 50.0),
            Candle(baseTs + 900000, 101.0, 103.0, 96.0, 102.0, 50.0),
            Candle(baseTs + 1800000, 102.0, 101.0, 90.0, 92.0, 100.0), // Low at 90
            Candle(baseTs + 2700000, 92.0, 98.0, 93.0, 97.0, 50.0),
            Candle(baseTs + 3600000, 97.0, 99.0, 94.0, 98.0, 50.0),
            // Sweep candle: dips to 88 (below 90), reclaims to close at 92
            Candle(baseTs + 4500000, 98.0, 99.0, 88.0, 92.0, 150.0)
        )

        val detector = SwingDetector()
        val swings = detector.detectSwings(candles, leftBars = 2, rightBars = 2)
        val pools = poolDetector.detectPools(candles, swings, atr = 2.0)

        val sslPools = pools.filter { it.type == LiquidityType.SELL_SIDE }
        assertTrue(sslPools.isNotEmpty())

        val sweeps = sweepDetector.detectSweeps(candles, sslPools, Timeframe.M15, atr = 2.0)
        assertTrue(sweeps.isNotEmpty())
        val sweep = sweeps.first()
        assertTrue(sweep.isConfirmed)
        assertEquals(88.0, sweep.extremePrice, 0.001)
    }

    // 4. Order Block Lifecycle Tests
    @Test
    fun testOrderBlockLifecycle() {
        val obDetector = OrderBlockDetector()
        val baseTs = fixedNow

        val candles = listOf(
            Candle(baseTs + 0, 100.0, 102.0, 98.0, 99.0, 50.0), // Bearish source candle (open=100, close=99 -> bodyTop=100.0)
            Candle(baseTs + 900000, 99.0, 110.0, 99.0, 109.0, 200.0), // Displacement break candle
            Candle(baseTs + 1800000, 109.0, 108.0, 99.0, 105.0, 50.0) // Retest entering zone (98-100) with low=99.0
        )

        val structEvent = com.example.trading.analysis.smc.StructureEvent(
            type = MarketStructureType.BULLISH_BOS,
            brokenSwing = null,
            triggerCandleTimestamp = baseTs + 900000,
            confirmationTimestamp = baseTs + 900000,
            breakPrice = 110.0,
            timeframe = Timeframe.M15
        )

        val obs = obDetector.detectOrderBlocks(candles, listOf(structEvent), Timeframe.M15)
        assertEquals(1, obs.size)
        val ob = obs.first()
        assertEquals(OrderBlockLifecycle.PARTIALLY_MITIGATED, ob.state)
    }

    // 5. Fair Value Gap Tests
    @Test
    fun testFairValueGapDetection() {
        val fvgDetector = FairValueGapDetector()
        val baseTs = fixedNow

        val candles = listOf(
            Candle(baseTs + 0, 100.0, 102.0, 98.0, 101.0, 50.0), // Candle 1 High = 102.0
            Candle(baseTs + 900000, 101.0, 112.0, 101.0, 111.0, 200.0), // Candle 2 big body
            Candle(baseTs + 1800000, 111.0, 115.0, 106.0, 114.0, 100.0) // Candle 3 Low = 106.0 -> Gap 102.0 to 106.0
        )

        val fvgs = fvgDetector.detectFvgs(candles, Timeframe.M15, minGapAtrFraction = 0.05, atr = 2.0)
        assertEquals(1, fvgs.size)
        val fvg = fvgs.first()
        assertEquals(SignalDirection.LONG, fvg.direction)
        assertEquals(106.0, fvg.topPrice, 0.001)
        assertEquals(102.0, fvg.bottomPrice, 0.001)
    }

    // 6. Premium / Discount Tests
    @Test
    fun testPremiumDiscountEvaluation() {
        val analyzer = PremiumDiscountAnalyzer()
        val dealingRange = DealingRangeAnalyzer().calculateDealingRange(
            createCandles(10, 100.0, 2.0),
            emptyList(),
            Timeframe.H1
        )

        assertNotNull(dealingRange)
        val range = dealingRange!!

        // Long in discount (< 50% equilibrium) -> Acceptable
        val discountEval = analyzer.evaluatePriceLocation(range.rangeLow + (range.rangeHigh - range.rangeLow) * 0.2, range, SignalDirection.LONG)
        assertTrue(discountEval.isAcceptableForDirection)

        // Long in premium (> 50% equilibrium) -> Rejected
        val premiumEval = analyzer.evaluatePriceLocation(range.rangeLow + (range.rangeHigh - range.rangeLow) * 0.8, range, SignalDirection.LONG)
        assertTrue(!premiumEval.isAcceptableForDirection)
    }

    // 7. Full Strategy Integration Test
    @Test
    fun testValidBullishSmcStrategySetup() = runBlocking {
        val strategy = SmcLiquiditySweepStrategy()
        val baseTs = fixedNow

        // Create H1 bullish structure starting at 80.0
        val h1Candles = createCandles(20, 80.0, 2.0, baseTs - 20 * 3600000L)
        val h1Snap = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), IndicatorSnapshot(atr = 2.0))

        // Create M15 candles with sell-side sweep + bullish structure break + order block retest
        val m15Candles = mutableListOf(
            Candle(baseTs - 10800000, 98.0, 100.0, 95.0, 99.0, 50.0),
            Candle(baseTs - 9900000, 99.0, 101.0, 96.0, 100.0, 50.0),
            Candle(baseTs - 9000000, 100.0, 103.0, 95.0, 101.0, 50.0), // Swing High 103
            Candle(baseTs - 8100000, 101.0, 102.0, 96.0, 101.0, 50.0),
            Candle(baseTs - 7200000, 101.0, 102.0, 90.0, 92.0, 100.0), // Low 90 (SSL)
            Candle(baseTs - 6300000, 92.0, 98.0, 93.0, 97.0, 50.0),
            Candle(baseTs - 5400000, 97.0, 99.0, 94.0, 98.0, 50.0),
            Candle(baseTs - 4500000, 98.0, 99.0, 88.0, 92.0, 150.0), // Sweep low 88
            Candle(baseTs - 3600000, 92.0, 94.0, 89.0, 90.0, 50.0), // Bearish OB candle
            Candle(baseTs - 2700000, 90.0, 108.0, 90.0, 107.0, 300.0), // Bullish CHOCH/MSS
            Candle(baseTs - 1800000, 106.0, 107.0, 92.0, 94.0, 80.0), // Retest into OB zone 90-94
            Candle(baseTs - 900000, 94.0, 95.0, 93.5, 94.5, 100.0),
            Candle(baseTs, 94.5, 96.0, 94.0, 95.5, 100.0)
        )
        val m15Snap = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), IndicatorSnapshot(atr = 2.0))

        // M5 confirmation inside OB zone (90.0 - 94.0)
        val m5Candles = createCandles(10, 91.0, 0.15, baseTs - 10 * 300000L)
        val m5Snap = MarketSnapshot("BTC/USDT", Timeframe.M5, m5Candles, m5Candles.last(), IndicatorSnapshot(atr = 1.0))

        val context = StrategyContext(
            symbol = "BTC/USDT",
            m5Snapshot = m5Snap,
            m15Snapshot = m15Snap,
            h1Snapshot = h1Snap,
            currentSpreadPercent = 0.05,
            dataTimestamp = baseTs,
            currentMarketRegime = MarketRegime.WEAK_BULL_TREND,
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

    @Test
    fun testMissingSweepRejection() = runBlocking {
        val strategy = SmcLiquiditySweepStrategy()
        val baseTs = fixedNow

        val h1Candles = createCandles(10, 100.0, 1.0)
        val m15Candles = createCandles(10, 100.0, 1.0)
        val m5Candles = createCandles(10, 100.0, 0.5)

        val context = StrategyContext(
            symbol = "BTC/USDT",
            m5Snapshot = MarketSnapshot("BTC/USDT", Timeframe.M5, m5Candles, m5Candles.last(), IndicatorSnapshot()),
            m15Snapshot = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), IndicatorSnapshot()),
            h1Snapshot = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), IndicatorSnapshot()),
            currentSpreadPercent = 0.05,
            dataTimestamp = baseTs
        )

        val signal = strategy.evaluate(context, context.config)
        assertEquals(SignalDecision.REJECT, signal.decision)
    }
}
