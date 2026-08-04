package com.example.trading.portfolio

import com.example.trading.analysis.Candle
import com.example.trading.analysis.IndicatorSnapshot
import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.MarketSnapshot
import com.example.trading.analysis.MultiTimeframeSnapshot
import com.example.trading.analysis.Timeframe
import com.example.trading.strategy.NoTradeReason
import com.example.trading.strategy.SignalDirection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PortfolioIntegrationTest {

    private lateinit var portfolioManager: StrategyPortfolioManager
    private val baseTs = 1700000000000L

    @Before
    fun setUp() {
        portfolioManager = StrategyPortfolioManager()
    }

    @Test
    fun testTrendingMarket_PreferredTrendStrategyWins() = runBlocking {
        val trendingSnapshot = buildTrendingMtfSnapshot()
        val config = PortfolioConfig()

        val decision = portfolioManager.evaluatePortfolio(
            mtfSnapshots = listOf(trendingSnapshot),
            portfolioConfig = config,
            currentTimeMs = baseTs
        )

        assertNotNull("Best candidate should be selected in strong trend", decision.bestCandidate)
        val winningStrategy = decision.bestCandidate!!.normalisedCandidate.signal.strategyId

        assertTrue(
            "Winning strategy in strong trend should be trend_pullback, momentum_continuation, or breakout_retest but was $winningStrategy",
            setOf("trend_pullback", "momentum_continuation", "breakout_retest", "baseline_trend_follow", "BASELINE_TREND_FOLLOW_M5_M15").contains(winningStrategy)
        )
        assertTrue("Range reversal should not win in strong trend", winningStrategy != "range_reversal")
        assertEquals(DecisionOutcome.PAPER_TRADE_CANDIDATE, decision.finalDecision)
    }

    @Test
    fun testUnstableMarket_ReturnsNoTrade() = runBlocking {
        val unstableSnapshot = buildUnstableMtfSnapshot()
        val config = PortfolioConfig()

        val decision = portfolioManager.evaluatePortfolio(
            mtfSnapshots = listOf(unstableSnapshot),
            portfolioConfig = config,
            currentTimeMs = baseTs
        )

        assertEquals(DecisionOutcome.NO_TRADE, decision.finalDecision)
        assertNull(decision.bestCandidate)
        assertTrue(decision.noTradeReasons.contains(NoTradeReason.UNSUPPORTED_REGIME))
    }

    // Helper builder for Trending MTF snapshot
    private fun buildTrendingMtfSnapshot(): MultiTimeframeSnapshot {
        val h1Candles = (11 downTo 0).map { i ->
            val p = 100.0 + (11 - i) * 1.5
            createCandle(baseTs - (i * 3600000L), p, p + 2.0, p - 0.5, p + 1.2, 500.0)
        }
        val h1Indicators = IndicatorSnapshot(ema21 = 105.0, ema50 = 108.0, ema200 = 100.0, adx = 35.0, rsi = 65.0, atr = 3.0, atrPercent = 1.5)

        val m15Candles = (11 downTo 0).map { i ->
            val p = 110.0 + (11 - i) * 0.5
            createCandle(baseTs - (i * 900000L), p, p + 1.0, p - 0.3, p + 0.4, 300.0)
        }
        val m15Indicators = IndicatorSnapshot(ema21 = 112.0, ema50 = 110.0, ema200 = 105.0, adx = 32.0, rsi = 58.0, atr = 1.5, atrPercent = 1.2)

        val m5Candles = (11 downTo 0).map { i ->
            val p = 114.0 + (11 - i) * 0.2
            createCandle(baseTs - (i * 300000L), p, p + 0.5, p - 0.2, p + 0.2, 150.0)
        }
        val m5Indicators = IndicatorSnapshot(ema21 = 115.0, ema50 = 114.0, ema200 = 110.0, adx = 30.0, rsi = 62.0, atr = 0.8, atrPercent = 1.0)

        return MultiTimeframeSnapshot(
            symbol = "BTCUSDT",
            timestamp = baseTs,
            h1 = MarketSnapshot("BTCUSDT", Timeframe.H1, h1Candles, h1Candles.last(), h1Indicators),
            m15 = MarketSnapshot("BTCUSDT", Timeframe.M15, m15Candles, m15Candles.last(), m15Indicators),
            m5 = MarketSnapshot("BTCUSDT", Timeframe.M5, m5Candles, m5Candles.last(), m5Indicators)
        )
    }

    private fun buildUnstableMtfSnapshot(): MultiTimeframeSnapshot {
        val candles = (11 downTo 0).map { i ->
            createCandle(baseTs - (i * 300000L), 100.0, 120.0, 80.0, 105.0, 500.0)
        }
        val indicators = IndicatorSnapshot(ema21 = 100.0, ema50 = 100.0, adx = 10.0, rsi = 50.0, atr = 15.0, atrPercent = 10.0)

        return MultiTimeframeSnapshot(
            symbol = "BTCUSDT",
            timestamp = baseTs,
            h1 = MarketSnapshot("BTCUSDT", Timeframe.H1, candles, candles.last(), indicators),
            m15 = MarketSnapshot("BTCUSDT", Timeframe.M15, candles, candles.last(), indicators),
            m5 = MarketSnapshot("BTCUSDT", Timeframe.M5, candles, candles.last(), indicators)
        )
    }

    private fun createCandle(
        timestamp: Long,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
        volume: Double
    ): Candle {
        return Candle(
            timestamp = timestamp,
            open = open,
            high = high,
            low = low,
            close = close,
            volume = volume
        )
    }
}
