package com.example.trading.analysis

import java.time.Instant

enum class OpportunityDirection {
    LONG,
    SHORT,
    NEUTRAL,
    NO_TRADE
}

enum class OpportunityEligibility {
    ELIGIBLE,
    INELIGIBLE_DATA_NOT_READY,
    INELIGIBLE_STALE_DATA,
    INELIGIBLE_RISK_REJECTED,
    INELIGIBLE_BELOW_THRESHOLD,
    INELIGIBLE_CONFLICT,
    PROVIDER_REGION_BLOCKED
}

enum class ScoreCalculationStatus {
    SCORE_CALCULATED,
    SCORE_NOT_CALCULATED,
    VALID_SCORE_ZERO,
    NO_STRATEGY_SIGNAL,
    DATA_NOT_READY,
    DATA_INVALID,
    RISK_REJECTED,
    PROVIDER_REGION_BLOCKED
}

enum class TradePlanCalculationStatus {
    CALCULATED,
    NOT_CALCULATED,
    REJECTED,
    DATA_UNAVAILABLE
}

enum class ConfidenceMethod {
    MULTI_FACTOR_WEIGHTED,
    HISTORICAL_CALIBRATED,
    HEURISTIC,
    UNAVAILABLE
}

enum class CalibrationStatus {
    CALIBRATED,
    PARTIALLY_CALCULATED,
    UNCALIBRATED,
    INSUFFICIENT_EVIDENCE
}

enum class DataQualityStatus {
    VALID,
    STALE,
    DEGRADED,
    UNAVAILABLE
}

enum class DataAvailabilityStatus {
    AVAILABLE,
    UNAVAILABLE,
    PARTIAL
}

data class ScorePenalty(
    val reason: String,
    val pointsDeducted: Double
)

data class ScoreBreakdown(
    val trendScore: Double = 0.0,
    val trendMax: Double = 15.0,
    val momentumScore: Double = 0.0,
    val momentumMax: Double = 15.0,
    val structureScore: Double = 0.0,
    val structureMax: Double = 15.0,
    val volumeScore: Double = 0.0,
    val volumeMax: Double = 8.0,
    val volatilityScore: Double = 0.0,
    val volatilityMax: Double = 7.0,
    val signalScore: Double = 0.0,
    val signalMax: Double = 15.0,
    val riskRewardScore: Double = 0.0,
    val riskRewardMax: Double = 10.0,
    val freshnessScore: Double = 0.0,
    val freshnessMax: Double = 5.0,
    val dataQualityScore: Double = 0.0,
    val dataQualityMax: Double = 5.0,
    val portfolioScore: Double = 0.0,
    val portfolioMax: Double = 5.0,
    val totalPenalties: Double = 0.0,
    val rawSubtotal: Double = 0.0,
    val maxPossiblePoints: Double = 100.0,
    val finalScore: Double = 0.0
)

data class AlphaTradePlan(
    val entryPrice: Double?,
    val stopLossPrice: Double?,
    val takeProfitPrice: Double?,
    val stopDistancePercent: Double?,
    val targetDistancePercent: Double?,
    val riskRewardRatio: Double?,
    val positionSize: Double?,
    val notionalValue: Double?,
    val generatedAtEpochMs: Long = System.currentTimeMillis(),
    val calculationStatus: TradePlanCalculationStatus = TradePlanCalculationStatus.CALCULATED,
    val authorizationStatusLabel: String = "Calculated Size — Not Authorized",
    val unavailableReason: String? = null
)

data class AlphaConfidence(
    val confidencePercent: Double?,
    val method: ConfidenceMethod,
    val sampleSize: Int?,
    val calibrationStatus: CalibrationStatus,
    val unavailableReason: String? = null
)

data class HistoricalPerformanceEvidence(
    val strategyId: String,
    val marketRegime: String,
    val symbol: String?,
    val sampleSize: Int,
    val winRatePercent: Double?,
    val profitFactor: Double?,
    val expectancy: Double?,
    val averageWin: Double?,
    val averageLoss: Double?,
    val maximumDrawdownPercent: Double?,
    val evidenceWindowStartEpochMs: Long?,
    val evidenceWindowEndEpochMs: Long?,
    val valid: Boolean,
    val unavailableReason: String? = null
)

