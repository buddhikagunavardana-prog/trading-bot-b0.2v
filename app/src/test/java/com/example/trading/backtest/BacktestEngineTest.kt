package com.example.trading.backtest

import com.example.trading.analysis.Timeframe
import com.example.trading.strategy.BaselineTrendFollowStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BacktestEngineTest {

    private fun generateCandles(count: Int, startPrice: Double, trend: Double): List<HistoricalCandle> {
        val list = mutableListOf<HistoricalCandle>()
        var price = startPrice
        val baseTs = 1704067200000L
        for (i in 0 until count) {
            val open = price
            price += trend + (if (i % 2 == 0) 0.2 else -0.1)
            val close = price
            val high = Math.max(open, close) + 0.5
            val low = Math.min(open, close) - 0.5
            val openTime = baseTs + (i * 300000L)
            val closeTime = openTime + 300000L - 1L
            list.add(
                HistoricalCandle(
                    symbol = "BTCUSDT",
                    timeframe = Timeframe.M5,
                    openTime = openTime,
                    closeTime = closeTime,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = 100.0
                )
            )
        }
        return list
    }

    @Test
    fun testIndividualStrategyBacktestExecution() {
        val strategy = BaselineTrendFollowStrategy()
        val m5Candles = generateCandles(60, 100.0, 0.5) // Strong uptrend
        val m15Candles = generateCandles(20, 100.0, 1.5)
        val h1Candles = generateCandles(10, 100.0, 3.0)

        val backtestEngine = BacktestEngine()
        val result = backtestEngine.runStrategyBacktest(
            strategy = strategy,
            symbol = "BTCUSDT",
            m5Candles = m5Candles,
            m15Candles = m15Candles,
            h1Candles = h1Candles
        )

        assertNotNull(result)
        assertEquals("BTC_HASH_BTCUSDT", result.evidence.datasetHash.replace("DS_", "BTC_"))
        assertTrue(result.equityCurve.isNotEmpty())
        assertNotNull(result.metrics)
    }

    @Test
    fun testPortfolioBacktestExecution() {
        val m5Candles = generateCandles(60, 100.0, 0.5)
        val m15Candles = generateCandles(20, 100.0, 1.5)
        val h1Candles = generateCandles(10, 100.0, 3.0)

        val portfolioEngine = PortfolioBacktestEngine()
        val result = portfolioEngine.runPortfolioBacktest(
            symbol = "BTCUSDT",
            m5Candles = m5Candles,
            m15Candles = m15Candles,
            h1Candles = h1Candles
        )

        assertNotNull(result)
        assertTrue(result.equityCurve.first() == 10000.0)
        assertNotNull(result.evidence)
    }
}
