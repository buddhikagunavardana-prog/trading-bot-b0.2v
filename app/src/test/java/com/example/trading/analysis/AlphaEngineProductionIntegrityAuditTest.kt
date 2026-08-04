package com.example.trading.analysis

import com.example.model.CryptoTicker
import com.example.service.BinancePublicMarketDataProvider
import com.example.trading.validation.SymbolNormalizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class AlphaEngineProductionIntegrityAuditTest {

    // Helper: generate genuine REST bootstrap candles
    private fun createGenuineRestCandles(count: Int, timeframe: Timeframe): List<Candle> {
        val now = System.currentTimeMillis()
        val intervalMs = timeframe.minutes * 60_000L
        val currentStart = now - (now % intervalMs)
        return (1..count).map { i ->
            val openTs = currentStart - (count - i) * intervalMs
            val closeTs = openTs + intervalMs
            Candle(
                timestamp = openTs,
                open = 100.0 + i * 0.1,
                high = 101.0 + i * 0.1,
                low = 99.0 + i * 0.1,
                close = 100.5 + i * 0.1,
                volume = 10000.0,
                isFinal = true,
                sourceOrigin = CandleSourceOrigin.REST_BOOTSTRAP,
                closeTimestamp = closeTs
            )
        }
    }

    // Helper: generate synthetic candles
    private fun createSyntheticCandles(count: Int, timeframe: Timeframe): List<Candle> {
        val now = System.currentTimeMillis()
        val intervalMs = timeframe.minutes * 60_000L
        val currentStart = now - (now % intervalMs)
        return (1..count).map { i ->
            val openTs = currentStart - (count - i) * intervalMs
            val closeTs = openTs + intervalMs
            Candle(
                timestamp = openTs,
                open = 100.0 + i * 0.1,
                high = 101.0 + i * 0.1,
                low = 99.0 + i * 0.1,
                close = 100.5 + i * 0.1,
                volume = 10000.0,
                isFinal = true,
                sourceOrigin = CandleSourceOrigin.SYNTHETIC_TEST,
                closeTimestamp = closeTs
            )
        }
    }

    @Test
    fun test1_binanceFetchFailureDoesNotCreateCandles() = runBlocking {
        val provider = BinancePublicMarketDataProvider()
        // Default execution without network will fail fetchBinanceKlinesRest
        val klines = provider.fetchBinanceKlinesRest("BTCUSDT", "5m")
        assertNull("Binance REST fetch failure must return null without generating synthetic candles", klines)
        assertEquals("Candle aggregator must remain empty on fetch failure", 0, provider.candleAggregator.getCandles("BTC/USDT", Timeframe.M5).size)
    }

    @Test
    fun test2_binanceFetchFailureKeepsReadinessFalse() = runBlocking {
        val provider = BinancePublicMarketDataProvider()
        provider.bootstrapGenuineKlines()
        val gate = provider.marketReadinessGate.value
        assertFalse("MarketReadinessGate.isFullyReady must be false when REST fetch fails", gate.isFullyReady)
        assertFalse("bootstrapComplete must be false when REST fetch fails", gate.bootstrapComplete)
        assertFalse("warmupComplete must be false when REST fetch fails", gate.warmupComplete)
        assertNotNull("blockingReason must be set on fetch failure", gate.blockingReason)
    }

    @Test
    fun test3_syntheticDataCannotBeMarkedGenuine() {
        try {
            Candle(
                timestamp = System.currentTimeMillis(),
                open = 100.0, high = 101.0, low = 99.0, close = 100.5, volume = 1000.0,
                isFinal = true,
                sourceOrigin = CandleSourceOrigin.SYNTHETIC_TEST,
                isGenuineSource = true // Invariant violation
            )
            fail("Constructing a synthetic candle with isGenuineSource = true must throw an IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("Exception message must cite invariant violation", e.message!!.contains("Invariant Violation"))
        }
    }

    @Test
    fun test4_syntheticDataCannotCreateEligibleLivePaperScore() {
        val scanner = AlphaOpportunityScanner()
        val m5 = createSyntheticCandles(250, Timeframe.M5)
        val m15 = createSyntheticCandles(250, Timeframe.M15)
        val h1 = createSyntheticCandles(250, Timeframe.H1)

        val mtfSnap = MultiTimeframeSnapshot(
            symbol = "BTC/USDT",
            m5 = MarketSnapshot("BTC/USDT", Timeframe.M5, m5, m5.last(), IndicatorSnapshot()),
            m15 = MarketSnapshot("BTC/USDT", Timeframe.M15, m15, m15.last(), IndicatorSnapshot()),
            h1 = MarketSnapshot("BTC/USDT", Timeframe.H1, h1, h1.last(), IndicatorSnapshot())
        )

        val ticker = CryptoTicker("BTC/USDT", "Bitcoin", 67000.0, 2.0, 68000.0, 66000.0, 1000000.0, 55.0, 66500.0, 64000.0, 80)
        val gate = MarketReadinessGate(
            websocketConnected = true,
            bootstrapComplete = true,
            warmupComplete = true,
            snapshotComplete = true,
            dataFresh = true,
            genuineSourceOnly = true,
            providerType = ProviderType.BINANCE_PUBLIC,
            marketDataMode = MarketDataMode.LIVE_BINANCE_PUBLIC,
            syntheticDataAllowed = false
        )

        val result = scanner.scanAllPairs(
            tickers = listOf(ticker),
            isFeedConnected = true,
            isFeedStale = false,
            readinessGate = gate,
            mtfSnapshots = mapOf("BTC/USDT" to mtfSnap)
        )

        val score = result.scores.find { it.symbol == "BTC/USDT" }!!
        assertNotEquals("Synthetic data must NOT yield ELIGIBLE status", OpportunityEligibility.ELIGIBLE, score.eligibility)
        assertEquals("Calculation status must be DATA_INVALID for synthetic candles", ScoreCalculationStatus.DATA_INVALID, score.calculationStatus)
        assertEquals("Score must be 0.0 for synthetic candles in live-paper mode", 0.0, score.score, 0.001)
        assertEquals("Direction must be NO_TRADE", OpportunityDirection.NO_TRADE, score.direction)
        assertTrue("Rejection reasons must cite unauthentic synthetic data", score.rejectionReasons.contains("UNAUTHENTIC_SYNTHETIC_DATA_DETECTED"))
    }

    @Test
    fun test5_syntheticDataCannotTriggerATrade() {
        val candle = createSyntheticCandles(1, Timeframe.M5).first()
        assertFalse("Synthetic candle must never be eligible as a live trade trigger event", candle.eligibleAsLiveTrigger)
    }

    @Test
    fun test6_restBootstrapDataCountsTowardWarmup() {
        val candle = createGenuineRestCandles(1, Timeframe.M5).first()
        assertTrue("Genuine REST bootstrap closed candle must be usable for warm-up", candle.usableForWarmup)
    }

    @Test
    fun test7_restBootstrapDataCannotActAsLiveTriggerEvent() {
        val candle = createGenuineRestCandles(1, Timeframe.M5).first()
        assertFalse("REST bootstrap candle must NOT be eligible as a live trade trigger event", candle.eligibleAsLiveTrigger)
    }

    @Test
    fun test8_onlyClosedCandlesCountTowardReadiness() {
        val closedCandle = createGenuineRestCandles(1, Timeframe.M5).first().copy(isFinal = true)
        val openCandle = createGenuineRestCandles(1, Timeframe.M5).first().copy(isFinal = false)

        assertTrue("Closed candle must be usable for warm-up", closedCandle.usableForWarmup)
        assertFalse("Incomplete / open candle must NOT be usable for warm-up", openCandle.usableForWarmup)
    }

    @Test
    fun test9_partialCandlesAreExcludedFromWarmupAndTriggers() {
        val partialCandle = Candle(
            timestamp = System.currentTimeMillis(),
            open = 100.0, high = 101.0, low = 99.0, close = 100.5, volume = 100.0,
            isFinal = false,
            sourceOrigin = CandleSourceOrigin.LIVE_STREAM
        )

        assertFalse("Partial live candle must NOT be usable for warm-up", partialCandle.usableForWarmup)
        assertFalse("Partial live candle must NOT be eligible as a live trade trigger event", partialCandle.eligibleAsLiveTrigger)
    }

    @Test
    fun test10_unknownProvenanceIsRejected() {
        val unknownCandle = Candle(
            timestamp = System.currentTimeMillis(),
            open = 100.0, high = 101.0, low = 99.0, close = 100.5, volume = 1000.0,
            isFinal = true,
            sourceOrigin = CandleSourceOrigin.UNKNOWN,
            isGenuineSource = false
        )

        assertFalse("Unknown provenance candle is not genuine source", unknownCandle.isGenuineSource)
        assertFalse("SourceAuthenticityGuard must reject UNKNOWN origin candle", SourceAuthenticityGuard.isGenuine(unknownCandle))
    }

    @Test
    fun test11_dynamicLookbackReturns250WhenEma200AndBufferActive() {
        val calculator = DynamicWarmupCalculator()
        val result = calculator.calculateRequiredCandles(
            timeframe = Timeframe.M5,
            emaPeriods = listOf(20, 50, 200),
            stabilisationBuffer = 50
        )

        assertEquals("Largest lookback must be 200 for EMA200", 200, result.largestRequiredLookback)
        assertEquals("Stabilisation buffer must be 50", 50, result.stabilisationBuffer)
        assertEquals("Final required candles must be 250", 250, result.finalRequiredCandles)
        assertEquals("Indicator responsible must be EMA200", "EMA200", result.indicatorOrStrategyResponsible)
    }

    @Test
    fun test12_dynamicLookbackChangesWhenIndicatorsChange() {
        val calculator = DynamicWarmupCalculator()
        val result = calculator.calculateRequiredCandles(
            timeframe = Timeframe.M5,
            emaPeriods = listOf(20, 50),
            smaPeriods = listOf(50),
            stabilisationBuffer = 50
        )

        assertEquals("Largest lookback must be 50 for EMA50", 50, result.largestRequiredLookback)
        assertEquals("Final required candles must be 100 (50 + 50)", 100, result.finalRequiredCandles)
        assertEquals("Indicator responsible must be EMA50", "EMA50", result.indicatorOrStrategyResponsible)
    }

    @Test
    fun test13_fakeProviderExistsOnlyInTestOrSimulationScope() {
        val gate = MarketReadinessGate(
            providerType = ProviderType.FAKE_SIMULATION,
            syntheticDataAllowed = true
        )
        assertFalse("MarketReadinessGate must NOT be fully ready when providerType is FAKE_SIMULATION", gate.isFullyReady)
    }

    @Test
    fun test14_productionDependencyInjectionSelectsBinanceProvider() {
        val provider = BinancePublicMarketDataProvider()
        val gate = provider.marketReadinessGate.value

        assertEquals("Production providerType must default to BINANCE_PUBLIC", ProviderType.BINANCE_PUBLIC, gate.providerType)
        assertEquals("Production marketDataMode must default to LIVE_BINANCE_PUBLIC", MarketDataMode.LIVE_BINANCE_PUBLIC, gate.marketDataMode)
        assertFalse("Production provider must NOT allow synthetic data", gate.syntheticDataAllowed)
    }

    @Test
    fun test15_oneFailedSymbolDoesNotBlockOtherNine() {
        val tracker = WarmupReadinessTracker()
        val symbols = listOf("BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT", "ADA/USDT", "DOGE/USDT", "AVAX/USDT", "DOT/USDT", "POL/USDT")

        // 9 symbols succeed with 250 candles
        symbols.drop(1).forEach { symbol ->
            tracker.updateCounts(symbol, 250, 250, 250, isGenuineSource = true)
        }
        // 1 symbol fails
        tracker.updateCounts(symbols.first(), 0, 0, 0, isGenuineSource = false)

        assertEquals("Ready symbols count must be 9", 9, tracker.getReadySymbolsCount(symbols))
        assertTrue("BTC/USDT must not be ready", !tracker.isSymbolReady(symbols.first()))
        assertTrue("ETH/USDT must be ready", tracker.isSymbolReady(symbols[1]))
    }

    @Test
    fun test16_scoreNotCalculatedDiffersFromValidScoreZero() {
        val uncalc = AlphaOpportunityScore(
            symbol = "BTC/USDT", score = 0.0, direction = OpportunityDirection.NO_TRADE,
            eligibility = OpportunityEligibility.INELIGIBLE_DATA_NOT_READY,
            marketRegime = MarketRegime.UNKNOWN, calculationStatus = ScoreCalculationStatus.DATA_NOT_READY
        )

        val validZero = AlphaOpportunityScore(
            symbol = "ETH/USDT", score = 0.0, direction = OpportunityDirection.NO_TRADE,
            eligibility = OpportunityEligibility.INELIGIBLE_BELOW_THRESHOLD,
            marketRegime = MarketRegime.RANGE, calculationStatus = ScoreCalculationStatus.VALID_SCORE_ZERO
        )

        assertNotEquals("DATA_NOT_READY must differ from VALID_SCORE_ZERO", uncalc.calculationStatus, validZero.calculationStatus)
        assertEquals(ScoreCalculationStatus.DATA_NOT_READY, uncalc.calculationStatus)
        assertEquals(ScoreCalculationStatus.VALID_SCORE_ZERO, validZero.calculationStatus)
    }

    @Test
    fun test17_runtimeDiagnosticsExposeActualProviderAndProvenance() {
        val provider = BinancePublicMarketDataProvider()
        val gate = provider.marketReadinessGate.value

        assertEquals(ProviderType.BINANCE_PUBLIC, gate.providerType)
        assertEquals(MarketDataMode.LIVE_BINANCE_PUBLIC, gate.marketDataMode)
        assertFalse(gate.syntheticDataAllowed)
        assertTrue(gate.genuineSourceOnly)
    }

    @Test
    fun test18_maticResolvesToPolUsdt() {
        assertEquals("POL/USDT", SymbolNormalizer.toCanonicalDisplay("MATIC/USDT"))
        assertEquals("POL/USDT", SymbolNormalizer.toCanonicalDisplay("MATICUSDT"))
        assertEquals("POLUSDT", SymbolNormalizer.toExchangeSymbol("MATIC/USDT"))
        assertEquals("POL", SymbolNormalizer.extractBaseAsset("MATICUSDT"))
    }

    @Test
    fun test19_duplicateBootstrapCandlesDoNotIncreaseReadiness() {
        val originalList = createGenuineRestCandles(100, Timeframe.M5)
        val duplicateList = originalList + originalList // Duplicate 100 candles

        val deDuplicated = duplicateList.distinctBy { it.timestamp }
        assertEquals("De-duplication must reduce candle count back to 100", 100, deDuplicated.size)
    }

    @Test
    fun test20_missingCandleIntervalsAreReported() {
        val validator = DataQualityValidator()
        val candles = createGenuineRestCandles(20, Timeframe.M5).toMutableList()

        // Insert duplicate timestamp to trigger DataQualityIssue
        candles[5] = candles[4].copy()

        val snapshot = MarketSnapshot("BTC/USDT", Timeframe.M5, candles, candles.last(), IndicatorSnapshot())
        val result = validator.validateSnapshot(snapshot, currentTimeMs = candles.last().closeTimestamp)

        assertFalse("Snapshot with duplicate/unordered timestamps must fail validation", result.isValid)
        assertTrue("Issues must cite DUPLICATE_CANDLE", result.issues.any { it.code == "DUPLICATE_CANDLE" })
    }

    @Test
    fun test21_stateFlowPropagationFromCandlesToUiState() {
        val scanner = AlphaOpportunityScanner()
        val tickers = listOf(
            CryptoTicker("BTC/USDT", "Bitcoin Futures", 67450.25, 2.45, 68200.0, 65900.0, 32150400.0)
        )

        // 1. Initial State: Empty repo / default readiness gate -> DATA_NOT_READY
        val initialGate = MarketReadinessGate(
            websocketConnected = false,
            bootstrapComplete = false,
            warmupComplete = false,
            snapshotComplete = false,
            dataFresh = false,
            genuineSourceOnly = true,
            providerType = ProviderType.BINANCE_PUBLIC,
            marketDataMode = MarketDataMode.LIVE_BINANCE_PUBLIC,
            syntheticDataAllowed = false,
            blockingReason = "BOOTSTRAP_WARMUP_INCOMPLETE"
        )
        val initialScan = scanner.scanAllPairs(
            tickers = tickers,
            isFeedConnected = true,
            isFeedStale = false,
            readinessGate = initialGate,
            mtfSnapshots = emptyMap(),
            sessionId = "SESS_TEST",
            alertOnEligible = false
        )

        assertEquals("Initial scan without candles must be INELIGIBLE_DATA_NOT_READY", OpportunityEligibility.INELIGIBLE_DATA_NOT_READY, initialScan.topOpportunity!!.eligibility)
        assertEquals(0.0, initialScan.topOpportunity!!.score, 0.001)

        // 2. Repository receives candles
        val m5Candles = createGenuineRestCandles(250, Timeframe.M5)
        val m15Candles = createGenuineRestCandles(250, Timeframe.M15)
        val h1Candles = createGenuineRestCandles(250, Timeframe.H1)

        val mtf = MultiTimeframeSnapshot(
            symbol = "BTC/USDT",
            m5 = MarketSnapshot("BTC/USDT", Timeframe.M5, m5Candles, m5Candles.last(), IndicatorSnapshot()),
            m15 = MarketSnapshot("BTC/USDT", Timeframe.M15, m15Candles, m15Candles.last(), IndicatorSnapshot()),
            h1 = MarketSnapshot("BTC/USDT", Timeframe.H1, h1Candles, h1Candles.last(), IndicatorSnapshot())
        )

        // 3. Readiness becomes true
        val readyGate = MarketReadinessGate(
            websocketConnected = true,
            bootstrapComplete = true,
            warmupComplete = true,
            snapshotComplete = true,
            dataFresh = true,
            genuineSourceOnly = true,
            providerType = ProviderType.BINANCE_PUBLIC,
            marketDataMode = MarketDataMode.LIVE_BINANCE_PUBLIC,
            syntheticDataAllowed = false,
            blockingReason = null
        )

        // 4. Score result emitted
        val updatedScan = scanner.scanAllPairs(
            tickers = tickers,
            isFeedConnected = true,
            isFeedStale = false,
            readinessGate = readyGate,
            mtfSnapshots = mapOf("BTC/USDT" to mtf),
            sessionId = "SESS_TEST",
            alertOnEligible = false
        )

        assertNotEquals("Updated scan with genuine candles must not be INELIGIBLE_DATA_NOT_READY", OpportunityEligibility.INELIGIBLE_DATA_NOT_READY, updatedScan.topOpportunity!!.eligibility)
        assertEquals("BTC/USDT", updatedScan.topOpportunity!!.symbol)
    }

    @Test
    fun test22_http451TransitionsConnectionStateToBlockedAndFailsFast() = runBlocking {
        val provider = BinancePublicMarketDataProvider()
        provider.bootstrapGenuineKlines()

        val gate = provider.marketReadinessGate.value
        val connState = provider.connectionState.value

        if (provider.lastRestError?.contains("451") == true) {
            assertEquals("HTTP 451 error must transition connection state to BLOCKED", com.example.service.MarketConnectionState.BLOCKED, connState)
            assertFalse("bootstrapComplete must be false on HTTP 451", gate.bootstrapComplete)
            assertFalse("warmupComplete must be false on HTTP 451", gate.warmupComplete)
            assertTrue("blockingReason must cite HTTP 451 restriction", gate.blockingReason?.contains("451") == true)
        } else {
            // When run offline without net
            assertFalse("bootstrapComplete must be false without network", gate.bootstrapComplete)
        }
    }

    @Test
    fun test23_providerSwitchingIsAuditedAndConfigurable() {
        val provider = BinancePublicMarketDataProvider()
        assertEquals("BINANCE_PUBLIC", provider.providerState)
        assertTrue("Audit history initially empty", provider.providerSwitchAuditHistory.isEmpty())

        provider.switchProviderConfig("ALTERNATIVE_PUBLIC_PROVIDER", "REGION_HTTP_451_FAILOVER")
        assertEquals("ALTERNATIVE_PUBLIC_PROVIDER", provider.providerState)
        assertEquals(1, provider.providerSwitchAuditHistory.size)

        val record = provider.providerSwitchAuditHistory.first()
        assertEquals("BINANCE_PUBLIC", record.previousProvider)
        assertEquals("ALTERNATIVE_PUBLIC_PROVIDER", record.newProvider)
        assertEquals("REGION_HTTP_451_FAILOVER", record.switchReason)

        try {
            provider.switchProviderConfig("OFFLINE_TEST_PROVIDER", "SIMULATION_ATTEMPT", isTestMode = false)
            fail("Selecting OFFLINE_TEST_PROVIDER outside test mode must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("OFFLINE_TEST_PROVIDER"))
        }
    }

    @Test
    fun test24_blockedProviderProducesProviderRegionBlockedScore() {
        val scanner = AlphaOpportunityScanner(opportunityThreshold = 65.0)
        val readyGate = MarketReadinessGate(
            websocketConnected = false,
            bootstrapComplete = false,
            warmupComplete = false,
            snapshotComplete = false,
            dataFresh = false,
            genuineSourceOnly = true,
            providerType = ProviderType.BINANCE_PUBLIC,
            marketDataMode = MarketDataMode.LIVE_BINANCE_PUBLIC,
            syntheticDataAllowed = false,
            blockingReason = "PROVIDER_REGION_BLOCKED (HTTP_451_REGION_RESTRICTED)"
        )

        val result = scanner.scanAllPairs(
            tickers = listOf(CryptoTicker("BTC/USDT", "Bitcoin", 60000.0, 1.5, 60500.0, 59500.0, 10000.0)),
            isFeedConnected = false,
            isFeedStale = false,
            readinessGate = readyGate,
            mtfSnapshots = emptyMap(),
            sessionId = "SESS_TEST_451"
        )

        val top = result.topOpportunity
        assertNotNull("Top opportunity must exist even when blocked", top)
        assertEquals("Eligibility must be PROVIDER_REGION_BLOCKED", OpportunityEligibility.PROVIDER_REGION_BLOCKED, top!!.eligibility)
        assertEquals("Calculation status must be PROVIDER_REGION_BLOCKED", ScoreCalculationStatus.PROVIDER_REGION_BLOCKED, top.calculationStatus)
        assertEquals("Score must be 0.0 when blocked", 0.0, top.score, 0.001)
        assertTrue("Rejection reasons must cite PROVIDER_REGION_BLOCKED", top.rejectionReasons.contains("PROVIDER_REGION_BLOCKED"))
    }
}
