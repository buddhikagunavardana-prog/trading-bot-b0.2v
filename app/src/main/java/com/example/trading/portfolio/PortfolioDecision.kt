package com.example.trading.portfolio

import com.example.trading.analysis.MarketRegime
import com.example.trading.strategy.NoTradeReason
import com.example.trading.strategy.StrategySignal

data class PortfolioDecision(
    val evaluationId: String,
    val evaluationTimestamp: Long,
    val symbolsEvaluated: List<String>,
    val strategiesEvaluated: List<String>,
    val marketRegimes: Map<String, MarketRegime>,
    val rawStrategySignals: List<StrategySignal>,
    val normalisedCandidates: List<NormalisedCandidate>,
    val conflictReport: ConflictReport,
    val portfolioRiskReport: PortfolioRiskReport,
    val rankedCandidates: List<RankedCandidate>,
    val bestCandidate: RankedCandidate?,
    val finalDecision: DecisionOutcome,
    val decisionConfidence: Double, // 0 to 100
    val noTradeReasons: List<NoTradeReason>,
    val warnings: List<String>,
    val evaluationDurationMs: Long,
    val configVersion: String
)
