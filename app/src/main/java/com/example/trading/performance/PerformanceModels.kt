package com.example.trading.performance

import com.example.trading.analysis.MarketRegime

enum class VerificationStatus {
    UNVERIFIED,
    UNIT_TESTED,
    BACKTESTED,
    WALK_FORWARD_VALIDATED,
    PAPER_VALIDATED,
    REJECTED,
    DEPRECATED
}

enum class SampleValidity {
    VALID,
    LOW_SAMPLE,
    UNSTABLE,
    INVALID
}

enum class OverfittingRisk {
    LOW_OVERFITTING_RISK,
    MODERATE_OVERFITTING_RISK,
    HIGH_OVERFITTING_RISK,
    CRITICAL_OVERFITTING_RISK,
    UNKNOWN
}

data class VerifiedPerformanceRecord(
    val id: String,
    val strategyId: String,
    val strategyVersion: String,
    val symbol: String,
    val regime: MarketRegime,
    val timeframes: List<String>,
    val datasetId: String,
    val datasetPeriodStart: Long,
    val datasetPeriodEnd: Long,
    val datasetHash: String,
    val configurationHash: String,
    val executionCostHash: String,
    val validationConfigHash: String,
    val backtestType: String,
    val trainingPeriodStart: Long,
    val trainingPeriodEnd: Long,
    val validationPeriodStart: Long,
    val validationPeriodEnd: Long,
    val testPeriodStart: Long,
    val testPeriodEnd: Long,
    val foldId: String,
    val tradeCount: Int,
    val winRate: Double,
    val profitFactor: Double,
    val expectancy: Double,
    val netReturnPercent: Double,
    val maxDrawdownPercent: Double,
    val sharpeRatio: Double,
    val sortinoRatio: Double,
    val stabilityGrade: String,
    val overfittingRisk: OverfittingRisk,
    val sampleValidity: SampleValidity,
    val verificationStatus: VerificationStatus,
    val createdTimestamp: Long
)
