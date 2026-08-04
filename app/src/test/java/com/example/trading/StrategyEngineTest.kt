package com.example.trading

import com.example.trading.analysis.*
import com.example.trading.risk.*
import com.example.trading.strategy.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StrategyEngineTest {

    private lateinit var dataQualityValidator: DataQualityValidator
    private lateinit var regimeDetector: MarketRegimeDetector
    private lateinit var riskEngine: RiskEngine
    private lateinit var registry: StrategyRegistry
    private lateinit var strategyEngine: StrategyEngine

    private val fixedNow = 1700000000000L // Fixed deterministic reference timestamp

    @Before
    fun setUp() {
        dataQualityValidator = DataQualityValidator(minRequiredCandles = 5)
        regimeDetector = MarketRegimeDetector()
        riskEngine = RiskEngine(RiskConfig(maxRiskPerTradePercent = 2.0, minRiskRewardRatio = 1.5))
        registry = StrategyRegistry()
        strategyEngine = StrategyEngine(
            registry = registry,
            dataQualityValidator = dataQualityValidator,
            regimeDetector = regimeDetector,
            riskEngine = riskEngine
        )
    }

    // Helper to generate deterministic valid candles
    private fun createValidCandles(count: Int, startTs: Long = fixedNow - (count * 300000L), basePrice: Double = 60000.0): List<Candle> {
        val candles = mutableListOf<Candle>()
        for (i in 0 until count) {
            val ts = startTs + (i * 300000L)
            val p = basePrice + (i * 10.0)
            candles.add(Candle(timestamp = ts, open = p, high = p + 5.0, low = p - 5.0, close = p + 2.0, volume = 100.0))
        }
        return candles
    }

    @Test
    fun testValidAndInvalidCandleData() {
        val validCandles = createValidCandles(10)
        val validSnapshot = MarketSnapshot(
            symbol = "BTC/USDT",
            timeframe = Timeframe.M5,
            candles = validCandles,
            latestCandle = validCandles.last(),
            indicators = IndicatorSnapshot(sma50 = 60000.0)
        )
        val validResult = dataQualityValidator.validateSnapshot(validSnapshot, currentTimeMs = validCandles.last().timestamp)
        assertTrue(validResult.isValid)
        assertTrue(validResult.issues.isEmpty())

        // Invalid Candle: High < Low
        val invalidCandles = validCandles.toMutableList()
        invalidCandles[2] = invalidCandles[2].copy(high = 50000.0, low = 55000.0)
        val invalidSnapshot = validSnapshot.copy(candles = invalidCandles, latestCandle = invalidCandles.last())
        val invalidResult = dataQualityValidator.validateSnapshot(invalidSnapshot, currentTimeMs = invalidCandles.last().timestamp)
        assertFalse(invalidResult.isValid)
        assertTrue(invalidResult.issues.any { it.code == "HIGH_BELOW_LOW" })
    }

    @Test
    fun testStaleMarketData() {
        val candles = createValidCandles(10, startTs = fixedNow - 10000000L) // Stale timestamp
        val snapshot = MarketSnapshot(
            symbol = "BTC/USDT",
            timeframe = Timeframe.M5,
            candles = candles,
            latestCandle = candles.last(),
            indicators = IndicatorSnapshot()
        )
        val result = dataQualityValidator.validateSnapshot(snapshot, currentTimeMs = fixedNow)
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.code == "STALE_DATA" })
    }

    @Test
    fun testMarketRegimeClassification() {
        val candles = createValidCandles(10)
        val snapshot = MarketSnapshot(
            symbol = "BTC/USDT",
            timeframe = Timeframe.M15,
            candles = candles,
            latestCandle = candles.last(),
            indicators = IndicatorSnapshot(ema50 = 61000.0, ema200 = 59000.0, adx = 30.0) // Strong Bull
        )
        val mtf = MultiTimeframeSnapshot(symbol = "BTC/USDT", m5 = snapshot, m15 = snapshot)
        val regime = regimeDetector.detectRegime(mtf)
        assertEquals(MarketRegime.STRONG_BULL_TREND, regime)
    }

    @Test
    fun testStrategyRegistrationAndDuplicateIds() {
        val strategy1 = BaselineTrendFollowStrategy()
        registry.registerStrategy(strategy1)
        assertEquals(1, registry.getAllStrategies().size)

        try {
            registry.registerStrategy(BaselineTrendFollowStrategy())
            fail("Should throw IllegalArgumentException for duplicate strategy ID")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("already registered"))
        }
    }

    @Test
    fun testStrategySelectionByRegime() {
        val strategy = BaselineTrendFollowStrategy()
        registry.registerStrategy(strategy)
        val selector = StrategySelector(registry)

        val reportBullish = selector.selectStrategies(
            symbol = "BTC/USDT",
            regime = MarketRegime.STRONG_BULL_TREND,
            availableTimeframes = setOf(Timeframe.M5, Timeframe.M15),
            isDataQualityValid = true,
            config = StrategyConfig()
        )
        assertEquals(1, reportBullish.selectedStrategies.size)

        val reportRange = selector.selectStrategies(
            symbol = "BTC/USDT",
            regime = MarketRegime.RANGE,
            availableTimeframes = setOf(Timeframe.M5, Timeframe.M15),
            isDataQualityValid = true,
            config = StrategyConfig()
        )
        assertEquals(0, reportRange.selectedStrategies.size)
        assertTrue(reportRange.rejectedStrategiesWithReasons.containsKey(strategy.id))
    }

    @Test
    fun testSignalScoreBoundaries() {
        val scoreOverflow = SignalScore(
            trendAlignment = 25,
            marketStructure = 20,
            momentum = 20,
            volumeConfirmation = 20,
            volatilitySuitability = 20,
            entryQuality = 20,
            riskRewardQuality = 20,
            aiAdvisory = 10
        )
        assertEquals(100, scoreOverflow.totalScore) // Bounded at 100

        val scoreUnderflow = SignalScore(trendAlignment = -10)
        assertEquals(0, scoreUnderflow.totalScore) // Bounded at 0
    }

    @Test
    fun testInvalidSlTpRelationships() {
        val accountState = AccountRiskState(totalEquityUsdt = 10000.0)
        // LONG trade with SL above Entry
        val decision = riskEngine.validateTradeRisk(
            symbol = "BTC/USDT",
            direction = SignalDirection.LONG,
            entryPrice = 60000.0,
            stopLossPrice = 61000.0, // WRONG SIDE
            takeProfitPrice = 65000.0,
            accountState = accountState
        )
        assertFalse(decision.isApproved)
        assertTrue(decision.rejectionReasons.contains(RiskRejectionReason.STOP_LOSS_WRONG_SIDE))
    }

    @Test
    fun testMinimumRiskRewardRejection() {
        val accountState = AccountRiskState(totalEquityUsdt = 10000.0)
        // Entry 60000, SL 59000 (Risk 1000), TP 60500 (Reward 500) -> R:R = 0.5 (< 1.5 min)
        val decision = riskEngine.validateTradeRisk(
            symbol = "BTC/USDT",
            direction = SignalDirection.LONG,
            entryPrice = 60000.0,
            stopLossPrice = 59000.0,
            takeProfitPrice = 60500.0,
            accountState = accountState
        )
        assertFalse(decision.isApproved)
        assertTrue(decision.rejectionReasons.contains(RiskRejectionReason.POOR_RISK_REWARD))
    }

    @Test
    fun testPositionSizeCalculation() {
        val units = PositionSizer.calculatePositionSize(
            accountEquityUsdt = 10000.0,
            riskPercent = 2.0, // $200 risk
            entryPrice = 60000.0,
            stopLossPrice = 58000.0 // $2000 per unit risk
        )
        assertEquals(0.1, units, 0.0001) // $200 / $2000 = 0.1 BTC
    }

    @Test
    fun testDailyLossRejection() {
        val accountState = AccountRiskState(
            totalEquityUsdt = 10000.0,
            dailyRealizedPnlUsdt = -600.0 // Max loss is 500
        )
        val decision = riskEngine.validateTradeRisk(
            symbol = "BTC/USDT",
            direction = SignalDirection.LONG,
            entryPrice = 60000.0,
            stopLossPrice = 58000.0,
            takeProfitPrice = 64000.0,
            accountState = accountState
        )
        assertFalse(decision.isApproved)
        assertTrue(decision.rejectionReasons.contains(RiskRejectionReason.DAILY_LOSS_LIMIT_REACHED))
    }

    @Test
    fun testCooldownRejection() {
        val accountState = AccountRiskState(
            totalEquityUsdt = 10000.0,
            lastLossTimestampMap = mapOf("BTC/USDT" to fixedNow - 300000L) // Loss 5 mins ago (< 30m cooldown)
        )
        val decision = riskEngine.validateTradeRisk(
            symbol = "BTC/USDT",
            direction = SignalDirection.LONG,
            entryPrice = 60000.0,
            stopLossPrice = 58000.0,
            takeProfitPrice = 64000.0,
            accountState = accountState,
            currentTimeMs = fixedNow
        )
        assertFalse(decision.isApproved)
        assertTrue(decision.rejectionReasons.contains(RiskRejectionReason.COOLDOWN_ACTIVE))
    }

    @Test
    fun testConflictingTimeframeRejection() {
        val m5Candles = createValidCandles(10, startTs = fixedNow - 3000000L)
        val m15Candles = createValidCandles(10, startTs = fixedNow - 3000000L)

        val m5 = MarketSnapshot("BTC/USDT", Timeframe.M5, m5Candles, m5Candles.last(), IndicatorSnapshot(ema50 = 61000.0, ema200 = 59000.0, adx = 15.0))
        val m15 = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), IndicatorSnapshot(ema50 = 58000.0, ema200 = 60000.0, adx = 15.0))
        val mtf = MultiTimeframeSnapshot("BTC/USDT", m5, m15)

        val regime = regimeDetector.detectRegime(mtf)
        assertEquals(MarketRegime.UNSTABLE, regime) // Conflicting M5/M15 trend direction with weak ADX -> UNSTABLE
    }

    @Test
    fun testStrategyEngineNoTradeResultOnStaleData() = runBlocking {
        val candles = createValidCandles(10, startTs = fixedNow - 10000000L)
        val snapshot = MarketSnapshot("BTC/USDT", Timeframe.M15, candles, candles.last(), IndicatorSnapshot())
        val mtf = MultiTimeframeSnapshot("BTC/USDT", snapshot, snapshot)

        val result = strategyEngine.evaluateSymbol(mtfSnapshot = mtf, currentTimeMs = fixedNow)
        assertNull(result.bestCandidate)
        assertTrue(result.noTradeReasons.contains(NoTradeReason.STALE_DATA))
    }

    @Test
    fun testStrategyEngineSuccessfulPaperTradeCandidate() = runBlocking {
        registry.registerStrategy(BaselineTrendFollowStrategy())

        val candles = createValidCandles(10, startTs = fixedNow - 3000000L, basePrice = 60000.0)
        val ind = IndicatorSnapshot(
            ema50 = 61000.0,
            ema200 = 58000.0,
            adx = 35.0,
            atr = 1000.0,
            atrPercent = 1.6,
            rsi = 55.0,
            supportPrice = 58000.0,
            resistancePrice = 64000.0,
            volumeSma20 = 50.0
        )
        val m5Snapshot = MarketSnapshot("BTC/USDT", Timeframe.M5, candles, candles.last(), ind)
        val m15Snapshot = MarketSnapshot("BTC/USDT", Timeframe.M15, candles, candles.last(), ind)
        val mtf = MultiTimeframeSnapshot("BTC/USDT", m5Snapshot, m15Snapshot)

        val result = strategyEngine.evaluateSymbol(
            mtfSnapshot = mtf,
            accountState = AccountRiskState(totalEquityUsdt = 10000.0),
            currentTimeMs = fixedNow
        )

        assertEquals(MarketRegime.STRONG_BULL_TREND, result.detectedRegime)
        assertNotNull(result.bestCandidate)
        val candidate = result.bestCandidate!!
        assertEquals("BTC/USDT", candidate.symbol)
        assertEquals(SignalDirection.LONG, candidate.direction)
        assertTrue(candidate.isPaperTradeEligible)
        assertTrue(candidate.finalScore >= 65)
    }
}
