package com.example.trading.validation

import com.example.trading.backtest.BacktestConfig
import com.example.trading.backtest.HistoricalCandle
import com.example.trading.backtest.PortfolioBacktestEngine

class WalkForwardEngine(
    private val portfolioEngine: PortfolioBacktestEngine = PortfolioBacktestEngine()
) {

    fun runWalkForwardValidation(
        symbol: String,
        m5Candles: List<HistoricalCandle>,
        m15Candles: List<HistoricalCandle>,
        h1Candles: List<HistoricalCandle>,
        foldCount: Int = 3
    ): WalkForwardResult {
        if (m5Candles.size < 100) {
            val emptyResult = portfolioEngine.runPortfolioBacktest(symbol, m5Candles, m15Candles, h1Candles)
            return WalkForwardResult(
                status = WalkForwardStatus.INSUFFICIENT_DATA,
                totalFolds = 0,
                passedFoldsCount = 0,
                aggregateOosMetrics = emptyResult.metrics,
                folds = emptyList(),
                overallDegradationPercent = 0.0
            )
        }

        val totalM5 = m5Candles.size
        val foldSize = totalM5 / (foldCount + 1)
        val foldsList = mutableListOf<WalkForwardFold>()

        for (i in 0 until foldCount) {
            val trainEndIdx = (i + 1) * foldSize
            val oosEndIdx = (i + 2) * foldSize

            val trainM5 = m5Candles.take(trainEndIdx)
            val trainM15 = m15Candles.filter { it.closeTime <= trainM5.last().closeTime }
            val trainH1 = h1Candles.filter { it.closeTime <= trainM5.last().closeTime }

            val trainRes = portfolioEngine.runPortfolioBacktest(symbol, trainM5, trainM15, trainH1)

            val oosM5 = m5Candles.subList(trainEndIdx, oosEndIdx.coerceAtMost(m5Candles.size))
            val oosM15 = m15Candles.filter { it.openTime >= oosM5.first().openTime && it.closeTime <= oosM5.last().closeTime }
            val oosH1 = h1Candles.filter { it.openTime >= oosM5.first().openTime && it.closeTime <= oosM5.last().closeTime }

            val oosRes = portfolioEngine.runPortfolioBacktest(symbol, oosM5, oosM15, oosH1)

            val trainPnL = trainRes.metrics.profitabilityMetrics.netPnL
            val oosPnL = oosRes.metrics.profitabilityMetrics.netPnL
            val degradation = if (trainPnL != 0.0) ((trainPnL - oosPnL) / Math.abs(trainPnL)) * 100.0 else 0.0
            val isPassed = oosRes.metrics.profitabilityMetrics.netPnL >= 0 || oosRes.trades.isEmpty()

            foldsList.add(
                WalkForwardFold(
                    foldId = "FOLD_${i + 1}",
                    trainPeriodStart = trainM5.first().openTime,
                    trainPeriodEnd = trainM5.last().closeTime,
                    validationPeriodStart = 0L,
                    validationPeriodEnd = 0L,
                    oosPeriodStart = if (oosM5.isNotEmpty()) oosM5.first().openTime else 0L,
                    oosPeriodEnd = if (oosM5.isNotEmpty()) oosM5.last().closeTime else 0L,
                    frozenConfigurationHash = BacktestConfig().calculateConfigHash(),
                    tradeCount = oosRes.trades.size,
                    netPnL = oosPnL,
                    profitFactor = oosRes.metrics.profitabilityMetrics.profitFactor,
                    expectancy = oosRes.metrics.tradeMetrics.expectancy,
                    maxDrawdownPercent = oosRes.metrics.riskMetrics.maxPercentageDrawdown,
                    performanceDegradationPercent = degradation,
                    isFoldPassed = isPassed
                )
            )
        }

        val fullOosResult = portfolioEngine.runPortfolioBacktest(symbol, m5Candles, m15Candles, h1Candles)
        val passedCount = foldsList.count { it.isFoldPassed }
        val status = when {
            passedCount == foldCount -> WalkForwardStatus.STRONG
            passedCount >= foldCount / 2 -> WalkForwardStatus.ACCEPTABLE
            else -> WalkForwardStatus.WEAK
        }

        val avgDegradation = foldsList.map { it.performanceDegradationPercent }.average()

        return WalkForwardResult(
            status = status,
            totalFolds = foldCount,
            passedFoldsCount = passedCount,
            aggregateOosMetrics = fullOosResult.metrics,
            folds = foldsList,
            overallDegradationPercent = if (avgDegradation.isNaN()) 0.0 else avgDegradation
        )
    }
}
