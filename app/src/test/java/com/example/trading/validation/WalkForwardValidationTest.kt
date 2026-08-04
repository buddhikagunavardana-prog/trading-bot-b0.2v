package com.example.trading.validation

import com.example.trading.analysis.Timeframe
import com.example.trading.backtest.HistoricalCandle
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkForwardValidationTest {

    private fun generateCandles(count: Int): List<HistoricalCandle> {
        val list = mutableListOf<HistoricalCandle>()
        var price = 100.0
        val baseTs = 1704067200000L
        for (i in 0 until count) {
            val open = price
            price += 0.1
            val close = price
            val high = Math.max(open, close) + 0.2
            val low = Math.min(open, close) - 0.2
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
    fun testWalkForwardValidationWorkflow() {
        val m5Candles = generateCandles(120)
        val m15Candles = generateCandles(40)
        val h1Candles = generateCandles(10)

        val wfEngine = WalkForwardEngine()
        val result = wfEngine.runWalkForwardValidation(
            symbol = "BTCUSDT",
            m5Candles = m5Candles,
            m15Candles = m15Candles,
            h1Candles = h1Candles,
            foldCount = 2
        )

        assertNotNull(result)
        assertTrue(result.folds.size == 2)
        assertNotNull(result.status)
    }
}