data class MarketPressureSnapshot(
    val provider: String,
    val symbol: String,
    val bidVolume: Double?,
    val askVolume: Double?,
    val bidPercent: Double?,
    val askPercent: Double?,
    val deltaPercent: Double?,
    val orderBookImbalance: Double?,
    val depthLevelsUsed: Int?,
    val eventTimeEpochMs: Long?,
    val receivedAtEpochMs: Long = System.currentTimeMillis(),
    val freshnessMs: Long = 0L,
    val qualityStatus: DataQualityStatus = DataQualityStatus.VALID,
    val dataOrigin: String = "Executed Buy/Sell Flow",
    val unavailableReason: String? = null
)

data class LiquidityEvidence(
    val liquidityScore: Double?,
    val spreadBps: Double?,
    val estimatedSlippageBps: Double?,
    val depthNearMid: Double?,
    val fundingRate: Double?,
    val openInterest: Double?,
    val liquidationPressure: Double?,
    val sourceProvider: String?,
    val timestampEpochMs: Long?,
    val status: DataAvailabilityStatus = DataAvailabilityStatus.AVAILABLE,
    val unavailableReason: String? = null
)

data class AlphaReasonSummary(
    val positiveReasons: List<String> = emptyList(),
    val negativeReasons: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val executionBlockers: List<String> = emptyList()
)

enum class ExecutionStatus {
    EXECUTION_DISABLED,
    DUPLICATE_BLOCKED,
    COOLDOWN,
    BELOW_THRESHOLD,
    RISK_REJECTED,
    WAITING_FOR_CONFIRMATION,
    PORTFOLIO_REJECTED,
    EXECUTION_ELIGIBLE,
    APPROVED_FOR_EXECUTION,
    PAPER_TRADE_OPENED,
    NOT_APPROVED,
    EXECUTION_ERROR,
    ORDER_OPENED,
    ORDER_FAILED,
    ELIGIBLE
}

enum class ExecutionReasonCode {
    SCORE_BELOW_THRESHOLD,
    RISK_RULE_FAILED,
    STRATEGY_SIGNAL_NOT_CONFIRMED,
    PORTFOLIO_POLICY_REJECTED,
    MAX_CONCURRENT_POSITIONS_REACHED,
    DUPLICATE_SYMBOL,
    COOLDOWN_ACTIVE,
    AUTO_TRADING_DISABLED,
    READY_FOR_EXECUTION,
    PAPER_TRADE_OPENED,
    UNCLASSIFIED_NOT_APPROVED,
    EXECUTION_RUNTIME_ERROR
}

enum class GateState {
    PASSED,
    FAILED,
    NOT_EVALUATED
}

fun resolveExecutionStatus(
    paperExecutionEnabled: Boolean,
    scoreGatePassed: Boolean,
    riskApproved: Boolean,
    riskRewardApproved: Boolean,
    positionSize: Double,
    strategyConfirmed: Boolean,
    portfolioApproved: Boolean,
    duplicateBlocked: Boolean,
    cooldownBlocked: Boolean,
    approvedForExecution: Boolean
): ExecutionStatus = when {
    !paperExecutionEnabled -> ExecutionStatus.EXECUTION_DISABLED
    duplicateBlocked -> ExecutionStatus.DUPLICATE_BLOCKED
    cooldownBlocked -> ExecutionStatus.COOLDOWN
    !scoreGatePassed -> ExecutionStatus.BELOW_THRESHOLD
    !riskApproved || !riskRewardApproved || positionSize <= 0.0 -> ExecutionStatus.RISK_REJECTED
    !strategyConfirmed -> ExecutionStatus.WAITING_FOR_CONFIRMATION
    !portfolioApproved -> ExecutionStatus.PORTFOLIO_REJECTED
    approvedForExecution -> ExecutionStatus.APPROVED_FOR_EXECUTION
    scoreGatePassed && riskApproved && riskRewardApproved && positionSize > 0.0 && strategyConfirmed && portfolioApproved -> ExecutionStatus.EXECUTION_ELIGIBLE
    else -> ExecutionStatus.NOT_APPROVED
}

