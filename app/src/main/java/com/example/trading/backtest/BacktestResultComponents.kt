package com.example.trading.backtest

import com.example.trading.backtest.metrics.ComprehensiveMetrics
import com.example.trading.performance.OverfittingRisk
import com.example.trading.performance.VerificationStatus

data class BacktestEvidence(
    val backtestId: String,
    val datasetHash: String,
    val configHash: String,
    val executionCostHash: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val evaluatedCandleCount: Int,
    val dataQualityIssues: List<DataQualityIssue>,
    val auditTrail: List<String>
)

data class BacktestResult(
    val backtestId: String,
    val config: BacktestConfig,
    val metrics: ComprehensiveMetrics,
    val trades: List<SimulatedTrade>,
    val equityCurve: List<Double>,
    val evidence: BacktestEvidence,
    val overfittingRisk: OverfittingRisk,
    val verificationStatus: VerificationStatus
)
