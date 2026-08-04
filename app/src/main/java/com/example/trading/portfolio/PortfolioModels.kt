package com.example.trading.portfolio

import com.example.trading.analysis.MarketRegime
import com.example.trading.strategy.NoTradeReason
import com.example.trading.strategy.SignalDirection
import com.example.trading.strategy.StrategySignal

enum class ConflictSeverity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class ConflictResolutionOutcome {
    RESOLVED_SINGLE_WINNER,
    RESOLVED_MERGED,
    UNRESOLVED_REJECT_ALL,
    NO_CONFLICT
}

data class ConflictItem(
    val conflictId: String,
    val symbols: List<String>,
    val strategyIds: List<String>,
    val directions: List<SignalDirection>,
    val severity: ConflictSeverity,
    val evidence: List<String>,
    val outcome: ConflictResolutionOutcome,
    val explanation: String
)

data class ConflictReport(
    val hasUnresolvedHighOrCritical: Boolean,
    val conflicts: List<ConflictItem>,
    val summary: String
)

data class NormalisationComponent(
    val name: String,
    val rawValue: Double,
    val normalisedValue: Double, // 0 to maxForComponent
    val maxPossibleValue: Double,
    val weight: Double,
    val explanation: String,
    val dataSource: String,
    val confidence: Double // 0.0 to 1.0
)

data class NormalisedCandidate(
    val signal: StrategySignal,
    val rawStrategyScore: Double,
    val normalisedScore: Double, // Bounded 0.0 to 100.0
    val components: List<NormalisationComponent>,
    val effectiveWeight: Double,
    val signalFingerprint: String,
    val isReliabilityVerified: Boolean
)

data class RankedCandidate(
    val normalisedCandidate: NormalisedCandidate,
    val rankPosition: Int,
    val rawRankScore: Double,
    val weightedRankScore: Double, // 0.0 to 100.0
    val confidence: Double,        // 0.0 to 100.0
    val evidence: List<String>,
    val disqualifyingFactors: List<String> = emptyList()
)

data class PortfolioPosition(
    val positionId: String,
    val symbol: String,
    val direction: SignalDirection,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val positionSize: Double,
    val notionalValue: Double,
    val riskAmount: Double,
    val strategyId: String,
    val assetClass: String = "CRYPTO",
    val openedTimestamp: Long
)

data class PendingPaperOrder(
    val orderId: String,
    val symbol: String,
    val direction: SignalDirection,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val strategyId: String,
    val createdTimestamp: Long
)

data class ExposureSummary(
    val totalOpenPositionsCount: Int,
    val symbolPositionsCount: Map<String, Int>,
    val assetClassPositionsCount: Map<String, Int>,
    val longNotional: Double,
    val shortNotional: Double,
    val totalNotional: Double,
    val totalRiskAmount: Double,
    val totalAccountEquity: Double,
    val currentDrawdownPercent: Double,
    val dailyLossAmount: Double
)

data class CorrelationWarning(
    val symbol1: String,
    val symbol2: String,
    val groupName: String,
    val correlationValue: Double,
    val explanation: String
)

data class CorrelationReport(
    val hasExcessiveCorrelation: Boolean,
    val warnings: List<CorrelationWarning>,
    val activeCorrelatedGroups: List<String>
)

data class PortfolioRiskReport(
    val isApproved: Boolean,
    val currentRiskPercent: Double,
    val proposedRiskPercent: Double,
    val riskAfterTradePercent: Double,
    val exposureChanges: String,
    val rejectionReasons: List<NoTradeReason>,
    val warnings: List<String>,
    val recommendedPositionSizeMultiplier: Double
)

data class StrategyPerformanceMetrics(
    val strategyId: String,
    val symbol: String,
    val regime: MarketRegime,
    val tradeCount: Int,
    val winRate: Double,          // 0.0 to 1.0
    val profitFactor: Double,
    val expectancy: Double,
    val maxDrawdown: Double,      // percentage
    val sharpeRatio: Double,
    val sortinoRatio: Double,
    val isReliable: Boolean       // True if tradeCount >= 30 and verified
)

enum class MergePolicy {
    HIGHEST_SCORE_ONLY,
    CONSERVATIVE_RISK,
    WEIGHTED_ENTRY,
    STRONGEST_STRUCTURE,
    NO_MERGE
}

enum class DecisionOutcome {
    NO_TRADE,
    WATCHLIST,
    PAPER_TRADE_CANDIDATE,
    PAPER_EXECUTION_APPROVED
}
