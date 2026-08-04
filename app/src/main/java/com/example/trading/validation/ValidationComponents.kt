package com.example.trading.validation

import com.example.trading.backtest.metrics.ComprehensiveMetrics

import com.example.trading.backtest.HistoricalCandle
import com.example.trading.backtest.ValidationSplitConfig

class DatasetSplitter {
    fun splitChronologically(
        candles: List<HistoricalCandle>,
        config: ValidationSplitConfig
    ): DatasetSplit {
        if (candles.isEmpty()) {
            return DatasetSplit("EMPTY", 0L, 0L, 0L, 0L, 0L, 0L)
        }

        val sorted = candles.sortedBy { it.openTime }
        val startTime = sorted.first().openTime
        val endTime = sorted.last().openTime
        val totalSpan = endTime - startTime

        val trainEnd = startTime + (totalSpan * config.trainRatio).toLong()
        val valEnd = trainEnd + (totalSpan * config.validationRatio).toLong()

        return DatasetSplit(
            splitId = "SPLIT_${startTime}_${endTime}",
            trainStart = startTime,
            trainEnd = trainEnd,
            validationStart = trainEnd,
            validationEnd = valEnd,
            testStart = valEnd,
            testEnd = endTime,
            purgeGapCandles = config.purgeGapCandles,
            embargoCandles = config.embargoCandles
        )
    }
}

class OverfittingAnalyzer {
    fun analyzeOverfittingRisk(
        trainMetrics: ComprehensiveMetrics,
        valMetrics: ComprehensiveMetrics,
        oosMetrics: ComprehensiveMetrics
    ): com.example.trading.performance.OverfittingRisk {
        val trainProfit = trainMetrics.profitabilityMetrics.netPnL
        val oosProfit = oosMetrics.profitabilityMetrics.netPnL

        if (trainProfit > 0 && oosProfit < 0) {
            return com.example.trading.performance.OverfittingRisk.CRITICAL_OVERFITTING_RISK
        }

        val degradation = if (trainProfit > 0) (trainProfit - oosProfit) / trainProfit else 0.0

        return when {
            degradation > 0.60 -> com.example.trading.performance.OverfittingRisk.HIGH_OVERFITTING_RISK
            degradation > 0.30 -> com.example.trading.performance.OverfittingRisk.MODERATE_OVERFITTING_RISK
            else -> com.example.trading.performance.OverfittingRisk.LOW_OVERFITTING_RISK
        }
    }
}

class RobustnessAnalyzer {
    fun evaluateRobustness(
        baselineMetrics: ComprehensiveMetrics,
        increasedCostMetrics: ComprehensiveMetrics,
        delayedEntryMetrics: ComprehensiveMetrics
    ): RobustnessResult {
        val baselinePnL = baselineMetrics.profitabilityMetrics.netPnL
        val costPnL = increasedCostMetrics.profitabilityMetrics.netPnL
        val delayPnL = delayedEntryMetrics.profitabilityMetrics.netPnL

        val costDegradation = if (baselinePnL != 0.0) (baselinePnL - costPnL) / Math.abs(baselinePnL) * 100.0 else 0.0
        val delayDegradation = if (baselinePnL != 0.0) (baselinePnL - delayPnL) / Math.abs(baselinePnL) * 100.0 else 0.0

        val perturbations = listOf(
            PerturbationResult("Increased Spread/Fee (+50%)", baselinePnL, costPnL, costDegradation, costDegradation < 40.0),
            PerturbationResult("1-Candle Entry Delay", baselinePnL, delayPnL, delayDegradation, delayDegradation < 50.0)
        )

        val failedCount = perturbations.count { !it.isAcceptable }
        val grade = when {
            failedCount == 0 -> RobustnessGrade.ROBUST
            failedCount == 1 -> RobustnessGrade.ACCEPTABLE
            else -> RobustnessGrade.FRAGILE
        }

        return RobustnessResult(
            grade = grade,
            baselineMetrics = baselineMetrics,
            perturbations = perturbations
        )
    }
}
