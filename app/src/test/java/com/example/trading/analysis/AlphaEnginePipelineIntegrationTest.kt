package com.example.trading.analysis

import com.example.model.CryptoTicker
import com.example.trading.validation.SymbolMetadataManager
import com.example.trading.validation.SymbolNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AlphaEnginePipelineIntegrationTest {

    @Test
    fun testSymbolNormalizerAndPolMigration() {
        assertEquals("POL/USDT", SymbolNormalizer.toCanonicalDisplay("MATIC/USDT"))
        assertEquals("POL/USDT", SymbolNormalizer.toCanonicalDisplay("MATICUSDT"))
        assertEquals("POLUSDT", SymbolNormalizer.toExchangeSymbol("MATIC/USDT"))
        assertEquals("BTC/USDT", SymbolNormalizer.toCanonicalDisplay("BTCUSDT"))
        assertEquals("BTCUSDT", SymbolNormalizer.toExchangeSymbol("BTC/USDT"))
        assertTrue(SymbolNormalizer.isSameSymbol("MATIC/USDT", "POL/USDT"))

        val metadataManager = SymbolMetadataManager()
        val polMeta = metadataManager.getSymbolMetadata("MATIC/USDT")
        assertNotNull(polMeta)
        assertEquals("POLUSDT", polMeta?.exchangeSymbol)
    }

    @Test
    fun testWarmupReadinessTrackerThresholds() {
        val tracker = WarmupReadinessTracker()
        val symbol = "BTC/USDT"

        tracker.updateCounts(symbol, m5Count = 200, m15Count = 200, h1Count = 200, isGenuineSource = true)
        assertFalse(tracker.isSymbolReady(symbol))

        tracker.updateCounts(symbol, m5Count = 250, m15Count = 250, h1Count = 250, isGenuineSource = true)
        assertTrue(tracker.isSymbolReady(symbol))
    }

    @Test
    fun testIndicatorCalculationAndSnapshotBuilding() {
        val aggregator = MultiTimeframeCandleAggregator()
        val symbol = "BTC/USDT"
        val now = System.currentTimeMillis()

        // Seed M5 historical candles
        val m5Candles = (1..60).map { i ->
            val ts = now - (60 - i) * 300_000L
            val p = 60000.0 + i * 10.0
            Candle(
                timestamp = ts,
                open = p - 5.0,
                high = p + 15.0,
                low = p - 10.0,
                close = p,
                volume = 1000.0 + i * 50.0,
                isFinal = true,
                sourceOrigin = CandleSourceOrigin.REST_BOOTSTRAP,
                closeTimestamp = ts + 300_000L
            )
        }

        // Seed M15 historical candles
        val m15Candles = (1..35).map { i ->
            val ts = now - (35 - i) * 900_000L
            val p = 60000.0 + i * 20.0
            Candle(
                timestamp = ts,
                open = p - 10.0,
                high = p + 25.0,
                low = p - 15.0,
                close = p,
                volume = 3000.0 + i * 100.0,
                isFinal = true,
                sourceOrigin = CandleSourceOrigin.REST_BOOTSTRAP,
                closeTimestamp = ts + 900_000L
            )
        }

        // Seed H1 historical candles
        val h1Candles = (1..15).map { i ->
            val ts = now - (15 - i) * 3600_000L
            val p = 60000.0 + i * 50.0
            Candle(
                timestamp = ts,
                open = p - 20.0,
                high = p + 50.0,
                low = p - 30.0,
                close = p,
                volume = 10000.0 + i * 200.0,
                isFinal = true,
                sourceOrigin = CandleSourceOrigin.REST_BOOTSTRAP,
                closeTimestamp = ts + 3600_000L
            )
        }

        aggregator.seedHistoricalCandles(symbol, Timeframe.M5, m5Candles)
        aggregator.seedHistoricalCandles(symbol, Timeframe.M15, m15Candles)
        aggregator.seedHistoricalCandles(symbol, Timeframe.H1, h1Candles)

        val ticker = CryptoTicker(
            symbol = symbol,
            name = "Bitcoin",
            price = 60600.0,
            change24h = 3.5,
            high24h = 61000.0,
            low24h = 59500.0,
            volume = 50000000.0,
            rsi = 62.0,
            sma50 = 60200.0,
            sma200 = 58000.0,
            aiScore = 85,
            priceHistory = m5Candles.map { it.close }
        )

        val snapshot = aggregator.buildSnapshot(ticker)
        assertNotNull(snapshot)
        assertNotNull(snapshot?.m5)
        assertNotNull(snapshot?.m15)
        assertNotNull(snapshot?.h1)

        val m5Ind = snapshot!!.m5!!.indicators
        assertTrue(m5Ind.ema50 > 0.0)
        assertTrue(m5Ind.rsi > 0.0)
        assertTrue(m5Ind.atr > 0.0)
    }

    @Test
    fun testRegimeDetectorAndAlphaScanner() {
        val warmupTracker = WarmupReadinessTracker()
        val scanner = AlphaOpportunityScanner(warmupTracker = warmupTracker)

        val tickers = scanner.canonicalUniverse.map { raw ->
            val canonical = SymbolNormalizer.toCanonicalDisplay(raw)
            CryptoTicker(
                symbol = canonical,
                name = raw,
                price = 100.0,
                change24h = 2.5,
                high24h = 105.0,
                low24h = 95.0,
                volume = 10000000.0,
                rsi = 60.0,
                sma50 = 98.0,
                sma200 = 90.0,
                aiScore = 80,
                priceHistory = listOf(95.0, 97.0, 99.0, 100.0)
            )
        }

        val aggregator = MultiTimeframeCandleAggregator()
        val mtfMap = mutableMapOf<String, MultiTimeframeSnapshot>()

        tickers.forEach { ticker ->
            val candlesM5 = (1..250).map { i -> Candle(System.currentTimeMillis() - (250 - i) * 300_000L, 95.0 + i * 0.1, 96.0 + i * 0.1, 94.0 + i * 0.1, 95.5 + i * 0.1, 1000.0) }
            val candlesM15 = (1..250).map { i -> Candle(System.currentTimeMillis() - (250 - i) * 900_000L, 95.0 + i * 0.2, 96.0 + i * 0.2, 94.0 + i * 0.2, 95.5 + i * 0.2, 3000.0) }
            val candlesH1 = (1..250).map { i -> Candle(System.currentTimeMillis() - (250 - i) * 3600_000L, 95.0 + i * 0.5, 96.0 + i * 0.5, 94.0 + i * 0.5, 95.5 + i * 0.5, 10000.0) }

            aggregator.seedHistoricalCandles(ticker.symbol, Timeframe.M5, candlesM5)
            aggregator.seedHistoricalCandles(ticker.symbol, Timeframe.M15, candlesM15)
            aggregator.seedHistoricalCandles(ticker.symbol, Timeframe.H1, candlesH1)

            warmupTracker.updateCounts(ticker.symbol, 250, 250, 250, isGenuineSource = true)

            val snap = aggregator.buildSnapshot(ticker)
            if (snap != null) {
                mtfMap[ticker.symbol] = snap
            }
        }

        val scanResult = scanner.scanAllPairs(
            tickers = tickers,
            isFeedConnected = true,
            isFeedStale = false,
            readinessGate = MarketReadinessGate(
                websocketConnected = true,
                bootstrapComplete = true,
                warmupComplete = true,
                snapshotComplete = true,
                dataFresh = true,
                genuineSourceOnly = true
            ),
            mtfSnapshots = mtfMap,
            alertOnEligible = false
        )

        assertEquals(10, scanResult.totalPairsScanned)
        assertTrue(scanResult.scores.isNotEmpty())

        val top = scanResult.topOpportunity
        assertNotNull(top)
        assertTrue("Top opportunity score should be > 0.0", top!!.score > 0.0)
        assertTrue("Top score should be <= 100.0", top.score <= 100.0)
        assertFalse("Top opportunity market regime should not be UNKNOWN", top.marketRegime == MarketRegime.UNKNOWN)
    }

    @Test
    fun testReadinessWatchdog() {
        val watchdog = AlphaEngineReadinessWatchdog()
        val now = Instant.now()
        val statuses = listOf(
            SymbolWarmupStatus("BTC/USDT", 10, 5, 2, isReady = false)
        )

        val readyState = watchdog.evaluateReadiness(
            MarketReadinessGate(websocketConnected = true, bootstrapComplete = true, warmupComplete = true, snapshotComplete = true, dataFresh = true, genuineSourceOnly = true),
            emptyList(),
            now
        )
        assertTrue(readyState.isFullyReady)
        assertEquals(WatchdogWarningLevel.OK, readyState.warningLevel)

        val unreadyState = watchdog.evaluateReadiness(
            MarketReadinessGate(websocketConnected = true, bootstrapComplete = false, warmupComplete = false, snapshotComplete = false, dataFresh = true, genuineSourceOnly = true, blockingReason = "BOOTSTRAP_WARMUP_INCOMPLETE"),
            statuses,
            now
        )
        assertFalse(unreadyState.isFullyReady)

        val warningState = watchdog.evaluateReadiness(
            MarketReadinessGate(websocketConnected = true, bootstrapComplete = false, warmupComplete = false, snapshotComplete = false, dataFresh = true, genuineSourceOnly = true, blockingReason = "BOOTSTRAP_WARMUP_INCOMPLETE"),
            statuses,
            now.plusSeconds(130)
        )
        assertEquals(WatchdogWarningLevel.WARNING_2MIN, warningState.warningLevel)

        val escalationState = watchdog.evaluateReadiness(
            MarketReadinessGate(websocketConnected = true, bootstrapComplete = false, warmupComplete = false, snapshotComplete = false, dataFresh = true, genuineSourceOnly = true, blockingReason = "BOOTSTRAP_WARMUP_INCOMPLETE"),
            statuses,
            now.plusSeconds(310)
        )
        assertEquals(WatchdogWarningLevel.ESCALATION_5MIN, escalationState.warningLevel)
    }
}