fun resolveExecutionReasonCode(
    status: ExecutionStatus,
    blockingReasons: List<String> = emptyList()
): ExecutionReasonCode = when (status) {
    ExecutionStatus.EXECUTION_DISABLED -> ExecutionReasonCode.AUTO_TRADING_DISABLED
    ExecutionStatus.DUPLICATE_BLOCKED -> ExecutionReasonCode.DUPLICATE_SYMBOL
    ExecutionStatus.COOLDOWN -> ExecutionReasonCode.COOLDOWN_ACTIVE
    ExecutionStatus.BELOW_THRESHOLD -> ExecutionReasonCode.SCORE_BELOW_THRESHOLD
    ExecutionStatus.RISK_REJECTED -> ExecutionReasonCode.RISK_RULE_FAILED
    ExecutionStatus.WAITING_FOR_CONFIRMATION -> ExecutionReasonCode.STRATEGY_SIGNAL_NOT_CONFIRMED
    ExecutionStatus.PORTFOLIO_REJECTED -> ExecutionReasonCode.PORTFOLIO_POLICY_REJECTED
    ExecutionStatus.EXECUTION_ELIGIBLE,
    ExecutionStatus.APPROVED_FOR_EXECUTION -> ExecutionReasonCode.READY_FOR_EXECUTION
    ExecutionStatus.PAPER_TRADE_OPENED, ExecutionStatus.ORDER_OPENED -> ExecutionReasonCode.PAPER_TRADE_OPENED
    ExecutionStatus.EXECUTION_ERROR, ExecutionStatus.ORDER_FAILED -> ExecutionReasonCode.EXECUTION_RUNTIME_ERROR
    ExecutionStatus.NOT_APPROVED, ExecutionStatus.ELIGIBLE -> ExecutionReasonCode.UNCLASSIFIED_NOT_APPROVED
}

fun resolveHumanReadableReason(
    status: ExecutionStatus,
    reasonCode: ExecutionReasonCode,
    blockingReasons: List<String> = emptyList()
): String {
    if (blockingReasons.isNotEmpty()) {
        return blockingReasons.first()
    }
    return when (status) {
        ExecutionStatus.EXECUTION_DISABLED -> "Auto paper trading disabled in settings"
        ExecutionStatus.DUPLICATE_BLOCKED -> "Active trade already exists for symbol"
        ExecutionStatus.COOLDOWN -> "Symbol is currently in cooldown period"
        ExecutionStatus.BELOW_THRESHOLD -> "Alpha Score below threshold"
        ExecutionStatus.RISK_REJECTED -> "Trade failed account risk rules"
        ExecutionStatus.WAITING_FOR_CONFIRMATION -> "Waiting for strategy signal confirmation"
        ExecutionStatus.PORTFOLIO_REJECTED -> "Portfolio manager rejected trade allocation"
        ExecutionStatus.EXECUTION_ELIGIBLE -> "All execution gates passed — eligible"
        ExecutionStatus.APPROVED_FOR_EXECUTION -> "Trade fully approved for execution"
        ExecutionStatus.PAPER_TRADE_OPENED, ExecutionStatus.ORDER_OPENED -> "Paper trade successfully opened"
        ExecutionStatus.EXECUTION_ERROR, ExecutionStatus.ORDER_FAILED -> "Runtime error during order execution"
        ExecutionStatus.NOT_APPROVED, ExecutionStatus.ELIGIBLE -> "Not approved for execution"
    }
}

