package com.example.trading.analysis

import com.example.model.CryptoTicker
import com.example.trading.paper.SessionCorrectionAuditLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AlphaGenuineCandlePipelineTest {

    @Test
    fun testCandleProvenanceAndMetadataInvariants() {
        val now = System.currentTimeMillis()
        val genuineCandle = Candle(
            timestamp = now - 300000,
            open = 67000.0,
            high = 67500.0,
            low = 66900.0,
            close = 67400.0,
            volume = 150.5,
            isFinal = true,
            sourceOrigin = CandleSourceOrigin.REST_BOOTSTRAP,
            closeTimestamp = now - 1,
            numberOfTrades = 4200L,
            takerBuyVolume = 85.2
        )

        assertTrue(genuineCandle.isFinal)
        assertEquals(CandleSourceOrigin.REST_BOOTSTRAP, genuineCandle.sourceOrigin)
        assertEquals(4200L, genuineCandle.numberOfTrades)
        assertEquals(85.2, genuineCandle.takerBuyVolume, 1e-5)
        assertTrue(SourceAuthenticityGuard.isGenuine(genuineCandle))
    }

    @Test
    fun testSourceAuthenticityGuardQuarantine() {
        val syntheticCandle = Candle(
            timestamp = System.currentTimeMillis(),
            open = 100.0, high = 105.0, low = 95.0, close = 102.0, volume = 10.0,
            isFinal = true,
            sourceOrigin = CandleSourceOrigin.DEMO_GENERATOR
        )

        assertFalse(SourceAuthenticityGuard.isGenuine(syntheticCandle))
        assertFalse(SourceAuthenticityGuard.validateCandles(listOf(syntheticCandle)))

        val genuineCandles = List(10) { i ->
            Candle(
                timestamp = System.currentTimeMillis() - (i * 300000L),
                open = 100.0, high = 105.0, low = 95.0, close = 102.0, volume = 10.0,
                isFinal = true,
                sourceOrigin = CandleSourceOrigin.REST_BOOTSTRAP
            )
        }

        assertTrue(SourceAuthenticityGuard.validateCandles(genuineCandles))
    }

    @Test
    fun testWarmupReadinessTrackerRequires250Candles() {
        val tracker = WarmupReadinessTracker()

        // Update with 200 candles (< 250 required)
        tracker.updateCounts("BTC/USDT", m5Count = 200, m15Count = 200, h1Count = 200, isGenuineSource = true)
        assertFalse(tracker.isSymbolReady("BTC/USDT"))

        // Update with 250 candles
        tracker.updateCounts("BTC/USDT", m5Count = 250, m15Count = 250, h1Count = 250, isGenuineSource = true)
        assertTrue(tracker.isSymbolReady("BTC/USDT"))

        // Non-genuine source must fail readiness
        tracker.updateCounts("ETH/USDT", m5Count = 250, m15Count = 250, h1Count = 250, isGenuineSource = false)
        assertFalse(tracker.isSymbolReady("ETH/USDT"))
    }

    @Test
    fun testMarketReadinessGateFailClosed() {
        val gateNotReady = MarketReadinessGate(
            websocketConnected = true,
            bootstrapComplete = false,
            warmupComplete = false,
            snapshotComplete = false,
            dataFresh = true,
            genuineSourceOnly = true,
            marketDataMode = MarketDataMode.LIVE_BINANCE_PUBLIC,
            blockingReason = "GENUINE_KLINE_DATA_UNAVAILABLE"
        )

        assertFalse(gateNotReady.isFullyReady)
        assertEquals("GENUINE_KLINE_DATA_UNAVAILABLE", gateNotReady.blockingReason)

        val gateReady = MarketReadinessGate(
            websocketConnected = true,
            bootstrapComplete = true,
            warmupComplete = true,
            snapshotComplete = true,
            dataFresh = true,
            genuineSourceOnly = true,
            marketDataMode = MarketDataMode.LIVE_BINANCE_PUBLIC
        )

        assertTrue(gateReady.isFullyReady)
        assertNull(gateReady.blockingReason)
    }

    @Test
    fun testAggregatorFailsClosedWithoutGenuineData() {
        val aggregator = MultiTimeframeCandleAggregator()
        val ticker = CryptoTicker(
            symbol = "BTC/USDT",
            name = "Bitcoin",
            price = 67000.0,
            change24h = 1.5,
            high24h = 68000.0,
            low24h = 66000.0,
            volume = 5000000.0,
            rsi = 55.0,
            sma50 = 66500.0,
            sma200 = 65000.0,
            aiScore = 75,
            priceHistory = listOf(67000.0)
        )

        // In production mode, buildSnapshot returns null when genuine data is absent
        val nullSnapshot = aggregator.buildSnapshot(ticker, allowSyntheticDemo = false)
        assertNull(nullSnapshot)
    }

    @Test
    fun testAggregatorWithSeededHistoricalCandles() {
        val aggregator = MultiTimeframeCandleAggregator()
        val ticker = CryptoTicker(
            symbol = "BTC/USDT", name = "Bitcoin", price = 67000.0, change24h = 1.5,
            high24h = 68000.0, low24h = 66000.0, volume = 5000000.0, rsi = 55.0,
            sma50 = 66500.0, sma200 = 65000.0, aiScore = 75, priceHistory = listOf(67000.0)
        )

        val m5Klines = List(250) { i ->
            Candle(
                timestamp = System.currentTimeMillis() - ((250 - i) * 300000L),
                open = 67000.0, high = 67200.0, low = 66900.0, close = 67100.0, volume = 50.0,
                isFinal = true, sourceOrigin = CandleSourceOrigin.REST_BOOTSTRAP
            )
        }
        val m15Klines = List(250) { i ->
            Candle(
                timestamp = System.currentTimeMillis() - ((250 - i) * 900000L),
                open = 67000.0, high = 67500.0, low = 66800.0, close = 67200.0, volume = 150.0,
                isFinal = true, sourceOrigin = CandleSourceOrigin.REST_BOOTSTRAP
            )
        }
        val h1Klines = List(250) { i ->
            Candle(
                timestamp = System.currentTimeMillis() - ((250 - i) * 3600000L),
                open = 66000.0, high = 68000.0, low = 65500.0, close = 67400.0, volume = 600.0,
                isFinal = true, sourceOrigin = CandleSourceOrigin.REST_BOOTSTRAP
            )
        }

        aggregator.seedHistoricalCandles("BTC/USDT", Timeframe.M5, m5Klines)
        aggregator.seedHistoricalCandles("BTC/USDT", Timeframe.M15, m15Klines)
        aggregator.seedHistoricalCandles("BTC/USDT", Timeframe.H1, h1Klines)

        val snapshot = aggregator.buildSnapshot(ticker, allowSyntheticDemo = false)
        assertNotNull(snapshot)
        assertEquals("BTC/USDT", snapshot?.symbol)
        assertEquals(250, snapshot?.m5?.candles?.size)
        assertEquals(250, snapshot?.m15?.candles?.size)
        assertEquals(250, snapshot?.h1?.candles?.size)
    }

    @Test
    fun testScannerRejectsWhenGateIsNotReady() {
        val scanner = AlphaOpportunityScanner()
        val tickers = listOf(
            CryptoTicker("BTC/USDT", "Bitcoin", 67000.0, 1.5, 68000.0, 66000.0, 5000000.0, 55.0, 66500.0, 65000.0, 75, priceHistory = listOf(67000.0))
        )

        val gateBlocked = MarketReadinessGate(
            websocketConnected = false,
            bootstrapComplete = false,
            warmupComplete = false,
            snapshotComplete = false,
            dataFresh = false,
            genuineSourceOnly = true,
            marketDataMode = MarketDataMode.LIVE_BINANCE_PUBLIC,
            blockingReason = "GENUINE_KLINE_DATA_UNAVAILABLE"
        )

        val result = scanner.scanAllPairs(
            tickers = tickers,
            isFeedConnected = false,
            isFeedStale = true,
            readinessGate = gateBlocked
        )

        assertEquals(10, result.totalPairsScanned)
        assertEquals(0, result.eligiblePairsCount)
        assertTrue(result.scores.all { it.score == 0.0 })
        assertTrue(result.scores.all { it.eligibility == OpportunityEligibility.INELIGIBLE_DATA_NOT_READY })
        assertTrue(result.scores.all { it.rejectionReasons.contains("GENUINE_KLINE_DATA_UNAVAILABLE") })
    }

    @Test
    fun testQuarantineAuditLedgerHasRecord() {
        val entries = SessionCorrectionAuditLedger.getQuarantinedRecords()
        assertTrue(entries.any { it.correctionId == "CORR_ALPHA_MARKET_DATA_001" })
    }
}
