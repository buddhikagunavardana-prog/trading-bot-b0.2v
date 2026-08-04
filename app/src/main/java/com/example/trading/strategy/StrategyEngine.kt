package com.example.trading.strategy

import com.example.trading.analysis.DataQualityResult
import com.example.trading.analysis.DataQualityValidator
import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.MarketRegimeDetector
import com.example.trading.analysis.MultiTimeframeSnapshot
import com.example.trading.analysis.Timeframe
import com.example.trading.risk.AccountRiskState
import com.example.trading.risk.RiskEngine

data class StrategyEngineResult(
    val symbol: String,
    val evaluationTimestamp: Long,
    val dataQualityResult: DataQualityResult,
    val detectedRegime: MarketRegime,
    val strategiesEvaluatedCount: Int,
    val signalsGenerated: List<StrategySignal>,
    val signalsRejected: List<StrategySignal>,
    val rankedAcceptedSignals: List<StrategySignal>,
    val bestCandidate: StrategySignal?,
    val noTradeReasons: List<NoTradeReason>,
    val totalExecutionTimeMs: Long,
    val errorsOrWarnings: List<String> = emptyList()
)

class StrategyEngine(
    val registry: StrategyRegistry = StrategyRegistry().apply {
        registerStrategy(BaselineTrendFollowStrategy())
        registerStrategy(TrendPullbackStrategy())
        registerStrategy(BreakoutRetestStrategy())
        registerStrategy(SmcLiquiditySweepStrategy())
        registerStrategy(RangeReversalStrategy())
        registerStrategy(MomentumContinuationStrategy())
    },
    val dataQualityValidator: DataQualityValidator = DataQualityValidator(),
    val regimeDetector: MarketRegimeDetector = MarketRegimeDetector(),
    val riskEngine: RiskEngine = RiskEngine()
) {
    private val selector = StrategySelector(registry)

    suspend fun evaluateSymbol(
        mtfSnapshot: MultiTimeframeSnapshot?,
        spreadPercent: Double = 0.05,
        accountState: AccountRiskState = AccountRiskState(),
        config: StrategyConfig = StrategyConfig(),
        currentTimeMs: Long = System.currentTimeMillis()
    ): StrategyEngineResult {
        val startTime = System.currentTimeMillis()
        val symbol = mtfSnapshot?.symbol ?: "UNKNOWN"
        val errorsOrWarnings = mutableListOf<String>()
        val noTradeReasons = mutableSetOf<NoTradeReason>()

        // 1. Data Quality Validation Gate
        val dataQualityResult = dataQualityValidator.validateMultiTimeframe(mtfSnapshot, currentTimeMs)
        if (!dataQualityResult.isValid) {
            noTradeReasons.add(NoTradeReason.STALE_DATA)
            dataQualityResult.issues.forEach { issue ->
                errorsOrWarnings.add("Data Quality Issue [${issue.code}]: ${issue.message}")
            }
            return StrategyEngineResult(
                symbol = symbol,
                evaluationTimestamp = currentTimeMs,
                dataQualityResult = dataQualityResult,
                detectedRegime = MarketRegime.UNSTABLE,
                strategiesEvaluatedCount = 0,
                signalsGenerated = emptyList(),
                signalsRejected = emptyList(),
                rankedAcceptedSignals = emptyList(),
                bestCandidate = null,
                noTradeReasons = noTradeReasons.toList(),
                totalExecutionTimeMs = System.currentTimeMillis() - startTime,
                errorsOrWarnings = errorsOrWarnings
            )
        }

        // 2. Detect Market Regime
        val regime = regimeDetector.detectRegime(
            mtf = mtfSnapshot,
            currentSpreadPercent = spreadPercent,
            isDataQualityValid = dataQualityResult.isValid
        )

        if (regime == MarketRegime.UNSTABLE || regime == MarketRegime.UNKNOWN) {
            noTradeReasons.add(NoTradeReason.UNSUPPORTED_REGIME)
        }

        // 3. Strategy Selection
        val availableTimeframes = mutableSetOf<Timeframe>()
        if (mtfSnapshot?.m5 != null) availableTimeframes.add(Timeframe.M5)
        if (mtfSnapshot?.m15 != null) availableTimeframes.add(Timeframe.M15)
        if (mtfSnapshot?.h1 != null) availableTimeframes.add(Timeframe.H1)

        val selectionReport = selector.selectStrategies(
            symbol = symbol,
            regime = regime,
            availableTimeframes = availableTimeframes,
            isDataQualityValid = dataQualityResult.isValid,
            config = config
        )

        val selectedStrategies = selectionReport.selectedStrategies
        if (selectedStrategies.isEmpty()) {
            noTradeReasons.add(NoTradeReason.UNSUPPORTED_REGIME)
            selectionReport.rejectedStrategiesWithReasons.forEach { (id, reason) ->
                errorsOrWarnings.add("Strategy '$id' rejected by selector: $reason")
            }
        }

        // 4. Build Context
        val hasActivePosition = accountState.activeSymbols.contains(symbol)
        val context = StrategyContext(
            symbol = symbol,
            m5Snapshot = mtfSnapshot?.m5,
            m15Snapshot = mtfSnapshot?.m15,
            h1Snapshot = mtfSnapshot?.h1,
            currentSpreadPercent = spreadPercent,
            dataTimestamp = currentTimeMs,
            hasActiveOpenPosition = hasActivePosition,
            accountRiskState = accountState,
            currentMarketRegime = regime,
            config = config
        )

        val signalsGenerated = mutableListOf<StrategySignal>()
        val signalsRejected = mutableListOf<StrategySignal>()
        val acceptedSignals = mutableListOf<StrategySignal>()

        // 5. Evaluate Strategies
        for (strategy in selectedStrategies) {
            try {
                val rawSignal = strategy.evaluate(context, config)
                signalsGenerated.add(rawSignal)

                if (rawSignal.decision == SignalDecision.REJECT) {
                    signalsRejected.add(rawSignal)
                    noTradeReasons.addAll(rawSignal.rejectionReasons)
                    continue
                }

                // 6. Risk Validation Gate
                val riskDecision = riskEngine.validateTradeRisk(
                    symbol = symbol,
                    direction = rawSignal.direction,
                    entryPrice = rawSignal.entryPrice,
                    stopLossPrice = rawSignal.proposedStopLoss,
                    takeProfitPrice = rawSignal.proposedTakeProfit,
                    spreadPercent = spreadPercent,
                    accountState = accountState,
                    currentTimeMs = currentTimeMs
                )

                if (!riskDecision.isApproved) {
                    noTradeReasons.add(NoTradeReason.RISK_ENGINE_REJECTED)
                    val rejectedDueToRisk = rawSignal.copy(
                        decision = SignalDecision.REJECT,
                        isPaperTradeEligible = false,
                        rejectionReasons = rawSignal.rejectionReasons + NoTradeReason.RISK_ENGINE_REJECTED,
                        evidence = rawSignal.evidence + "Risk Rejections: ${riskDecision.rejectionReasons}"
                    )
                    signalsRejected.add(rejectedDueToRisk)
                } else {
                    val approvedSignal = rawSignal.copy(
                        isPaperTradeEligible = true,
                        evidence = rawSignal.evidence + "Risk Approved: Size=${riskDecision.recommendedPositionSize}, R:R=${riskDecision.riskRewardRatio}"
                    )
                    acceptedSignals.add(approvedSignal)
                }
            } catch (e: Exception) {
                errorsOrWarnings.add("Exception evaluating strategy '${strategy.id}': ${e.message}")
            }
        }

        // 7. Rank Accepted Signals by Final Score
        val rankedAccepted = acceptedSignals.sortedByDescending { it.finalScore }
        val bestCandidate = rankedAccepted.firstOrNull()

        if (bestCandidate == null && signalsGenerated.isEmpty()) {
            noTradeReasons.add(NoTradeReason.INSUFFICIENT_DATA)
        }

        return StrategyEngineResult(
            symbol = symbol,
            evaluationTimestamp = currentTimeMs,
            dataQualityResult = dataQualityResult,
            detectedRegime = regime,
            strategiesEvaluatedCount = selectedStrategies.size,
            signalsGenerated = signalsGenerated,
            signalsRejected = signalsRejected,
            rankedAcceptedSignals = rankedAccepted,
            bestCandidate = bestCandidate,
            noTradeReasons = noTradeReasons.toList(),
            totalExecutionTimeMs = System.currentTimeMillis() - startTime,
            errorsOrWarnings = errorsOrWarnings
        )
    }
}
