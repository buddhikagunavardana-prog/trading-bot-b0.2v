package com.example.trading.validation

import com.example.trading.backtest.metrics.ComprehensiveMetrics

enum class WalkForwardStatus {
    STRONG,
    ACCEPTABLE,
    WEAK,
    FAILED,
    INSUFFICIENT_DATA
}

enum class RobustnessGrade {
    ROBUST,
    ACCEPTABLE,
    FRAGILE,
    FAILED,
    NOT_TESTED
}

data class DatasetSplit(
    val splitId: String,
    val trainStart: Long,
    val trainEnd: Long,
    val validationStart: Long,
    val validationEnd: Long,
    val testStart: Long,
    val testEnd: Long,
    val purgeGapCandles: Int = 12,
    val embargoCandles: Int = 12
)

data class WalkForwardFold(
    val foldId: String,
    val trainPeriodStart: Long,
    val trainPeriodEnd: Long,
    val validationPeriodStart: Long,
    val validationPeriodEnd: Long,
    val oosPeriodStart: Long,
    val oosPeriodEnd: Long,
    val frozenConfigurationHash: String,
    val tradeCount: Int,
    val netPnL: Double,
    val profitFactor: Double,
    val expectancy: Double,
    val maxDrawdownPercent: Double,
    val performanceDegradationPercent: Double,
    val isFoldPassed: Boolean
)

data class WalkForwardResult(
    val status: WalkForwardStatus,
    val totalFolds: Int,
    val passedFoldsCount: Int,
    val aggregateOosMetrics: ComprehensiveMetrics,
    val folds: List<WalkForwardFold>,
    val overallDegradationPercent: Double
)

data class PerturbationResult(
    val perturbationName: String,
    val baselineNetPnL: Double,
    val perturbedNetPnL: Double,
    val degradationPercent: Double,
    val isAcceptable: Boolean
)

data class RobustnessResult(
    val grade: RobustnessGrade,
    val baselineMetrics: ComprehensiveMetrics,
    val perturbations: List<PerturbationResult>,
    val failureReason: String? = null
)
