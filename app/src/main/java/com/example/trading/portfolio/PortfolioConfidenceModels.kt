package com.example.trading.portfolio

data class PortfolioConfidenceResult(
    val includedSymbols: List<String>,
    val excludedSymbols: List<String>,
    val weightedRawScore: Double,
    val totalWeight: Double,
    val normalizedConfidencePercent: Double,
    val triggerThresholdPercent: Double,
    val passesThreshold: Boolean,
    val calculatedAtEpochMs: Long,
    val blockingReasons: List<String>
)

data class PortfolioExecutionDecision(
    val decisionId: String,
    val evaluatedAtEpochMs: Long,
    val symbolsEvaluated: List<String>,
    val selectedSymbol: String?,
    val selectedDirection: String?,
    val alphaScore: Double?,
    val alphaThreshold: Double,
    val aiConfidencePercent: Double?,
    val portfolioConfidencePercent: Double,
    val portfolioTriggerPercent: Double,
    val dataFresh: Boolean,
    val strategyConfirmed: Boolean,
    val riskApproved: Boolean,
    val paperExecutionEnabled: Boolean,
    val approvedForExecution: Boolean,
    val blockingReasons: List<String>
)
