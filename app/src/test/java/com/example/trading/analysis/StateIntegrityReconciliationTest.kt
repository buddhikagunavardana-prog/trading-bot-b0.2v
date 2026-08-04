package com.example.trading.analysis

import com.example.model.CryptoTicker
import com.example.service.provider.MarketDataProviderCoordinator
import com.example.trading.validation.SymbolNormalizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateIntegrityReconciliationTest {

    private fun create250Candles(timeframe: Timeframe): List<Candle> {
        val now = System.currentTimeMillis()
        val intervalMs = timeframe.minutes * 60_000L
        val currentStart = now - (now % intervalMs)
        return (1..250).map { i ->
            val openTs = currentStart - (250 - i) * intervalMs
            val closeTs = openTs + intervalMs
            Candle(
                timestamp = openTs,
                open = 50000.0 + i * 1.0,
                high = 50500.0 + i * 1.0,
                low = 49500.0 + i * 1.0,
                close = 50200.0 + i * 1.0,
                volume = 100.0,
                isFinal = true,
                sourceOrigin = CandleSourceOrigin.REST_BOOTSTRAP,
                closeTimestamp = closeTs
            )
        }
    }

    @Test
    fun testStateIntegrityReconciliation_NoViolationWhenRepoAndAlphaReconciled() = runBlocking {
        val coordinator = MarketDataProviderCoordinator()
        val btcCandlesM5 = create250Candles(Timeframe.M5)
        val btcCandlesM15 = create250Candles(Timeframe.M15)
        val btcCandlesH1 = create250Candles(Timeframe.H1)

        coordinator.candleAggregator.seedHistoricalCandles("BTC/USDT", Timeframe.M5, btcCandlesM5)
        coordinator.candleAggregator.seedHistoricalCandles("BTC/USDT", Timeframe.M15, btcCandlesM15)
        coordinator.candleAggregator.seedHistoricalCandles("BTC/USDT", Timeframe.H1, btcCandlesH1)

        coordinator.warmupTracker.updateCounts(
            symbol = "BTC/USDT",
            m5Count = 250,
            m15Count = 250,
            h1Count = 250,
            isGenuineSource = true
        )

        val tickerList = listOf(
            CryptoTicker("BTC/USDT", "Bitcoin Futures", 50200.0, 2.0, 51000.0, 49000.0, 1000000.0, rsi = 60.0, sma50 = 50000.0, sma200 = 48000.0)
        )

        val mtfMap = mapOf(
            "BTC/USDT" to coordinator.candleAggregator.buildSnapshot(tickerList[0])!!
        )

        val scanner = AlphaOpportunityScanner(warmupTracker = coordinator.warmupTracker)
        val scanResult = scanner.scanAllPairs(
            tickers = tickerList,
            isFeedConnected = true,
            isFeedStale = false,
            readinessGate = MarketReadinessGate(websocketConnected = true, bootstrapComplete = true, warmupComplete = true, snapshotComplete = true, dataFresh = true),
            mtfSnapshots = mtfMap
        )

        val btcScore = scanResult.scores.find { SymbolNormalizer.isSameSymbol(it.symbol, "BTC/USDT") }
        assertNotNull("BTC/USDT score must exist in scoreboard", btcScore)

        // Runtime Evidence Checks
        val btcRepoReady = coordinator.warmupTracker.isSymbolReady("BTC/USDT")
        assertTrue("BTC/USDT repository readiness must be TRUE", btcRepoReady)

        assertEquals("BTC/USDT alpha eligibility must be ELIGIBLE", OpportunityEligibility.ELIGIBLE, btcScore!!.eligibility)

        // Reconcile runtime state
        coordinator.reconcileRuntimeStateWithScanResult(scanResult)
        val runtimeState = coordinator.runtimeState.value

        assertFalse("Current diagnostics state: stateIntegrityViolation must be FALSE when reconciled", runtimeState.stateIntegrityViolation)
        assertNull("Current diagnostics state: integrityViolationMessage must be NULL", runtimeState.integrityViolationMessage)
        assertEquals("BTC/USDT alpha status in runtime state must be ELIGIBLE", "ELIGIBLE", runtimeState.alphaEligibility["BTC/USDT"])
    }

    @Test
    fun testStateIntegrityReconciliation_ViolationTriggeredAndExactReasonDisplayedOnMismatch() = runBlocking {
        val coordinator = MarketDataProviderCoordinator()

        // BTC is ready in warmup tracker
        coordinator.warmupTracker.updateCounts(
            symbol = "BTC/USDT",
            m5Count = 250,
            m15Count = 250,
            h1Count = 250,
            isGenuineSource = true
        )

        // Scan result where BTC ticker is missing, so Alpha Engine reports INELIGIBLE_DATA_NOT_READY
        val scanner = AlphaOpportunityScanner(warmupTracker = coordinator.warmupTracker)
        val scanResult = scanner.scanAllPairs(
            tickers = emptyList(), // Tickers missing!
            isFeedConnected = true,
            isFeedStale = false,
            readinessGate = MarketReadinessGate(websocketConnected = true, bootstrapComplete = true, warmupComplete = true, snapshotComplete = true, dataFresh = true),
            mtfSnapshots = emptyMap()
        )

        coordinator.reconcileRuntimeStateWithScanResult(scanResult)
        val runtimeState = coordinator.runtimeState.value

        assertTrue("STATE_INTEGRITY_VIOLATION must be TRUE when RepositoryReady == true but Alpha Engine is INELIGIBLE_DATA_NOT_READY", runtimeState.stateIntegrityViolation)
        assertNotNull("Integrity violation message must be present", runtimeState.integrityViolationMessage)
        assertTrue("Message must contain exact blocking reason MISSING_TICKER_DATA", runtimeState.integrityViolationMessage!!.contains("MISSING_TICKER_DATA"))

        val btcDetail = runtimeState.symbolDetails["BTC/USDT"]
        assertNotNull("BTC/USDT symbol detail must be present", btcDetail)
        assertEquals("Blocking reason in detail must match exact rejection reason", "MISSING_TICKER_DATA", btcDetail!!.blockingReason)
    }
}