data class ExecutionDecision(
    val symbol: String,
    val direction: OpportunityDirection,
    val finalAlphaScore: Double,
    val alphaThreshold: Double,
    val alphaEligible: Boolean,
    val strategyConfirmed: Boolean,
    val signalFresh: Boolean,
    val riskRewardApproved: Boolean,
    val riskApproved: Boolean,
    val portfolioApproved: Boolean,
    val positionSize: Double,
    val paperExecutionEnabled: Boolean,
    val duplicateBlocked: Boolean,
    val cooldownBlocked: Boolean,
    val killSwitchActive: Boolean = false,
    val scoreGatePassed: Boolean = (finalAlphaScore >= alphaThreshold && alphaEligible),
    val approvedForExecution: Boolean = (
        scoreGatePassed &&
        strategyConfirmed &&
        signalFresh &&
        riskRewardApproved &&
        riskApproved &&
        portfolioApproved &&
        positionSize > 0.0 &&
        paperExecutionEnabled &&
        !duplicateBlocked &&
        !cooldownBlocked &&
        !killSwitchActive
    ),
    val executionStatus: ExecutionStatus = resolveExecutionStatus(
        paperExecutionEnabled = paperExecutionEnabled,
        scoreGatePassed = scoreGatePassed,
        riskApproved = riskApproved,
        riskRewardApproved = riskRewardApproved,
        positionSize = positionSize,
        strategyConfirmed = strategyConfirmed,
        portfolioApproved = portfolioApproved,
        duplicateBlocked = duplicateBlocked,
        cooldownBlocked = cooldownBlocked,
        approvedForExecution = approvedForExecution
    ),
    val status: ExecutionStatus = executionStatus,
    val reasonCode: ExecutionReasonCode = resolveExecutionReasonCode(executionStatus),
    val humanReadableReason: String = resolveHumanReadableReason(executionStatus, reasonCode),
    val blockingReasons: List<String> = emptyList(),
    val evaluatedAt: Instant = Instant.now(),
    val simulatedOrderId: String? = null,
    val persistedPositionId: String? = null,
    val thresholdUsed: Double = alphaThreshold,
    val thresholdSettingsVersion: Long = 1L,
    val settingsVersion: Long = thresholdSettingsVersion,
    val scanStartedAtEpochMs: Long = 0L,
    val decisionCreatedAtEpochMs: Long = System.currentTimeMillis(),
    val createdAtEpochMs: Long = decisionCreatedAtEpochMs
)

data class AlphaOpportunityScore(
    val symbol: String,
    val score: Double,
    val direction: OpportunityDirection,
    val eligibility: OpportunityEligibility,
    val marketRegime: MarketRegime,
    val calculationStatus: ScoreCalculationStatus = ScoreCalculationStatus.SCORE_CALCULATED,
    val strategyId: String? = null,
    val signalScore: Double = 0.0,
    val trendScore: Double = 0.0,
    val momentumScore: Double = 0.0,
    val structureScore: Double = 0.0,
    val volumeScore: Double = 0.0,
    val volatilityScore: Double = 0.0,
    val riskRewardScore: Double = 0.0,
    val freshnessScore: Double = 0.0,
    val dataQualityScore: Double = 0.0,
    val portfolioScore: Double = 0.0,
    val penalties: List<ScorePenalty> = emptyList(),
    val rejectionReasons: List<String> = emptyList(),
    val evaluatedAt: Instant = Instant.now(),
    val evidenceId: String = "",
    val componentBreakdown: ScoreBreakdown = ScoreBreakdown(),
    val scoringModelVersion: String = "v2.0_100pt_exact",
    val scoreCalculatedAt: Instant = evaluatedAt,
    val eligibilityThresholdUsed: Double = 75.0,
    val thresholdSettingsVersion: Long = 1L,
    val scanStartedAtEpochMs: Long = 0L,
    val executionDecision: ExecutionDecision? = null,
    val confidence: AlphaConfidence? = null,
    val tradePlan: AlphaTradePlan? = null,
    val historicalPerformance: HistoricalPerformanceEvidence? = null,
    val marketPressure: MarketPressureSnapshot? = null,
    val liquidityEvidence: LiquidityEvidence? = null,
    val reasonSummary: AlphaReasonSummary? = null,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis(),
    val dataAgeMs: Long = 0L,
    val activeProvider: String = "OKX_SWAP_PUBLIC",
    val providerSymbol: String = symbol,
    val dataOrigin: String = "REST_BOOTSTRAP"
)

data class AlphaOpportunityScanResult(
    val totalPairsScanned: Int = 10,
    val eligiblePairsCount: Int = 0,
    val analysisValidCount: Int = 0,
    val aboveScoreThresholdCount: Int = 0,
    val riskApprovedCount: Int = 0,
    val portfolioApprovedCount: Int = 0,
    val executionEligibleCount: Int = 0,
    val openedPaperTradesCount: Int = 0,
    val topOpportunity: AlphaOpportunityScore? = null,
    val scores: List<AlphaOpportunityScore> = emptyList(),
    val scannedAt: Instant = Instant.now()
)

