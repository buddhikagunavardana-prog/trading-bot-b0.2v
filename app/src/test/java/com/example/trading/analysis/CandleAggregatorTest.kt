package com.example.trading.analysis

import com.example.model.CryptoTicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CandleAggregatorTest {

    private lateinit var aggregator: MultiTimeframeCandleAggregator

    @Before
    fun setUp() {
        aggregator = MultiTimeframeCandleAggregator(maxCandlesPerTimeframe = 10)
    }

    @Test
    fun testCandleTimeframeAlignmentAndCloseDetection() {
        val symbol = "BTC/USDT"
        val ticker = CryptoTicker(symbol, "Bitcoin", 60000.0, 1.0, 60500.0, 59500.0, 10000.0)

        // T1: 12:01:00 (M5 boundary start = 12:00:00)
        val t1 = 1700000060000L // 12:01:00 approx
        val events1 = aggregator.processTick(ticker, t1)
        assertTrue(events1.isEmpty()) // Partial candle building

        // T2: 12:06:00 (M5 boundary start = 12:05:00) -> M5 candle for 12:00:00 closes!
        val t2 = t1 + 300000L
        val events2 = aggregator.processTick(ticker.copy(price = 60100.0), t2)

        assertTrue(events2.isNotEmpty())
        val closedM5 = events2.firstOrNull { it.timeframe == Timeframe.M5 }
        assertTrue(closedM5 != null)
        assertEquals(symbol, closedM5?.symbol)
        assertEquals(60000.0, closedM5?.closedCandle?.open ?: 0.0, 0.001)
    }

    @Test
    fun testDuplicateEventRejection() {
        val symbol = "ETH/USDT"
        val ticker = CryptoTicker(symbol, "Ethereum", 3000.0, 2.0, 3100.0, 2900.0, 5000.0)

        val t1 = 1700000000000L
        aggregator.processTick(ticker, t1)

        val t2 = t1 + 300000L
        val eventsA = aggregator.processTick(ticker, t2)
        val countA = eventsA.size

        // Same timestamp duplicate tick
        val eventsB = aggregator.processTick(ticker, t2)
        val countB = eventsB.size

        assertTrue(countA >= 1)
        assertEquals(0, countB) // Duplicate close rejected
    }

    @Test
    fun testBoundedMemoryStorage() {
        val symbol = "SOL/USDT"
        val ticker = CryptoTicker(symbol, "Solana", 150.0, 0.5, 155.0, 145.0, 2000.0)

        var t = 1700000000000L
        for (i in 0..30) {
            t += 300000L
            aggregator.processTick(ticker, t)
        }

        val candles = aggregator.getCandles(symbol, Timeframe.M5)
        assertTrue(candles.size <= 10) // Bounded by maxCandlesPerTimeframe=10
    }
}
