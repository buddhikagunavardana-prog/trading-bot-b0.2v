package com.example.trading.analysis

import com.example.model.CryptoTicker
import com.example.trading.paper.TelegramAlphaIdentityReporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AlphaOpportunityScannerTest {

    private lateinit var scanner: AlphaOpportunityScanner

    @Before
    fun setUp() {
        scanner = AlphaOpportunityScanner(opportunityThreshold = 65.0)
        TelegramAlphaIdentityReporter.clearSentMessages()
    }

    @Test
    fun scanAllPairs_scans10CanonicalPairs_andRanksByScore() {
        val testTickers = listOf(
            CryptoTicker("BTC/USDT", "Bitcoin", 68000.0, 3.5, 69000.0, 67000.0, 20000000.0, rsi = 62.0, sma50 = 67000.0, sma200 = 65000.0, aiScore = 88),
            CryptoTicker("ETH/USDT", "Ethereum", 3500.0, 1.2, 3550.0, 3450.0, 12000000.0, rsi = 55.0, sma50 = 3480.0, sma200 = 3400.0, aiScore = 75),
            CryptoTicker("SOL/USDT", "Solana", 160.0, 4.8, 165.0, 150.0, 15000000.0, rsi = 68.0, sma50 = 155.0, sma200 = 140.0, aiScore = 92),
            CryptoTicker("BNB/USDT", "BNB", 580.0, 0.2, 585.0, 575.0, 5000000.0, rsi = 50.0, sma50 = 578.0, sma200 = 570.0, aiScore = 60),
            CryptoTicker("XRP/USDT", "XRP", 0.60, -2.5, 0.62, 0.58, 4000000.0, rsi = 38.0, sma50 = 0.62, sma200 = 0.65, aiScore = 40),
            CryptoTicker("ADA/USDT", "Cardano", 0.45, 0.5, 0.46, 0.44, 3000000.0, rsi = 48.0, sma50 = 0.44, sma200 = 0.43, aiScore = 55),
            CryptoTicker("DOGE/USDT", "Dogecoin", 0.14, 5.2, 0.15, 0.13, 10000000.0, rsi = 70.0, sma50 = 0.13, sma200 = 0.12, aiScore = 82),
            CryptoTicker("AVAX/USDT", "Avalanche", 30.0, -0.8, 31.0, 29.0, 2000000.0, rsi = 44.0, sma50 = 30.5, sma200 = 32.0, aiScore = 48),
            CryptoTicker("DOT/USDT", "Polkadot", 7.2, 1.8, 7.4, 6.9, 1800000.0, rsi = 58.0, sma50 = 7.0, sma200 = 6.8, aiScore = 68),
            CryptoTicker("POL/USDT", "Polygon", 0.56, 2.1, 0.58, 0.54, 2500000.0, rsi = 60.0, sma50 = 0.54, sma200 = 0.51, aiScore = 72)
        )

        val result = scanner.scanAllPairs(
            tickers = testTickers,
            isFeedConnected = true,
            isFeedStale = false,
            alertOnEligible = true
        )

        assertEquals(10, result.totalPairsScanned)
        assertEquals(10, result.scores.size)
        assertNotNull(result.topOpportunity)

        // Verify descending order by score
        for (i in 0 until result.scores.size - 1) {
            assertTrue(
                "Score at $i (${result.scores[i].score}) should be >= score at ${i+1} (${result.scores[i+1].score})",
                result.scores[i].score >= result.scores[i+1].score
            )
        }

        // Verify top opportunity has highest score
        assertEquals(result.scores.first().symbol, result.topOpportunity?.symbol)
    }

    @Test
    fun scanAllPairs_handlesStaleData_andMarksIneligible() {
        val testTickers = listOf(
            CryptoTicker("BTC/USDT", "Bitcoin", 68000.0, 3.5, 69000.0, 67000.0, 20000000.0)
        )

        val result = scanner.scanAllPairs(
            tickers = testTickers,
            isFeedConnected = true,
            isFeedStale = true,
            alertOnEligible = false
        )

        assertEquals(10, result.totalPairsScanned)
        val btcScore = result.scores.find { it.symbol == "BTC/USDT" }
        assertNotNull(btcScore)
        assertEquals(OpportunityEligibility.INELIGIBLE_STALE_DATA, btcScore?.eligibility)
        assertEquals(0.0, btcScore?.score ?: -1.0, 0.001)
        assertTrue(btcScore?.rejectionReasons?.contains("STALE_MARKET_FEED") == true)
    }
}
