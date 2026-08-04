package com.example.trading.backtest

import com.example.trading.analysis.Candle
import com.example.trading.analysis.IndicatorSnapshot
import com.example.trading.analysis.MarketSnapshot
import com.example.trading.analysis.MultiTimeframeSnapshot
import com.example.trading.analysis.Timeframe
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MultiTimeframeReplayBuilder {

    /**
     * Builds a causal MultiTimeframeSnapshot for evaluation at exact time `evaluationTimeMs`.
     * Strict causality rule:
     * - Only CLOSED M5 candles with closeTime <= evaluationTimeMs are visible.
     * - Only CLOSED M15 candles with closeTime <= evaluationTimeMs are visible.
     * - Only CLOSED H1 candles with closeTime <= evaluationTimeMs are visible.
     */
    fun buildCausalSnapshot(
        symbol: String,
        evaluationTimeMs: Long,
        allM5Candles: List<HistoricalCandle>,
        allM15Candles: List<HistoricalCandle>,
        allH1Candles: List<HistoricalCandle>
    ): MultiTimeframeSnapshot? {
        // Filter strictly closed candles
        val visibleM5 = allM5Candles.filter { it.closeTime <= evaluationTimeMs }.map { toDomainCandle(it) }
        val visibleM15 = allM15Candles.filter { it.closeTime <= evaluationTimeMs }.map { toDomainCandle(it) }
        val visibleH1 = allH1Candles.filter { it.closeTime <= evaluationTimeMs }.map { toDomainCandle(it) }

        if (visibleM5.isEmpty() || visibleM15.isEmpty() || visibleH1.isEmpty()) {
            return null
        }

        val m5Snap = MarketSnapshot(symbol, Timeframe.M5, visibleM5.takeLast(50), visibleM5.last(), computeIndicators(visibleM5))
        val m15Snap = MarketSnapshot(symbol, Timeframe.M15, visibleM15.takeLast(50), visibleM15.last(), computeIndicators(visibleM15))
        val h1Snap = MarketSnapshot(symbol, Timeframe.H1, visibleH1.takeLast(50), visibleH1.last(), computeIndicators(visibleH1))

        return MultiTimeframeSnapshot(
            symbol = symbol,
            timestamp = evaluationTimeMs,
            h1 = h1Snap,
            m15 = m15Snap,
            m5 = m5Snap
        )
    }

    private fun toDomainCandle(c: HistoricalCandle): Candle {
        return Candle(
            timestamp = c.openTime,
            open = c.open,
            high = c.high,
            low = c.low,
            close = c.close,
            volume = c.volume
        )
    }

    private fun computeIndicators(candles: List<Candle>): IndicatorSnapshot {
        if (candles.isEmpty()) return IndicatorSnapshot()
        val closes = candles.map { it.close }

        val ema21 = calculateEma(closes, 21)
        val ema50 = calculateEma(closes, 50)
        val ema200 = calculateEma(closes, 200)
        val atr = calculateAtr(candles, 14)
        val rsi = calculateRsi(closes, 14)

        val atrPct = if (closes.last() > 0) (atr / closes.last()) * 100.0 else 1.0

        return IndicatorSnapshot(
            ema21 = ema21,
            ema50 = ema50,
            ema200 = ema200,
            adx = 28.0, // Baseline ADX
            rsi = rsi,
            atr = atr,
            atrPercent = atrPct
        )
    }

    private fun calculateEma(prices: List<Double>, period: Int): Double {
        if (prices.isEmpty()) return 0.0
        if (prices.size < period) return prices.average()
        val k = 2.0 / (period + 1)
        var ema = prices.take(period).average()
        for (i in period until prices.size) {
            ema = (prices[i] * k) + (ema * (1 - k))
        }
        return ema
    }

    private fun calculateAtr(candles: List<Candle>, period: Int): Double {
        if (candles.size < 2) return 1.0
        val trs = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val high = candles[i].high
            val low = candles[i].low
            val prevClose = candles[i - 1].close
            val tr = max(high - low, max(abs(high - prevClose), abs(low - prevClose)))
            trs.add(tr)
        }
        return if (trs.isEmpty()) 1.0 else trs.takeLast(period).average()
    }

    private fun calculateRsi(prices: List<Double>, period: Int): Double {
        if (prices.size < period + 1) return 50.0
        var gains = 0.0
        var losses = 0.0
        for (i in prices.size - period until prices.size) {
            val change = prices[i] - prices[i - 1]
            if (change > 0) gains += change else losses += abs(change)
        }
        if (losses == 0.0) return 100.0
        val rs = (gains / period) / (losses / period)
        return 100.0 - (100.0 / (1.0 + rs))
    }
}
