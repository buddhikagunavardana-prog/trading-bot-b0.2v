package com.example.trading.portfolio

data class PortfolioEvidence(
    val evaluationId: String,
    val evaluationTimestamp: Long,
    val summaryOfEvaluation: String,
    val normalisationDetails: List<String>,
    val conflictDetails: List<String>,
    val riskDetails: List<String>,
    val correlationDetails: List<String>,
    val rankingDetails: List<String>,
    val finalDecisionReasoning: String
)
