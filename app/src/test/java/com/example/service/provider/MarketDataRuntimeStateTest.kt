package com.example.service.provider

import com.example.model.CryptoTicker
import com.example.trading.analysis.*
import com.example.trading.validation.SymbolNormalizer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MarketDataRuntimeStateTest {

    private lateinit var candleAggregator: MultiTimeframeCandleAggregator
    private lateinit var warmupTracker: WarmupReadinessTracker

    @Before
    fun setUp() {
        candleAggregator = MultiTimeframeCandleAggregator()
        warmupTracker = WarmupReadinessTracker()
    }

    private fun generateClosedCandles(count: Int, timeframeMinutes: Long, refTime: Long = System.currentTimeMillis()): List<Candle> {
        val intervalMs = timeframeMinutes * 60_000L
        val end = refTime - 1000L
        val start = end - (count * intervalMs)
        return (0 until count).map { i ->
            val time = start + (i * intervalMs)
            Candle(
                timestamp = time,
                open = 50000.0 + i,
                high = 50100.0 + i,
                low = 49900.0 + i,
                close = 50050.0 + i,
                volume = 10.0,
                closeTimestamp = time + intervalMs - 1,
                providerId = "OKX_SWAP_PUBLIC"
            )
        }
    }

    @Test
    fun test_canonical_symbol_normalization() {
        assertEquals("BTC/USDT", SymbolNormalizer.toCanonicalDisplay("BTCUSDT"))
        assertEquals("BTC/USDT", SymbolNormalizer.toCanonicalDisplay("BTC-USDT-SWAP"))
        assertEquals("BTC/USDT", SymbolNormalizer.toCanonicalDisplay("BTC/USDT"))
        assertEquals("BTCUSDT", SymbolNormalizer.toExchangeSymbol("BTC-USDT-SWAP"))
    }

    @Test
    fun test_canonical_timeframe_normalization() {
        assertEquals(Timeframe.M5, Timeframe.M5)
        assertEquals(Timeframe.M15, Timeframe.M15)
        assertEquals(Timeframe.H1, Timeframe.H1)
    }

    @Test
    fun test_open_candle_filtered_out_from_commit() {
        val now = System.currentTimeMillis()
        val closedCandles = generateClosedCandles(250, 5L, refTime = now)
        val openCandle = Candle(
            timestamp = now - 100,
            open = 50000.0,
            high = 50100.0,
            low = 49900.0,
            close = 50050.0,
            volume = 1.0,
            closeTimestamp = now + 200_000L, // future close time -> open candle
            providerId = "OKX_SWAP_PUBLIC"
        )
        val mixed = closedCandles + openCandle

        val validation = CandleIntegrityValidator.validateAndDeduplicate(
            candles = mixed,
            timeframe = Timeframe.M5,
            requiredCount = 250,
            expectedProviderId = "OKX_SWAP_PUBLIC",
            nowEpochMs = now
        )

        assertTrue(validation.isValid)
        assertEquals(250, validation.validatedCandles.size)
        assertFalse(validation.validatedCandles.any { it.closeTimestamp > now })
    }

    @Test
    fun test_candle_commit_stores_exactly_250_closed_candles() {
        val candlesM5 = generateClosedCandles(250, 5L)
        candleAggregator.seedHistoricalCandles("BTC/USDT", Timeframe.M5, candlesM5)

        val retrieved = candleAggregator.getCandles("BTC/USDT", Timeframe.M5)
        assertEquals(250, retrieved.size)
    }

    @Test
    fun test_readback_verification_fails_if_candles_under_250() {
        val candlesM5 = generateClosedCandles(240, 5L)
        candleAggregator.seedHistoricalCandles("BTC/USDT", Timeframe.M5, candlesM5)

        val retrieved = candleAggregator.getCandles("BTC/USDT", Timeframe.M5)
        assertTrue(retrieved.size < 250)
    }

    @Test
    fun test_warmup_readiness_tracker_updates_correctly() {
        val status = warmupTracker.updateCounts(
            symbol = "BTC/USDT",
            m5Count = 250,
            m15Count = 250,
            h1Count = 250
        )
        assertTrue(status.isReady)
        assertTrue(warmupTracker.isSymbolReady("BTC/USDT"))
        assertEquals(100, status.readinessPercentage)
        assertNull(status.blockingReason)
    }

    @Test
    fun test_readiness_gate_approval() {
        val gate = MarketReadinessGate(
            bootstrapComplete = true,
            warmupComplete = true,
            dataFresh = true,
            genuineSourceOnly = true,
            providerType = ProviderType.OKX_SWAP_PUBLIC,
            marketDataMode = MarketDataMode.GENUINE_MARKET_DATA
        )
        assertTrue(gate.isApprovedProvider)
        assertTrue(gate.isFullyReady)
    }

    @Test
    fun test_partial_ready_emitted_when_only_subset_of_symbols_succeed() {
        warmupTracker.updateCounts("BTC/USDT", 250, 250, 250)
        warmupTracker.updateCounts("ETH/USDT", 100, 100, 100)

        assertTrue(warmupTracker.isSymbolReady("BTC/USDT"))
        assertFalse(warmupTracker.isSymbolReady("ETH/USDT"))
        assertEquals(1, warmupTracker.getReadySymbolsCount(listOf("BTC/USDT", "ETH/USDT")))
    }

    @Test
    fun test_alpha_engine_calculates_scores_when_bootstrap_status_is_ready() {
        val m5 = generateClosedCandles(250, 5L)
        val m15 = generateClosedCandles(250, 15L)
        val h1 = generateClosedCandles(250, 60L)

        candleAggregator.seedHistoricalCandles("BTC/USDT", Timeframe.M5, m5)
        candleAggregator.seedHistoricalCandles("BTC/USDT", Timeframe.M15, m15)
        candleAggregator.seedHistoricalCandles("BTC/USDT", Timeframe.H1, h1)
        warmupTracker.updateCounts("BTC/USDT", 250, 250, 250)

        val ticker = CryptoTicker("BTC/USDT", "Bitcoin", 50000.0, 1.5, 50100.0, 49900.0, 1000.0)
        val snapshot = candleAggregator.buildSnapshot(ticker)
        assertNotNull(snapshot)

        val scanner = AlphaOpportunityScanner(warmupTracker = warmupTracker)
        val gate = MarketReadinessGate(
            bootstrapComplete = true,
            warmupComplete = true,
            dataFresh = true,
            genuineSourceOnly = true,
            providerType = ProviderType.OKX_SWAP_PUBLIC,
            marketDataMode = MarketDataMode.GENUINE_MARKET_DATA
        )

        val result = scanner.scanAllPairs(
            tickers = listOf(ticker),
            isFeedConnected = true,
            isFeedStale = false,
            readinessGate = gate,
            mtfSnapshots = mapOf("BTC/USDT" to snapshot!!),
            sessionId = "TEST_SESS"
        )

        val btcScore = result.scores.find { SymbolNormalizer.isSameSymbol(it.symbol, "BTC/USDT") }
        assertNotNull(btcScore)
        assertNotEquals(OpportunityEligibility.INELIGIBLE_DATA_NOT_READY, btcScore?.eligibility)
        assertEquals(ScoreCalculationStatus.SCORE_CALCULATED, btcScore?.calculationStatus)
    }

    @Test
    fun test_alpha_engine_calculates_scores_when_bootstrap_status_is_partial_ready() {
        val m5 = generateClosedCandles(250, 5L)
        val m15 = generateClosedCandles(250, 15L)
        val h1 = generateClosedCandles(250, 60L)

        candleAggregator.seedHistoricalCandles("BTC/USDT", Timeframe.M5, m5)
        candleAggregator.seedHistoricalCandles("BTC/USDT", Timeframe.M15, m15)
        candleAggregator.seedHistoricalCandles("BTC/USDT", Timeframe.H1, h1)
        warmupTracker.updateCounts("BTC/USDT", 250, 250, 250)

        val tickerBtc = CryptoTicker("BTC/USDT", "Bitcoin", 50000.0, 1.5, 50100.0, 49900.0, 1000.0)
        val tickerEth = CryptoTicker("ETH/USDT", "Ethereum", 3000.0, 2.0, 3050.0, 2950.0, 500.0)

        val snapshotBtc = candleAggregator.buildSnapshot(tickerBtc)
        assertNotNull(snapshotBtc)

        val scanner = AlphaOpportunityScanner(warmupTracker = warmupTracker)
        val partialGate = MarketReadinessGate(
            bootstrapComplete = true,
            warmupComplete = false,
            dataFresh = true,
            genuineSourceOnly = true,
            providerType = ProviderType.OKX_SWAP_PUBLIC,
            marketDataMode = MarketDataMode.GENUINE_MARKET_DATA
        )

        val result = scanner.scanAllPairs(
            tickers = listOf(tickerBtc, tickerEth),
            isFeedConnected = true,
            isFeedStale = false,
            readinessGate = partialGate,
            mtfSnapshots = mapOf("BTC/USDT" to snapshotBtc!!),
            sessionId = "TEST_SESS"
        )

        val btcScore = result.scores.find { SymbolNormalizer.isSameSymbol(it.symbol, "BTC/USDT") }
        val ethScore = result.scores.find { SymbolNormalizer.isSameSymbol(it.symbol, "ETH/USDT") }

        assertNotNull(btcScore)
        assertEquals(ScoreCalculationStatus.SCORE_CALCULATED, btcScore?.calculationStatus)

        assertNotNull(ethScore)
        assertEquals(OpportunityEligibility.INELIGIBLE_DATA_NOT_READY, ethScore?.eligibility)
    }

    @Test
    fun test_state_integrity_violation_flagged_when_ready_symbol_reports_unavailable() {
        warmupTracker.updateCounts("BTC/USDT", 250, 250, 250)
        assertTrue(warmupTracker.isSymbolReady("BTC/USDT"))

        val mockIneligibleScore = AlphaOpportunityScore(
            symbol = "BTC/USDT",
            score = 0.0,
            direction = OpportunityDirection.NEUTRAL,
            eligibility = OpportunityEligibility.INELIGIBLE_DATA_NOT_READY,
            marketRegime = MarketRegime.UNKNOWN,
            calculationStatus = ScoreCalculationStatus.DATA_NOT_READY
        )

        val scanResult = AlphaOpportunityScanResult(
            scores = listOf(mockIneligibleScore)
        )

        val readySymbols = warmupTracker.getStatusMap().filterValues { it.isReady }.keys
        val isViolation = scanResult.scores.any { score ->
            readySymbols.contains(score.symbol) && score.eligibility == OpportunityEligibility.INELIGIBLE_DATA_NOT_READY
        }

        assertTrue(isViolation)
    }

    @Test
    fun test_all_providers_451_transitions_to_region_restricted_and_disables_alpha_scoring() {
        val gate = MarketReadinessGate(
            bootstrapComplete = false,
            warmupComplete = false,
            providerType = ProviderType.BINANCE_PUBLIC,
            blockingReason = "HTTP_451_REGION_RESTRICTED"
        )

        val scanner = AlphaOpportunityScanner(warmupTracker = warmupTracker)
        val ticker = CryptoTicker("BTC/USDT", "Bitcoin", 50000.0, 1.5, 50100.0, 49900.0, 1000.0)

        val result = scanner.scanAllPairs(
            tickers = listOf(ticker),
            isFeedConnected = false,
            isFeedStale = true,
            readinessGate = gate,
            mtfSnapshots = emptyMap(),
            sessionId = "TEST_SESS"
        )

        val score = result.scores.first()
        assertEquals(OpportunityEligibility.PROVIDER_REGION_BLOCKED, score.eligibility)
        assertEquals(ScoreCalculationStatus.PROVIDER_REGION_BLOCKED, score.calculationStatus)
    }
}
