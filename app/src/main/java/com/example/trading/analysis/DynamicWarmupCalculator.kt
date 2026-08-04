package com.example.trading.analysis

data class WarmupRequirementResult(
    val timeframe: Timeframe,
    val largestRequiredLookback: Int,
    val stabilisationBuffer: Int,
    val finalRequiredCandles: Int,
    val indicatorOrStrategyResponsible: String
)

class DynamicWarmupCalculator {

    fun calculateRequiredCandles(
        timeframe: Timeframe,
        emaPeriods: List<Int> = listOf(20, 50, 200),
        smaPeriods: List<Int> = listOf(50, 200),
        macdSlow: Int = 26,
        macdSignal: Int = 9,
        atrLookback: Int = 14,
        adxLookback: Int = 14,
        rsiLookback: Int = 14,
        bollingerLookback: Int = 20,
        superTrendLookback: Int = 10,
        supportResistanceWindow: Int = 50,
        volumeLookback: Int = 20,
        strategyLookback: Int = 50,
        stabilisationBuffer: Int = 50
    ): WarmupRequirementResult {
        val candidates = mutableListOf<Pair<String, Int>>()

        emaPeriods.maxOrNull()?.let { candidates.add("EMA$it" to it) }
        smaPeriods.maxOrNull()?.let { candidates.add("SMA$it" to it) }
        candidates.add("MACD(${macdSlow}+$macdSignal)" to (macdSlow + macdSignal))
        candidates.add("ATR$atrLookback" to atrLookback)
        candidates.add("ADX$adxLookback" to (adxLookback * 2))
        candidates.add("RSI$rsiLookback" to rsiLookback)
        candidates.add("Bollinger$bollingerLookback" to bollingerLookback)
        candidates.add("SuperTrend$superTrendLookback" to superTrendLookback)
        candidates.add("SupportResistance$supportResistanceWindow" to supportResistanceWindow)
        candidates.add("Volume$volumeLookback" to volumeLookback)
        candidates.add("Strategy$strategyLookback" to strategyLookback)

        val largest = candidates.maxByOrNull { it.second } ?: ("EMA200" to 200)
        val finalCandles = largest.second + stabilisationBuffer

        return WarmupRequirementResult(
            timeframe = timeframe,
            largestRequiredLookback = largest.second,
            stabilisationBuffer = stabilisationBuffer,
            finalRequiredCandles = finalCandles,
            indicatorOrStrategyResponsible = largest.first
        )
    }
}
