package com.example.trading.analysis

data class RegimeDetectorConfig(
    val adxStrongTrendThreshold: Double = 25.0,
    val adxWeakTrendThreshold: Double = 18.0,
    val atrHighVolThresholdPercent: Double = 3.0,
    val atrExtremeVolThresholdPercent: Double = 6.0,
    val atrLowVolThresholdPercent: Double = 0.5,
    val volumeBreakoutMultiplier: Double = 1.5,
    val maxSpreadPercent: Double = 0.5
)

class MarketRegimeDetector(
    val config: RegimeDetectorConfig = RegimeDetectorConfig()
) {

    fun detectRegime(
        mtf: MultiTimeframeSnapshot?,
        currentSpreadPercent: Double = 0.0,
        isDataQualityValid: Boolean = true
    ): MarketRegime {
        if (mtf == null || !isDataQualityValid) {
            return MarketRegime.UNSTABLE
        }

        if (currentSpreadPercent > config.maxSpreadPercent) {
            return MarketRegime.UNSTABLE
        }

        val primary = mtf.m15 ?: mtf.m5 ?: return MarketRegime.UNKNOWN
        val ind = primary.indicators
        val candle = primary.latestCandle

        // 1. Extreme volatility or extreme spread -> UNSTABLE
        if (ind.atrPercent >= config.atrExtremeVolThresholdPercent) {
            return MarketRegime.UNSTABLE
        }

        // 2. Check Breakout
        val isAboveResistance = ind.resistancePrice > 0.0 && candle.close > ind.resistancePrice
        val isBelowSupport = ind.supportPrice > 0.0 && candle.close < ind.supportPrice
        val isHighVolume = ind.volumeSma20 > 0.0 && candle.volume >= (ind.volumeSma20 * config.volumeBreakoutMultiplier)

        if ((isAboveResistance || isBelowSupport) && isHighVolume) {
            return MarketRegime.BREAKOUT
        }

        // 3. High Volatility Regime
        if (ind.atrPercent >= config.atrHighVolThresholdPercent) {
            return MarketRegime.HIGH_VOLATILITY
        }

        // 4. Low Volatility Regime
        if (ind.atrPercent > 0.0 && ind.atrPercent <= config.atrLowVolThresholdPercent && ind.adx < config.adxWeakTrendThreshold) {
            return MarketRegime.LOW_VOLATILITY
        }

        // 5. Check Trend vs Range
        val isBullishEmaStructure = ind.ema50 > ind.ema200 || (ind.ema50 == 0.0 && ind.sma50 > ind.sma200)
        val isBearishEmaStructure = ind.ema50 < ind.ema200 || (ind.ema50 == 0.0 && ind.sma50 < ind.sma200)

        // Check timeframe alignment if M5 and M15 both present
        var isTimeframeConflicting = false
        if (mtf.m5 != null && mtf.m15 != null) {
            val m5Bull = mtf.m5.indicators.ema50 >= mtf.m5.indicators.ema200
            val m15Bull = mtf.m15.indicators.ema50 >= mtf.m15.indicators.ema200
            if (m5Bull != m15Bull && ind.adx < config.adxStrongTrendThreshold) {
                isTimeframeConflicting = true
            }
        }

        if (isTimeframeConflicting) {
            return MarketRegime.UNSTABLE
        }

        if (ind.adx >= config.adxStrongTrendThreshold) {
            return if (isBullishEmaStructure) MarketRegime.STRONG_BULL_TREND else MarketRegime.STRONG_BEAR_TREND
        } else if (ind.adx >= config.adxWeakTrendThreshold) {
            return if (isBullishEmaStructure) MarketRegime.WEAK_BULL_TREND else MarketRegime.WEAK_BEAR_TREND
        }

        // Range Regime
        if (ind.adx < config.adxWeakTrendThreshold) {
            return MarketRegime.RANGE
        }

        return MarketRegime.UNKNOWN
    }
}
