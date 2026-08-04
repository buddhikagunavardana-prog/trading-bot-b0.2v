package com.example.trading.portfolio

import com.example.trading.analysis.DataQualityValidator
import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.MarketRegimeDetector
import com.example.trading.analysis.MultiTimeframeSnapshot
import com.example.trading.strategy.NoTradeReason
import com.example.trading.strategy.SignalDecision
import com.example.trading.strategy.StrategyConfig
import com.example.trading.strategy.StrategyContext
import com.example.trading.strategy.StrategyEngine
import com.example.trading.strategy.StrategyRegistry
import com.example.trading.strategy.StrategySelector
import com.example.trading.strategy.StrategySignal
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class StrategyPortfolioManager(
    val registry: StrategyRegistry = StrategyRegistry().apply {
        registerStrategy(com.example.trading.strategy.BaselineTrendFollowStrategy())
        registerStrategy(com.example.trading.strategy.TrendPullbackStrategy())
        registerStrategy(com.example.trading.strategy.BreakoutRetestStrategy())
        registerStrategy(com.example.trading.strategy.SmcLiquiditySweepStrategy())
        registerStrategy(com.example.trading.strategy.RangeReversalStrategy())
        registerStrategy(com.example.trading.strategy.MomentumContinuationStrategy())
    },
    val normaliser: SignalNormaliser = SignalNormaliser(),
    val conflictResolver: StrategyConflictResolver = StrategyConflictResolver(),
    val riskManager: PortfolioRiskManager = PortfolioRiskManager(),
    val ranker: SignalRanker = SignalRanker(),
    val selector: CandidateSelector = CandidateSelector(),
    val dataQualityValidator: DataQualityValidator = DataQualityValidator(),
    val regimeDetector: MarketRegimeDetector = MarketRegimeDetector()
) {
    private val strategySelector = StrategySelector(registry)

    suspend fun evaluatePortfolio(
        mtfSnapshots: List<MultiTimeframeSnapshot>,
        portfolioConfig: PortfolioConfig = PortfolioConfig(),
        strategyConfig: StrategyConfig = StrategyConfig(),
        currentTimeMs: Long = System.currentTimeMillis()
    ): PortfolioDecision {
        val startTime = System.currentTimeMillis()
        val evalId = "EVAL_${UUID.randomUUID().toString().take(8)}"
        val warnings = mutableListOf<String>()
        val noTradeReasons = mutableSetOf<NoTradeReason>()

        // Config validation
        val configErrors = portfolioConfig.validate()
        if (configErrors.isNotEmpty()) {
            configErrors.forEach { warnings.add("Config Error: $it") }
            if (portfolioConfig.failClosedOnException) {
                noTradeReasons.add(NoTradeReason.RISK_ENGINE_REJECTED)
                return createNoTradeDecision(
                    evalId = evalId,
                    evalTime = currentTimeMs,
                    symbols = mtfSnapshots.map { it.symbol },
                    reasons = noTradeReasons.toList(),
                    warnings = warnings,
                    durationMs = System.currentTimeMillis() - startTime,
                    configVersion = portfolioConfig.version
                )
            }
        }

        if (portfolioConfig.isGlobalKillSwitchActive) {
            noTradeReasons.add(NoTradeReason.RISK_ENGINE_REJECTED)
            warnings.add("Global Kill Switch is active")
            return createNoTradeDecision(
                evalId = evalId,
                evalTime = currentTimeMs,
                symbols = mtfSnapshots.map { it.symbol },
                reasons = noTradeReasons.toList(),
                warnings = warnings,
                durationMs = System.currentTimeMillis() - startTime,
                configVersion = portfolioConfig.version
            )
        }

        val rawSignals = mutableListOf<StrategySignal>()
        val marketRegimes = mutableMapOf<String, MarketRegime>()
        val evaluatedStrategies = mutableSetOf<String>()

        // Evaluate strategies across symbols safely with structured concurrency and timeouts
        try {
            coroutineScope {
                for (snapshot in mtfSnapshots) {
                    val symbol = snapshot.symbol
                    if (portfolioConfig.deniedSymbols.contains(symbol)) {
                        warnings.add("Symbol $symbol is in denied symbols list. Skipping.")
                        continue
                    }
                    if (portfolioConfig.allowedSymbols.isNotEmpty() && !portfolioConfig.allowedSymbols.contains(symbol)) {
                        warnings.add("Symbol $symbol not in allowed symbols list. Skipping.")
                        continue
                    }

                    // Data Quality check
                    val dataQualityResult = dataQualityValidator.validateMultiTimeframe(snapshot, currentTimeMs)
                    if (!dataQualityResult.isValid) {
                        warnings.add("Symbol $symbol failed data quality check")
                        noTradeReasons.add(NoTradeReason.STALE_DATA)
                        continue
                    }

                    val regime = regimeDetector.detectRegime(snapshot, 0.05, dataQualityResult.isValid)
                    marketRegimes[symbol] = regime

                    if (regime == MarketRegime.UNSTABLE || regime == MarketRegime.UNKNOWN) {
                        warnings.add("Symbol $symbol has UNSTABLE or UNKNOWN regime: $regime")
                        noTradeReasons.add(NoTradeReason.UNSUPPORTED_REGIME)
                        continue
                    }

                    val availableTimeframes = setOfNotNull(
                        snapshot.m5?.let { com.example.trading.analysis.Timeframe.M5 },
                        snapshot.m15?.let { com.example.trading.analysis.Timeframe.M15 },
                        snapshot.h1?.let { com.example.trading.analysis.Timeframe.H1 }
                    )

                    val selectionReport = strategySelector.selectStrategies(
                        symbol = symbol,
                        regime = regime,
                        availableTimeframes = availableTimeframes,
                        isDataQualityValid = true,
                        config = strategyConfig
                    )

                    val selectedStrategies = selectionReport.selectedStrategies.filter { strategy ->
                        portfolioConfig.enabledStrategyIds.contains(strategy.id) &&
                                !portfolioConfig.disabledStrategyIds.contains(strategy.id)
                    }

                    val context = StrategyContext(
                        symbol = symbol,
                        m5Snapshot = snapshot.m5,
                        m15Snapshot = snapshot.m15,
                        h1Snapshot = snapshot.h1,
                        currentSpreadPercent = 0.05,
                        dataTimestamp = currentTimeMs,
                        currentMarketRegime = regime,
                        config = strategyConfig
                    )

                    // Execute strategies concurrently with timeout and failure isolation
                    val evalDeferreds = selectedStrategies.map { strategy ->
                        evaluatedStrategies.add(strategy.id)
                        async {
                            withTimeoutOrNull(portfolioConfig.portfolioEvaluationTimeoutMs) {
                                try {
                                    strategy.evaluate(context, strategyConfig)
                                } catch (e: Exception) {
                                    warnings.add("Strategy '${strategy.id}' threw exception on $symbol: ${e.message}")
                                    null
                                }
                            } ?: run {
                                warnings.add("Strategy '${strategy.id}' timed out on $symbol")
                                null
                            }
                        }
                    }

                    val symbolSignals = evalDeferreds.mapNotNull { it.await() }
                    rawSignals.addAll(symbolSignals)
                }
            }
        } catch (e: Exception) {
            warnings.add("Unhandled exception during portfolio strategy evaluation: ${e.message}")
            if (portfolioConfig.failClosedOnException) {
                noTradeReasons.add(NoTradeReason.RISK_ENGINE_REJECTED)
                return createNoTradeDecision(
                    evalId = evalId,
                    evalTime = currentTimeMs,
                    symbols = mtfSnapshots.map { it.symbol },
                    reasons = noTradeReasons.toList(),
                    warnings = warnings,
                    durationMs = System.currentTimeMillis() - startTime,
                    configVersion = portfolioConfig.version
                )
            }
        }

        // Filter valid non-rejected signals
        val validSignals = rawSignals.filter { it.decision != SignalDecision.REJECT }
        if (validSignals.isEmpty()) {
            noTradeReasons.add(NoTradeReason.LOW_SIGNAL_SCORE)
            return createNoTradeDecision(
                evalId = evalId,
                evalTime = currentTimeMs,
                symbols = mtfSnapshots.map { it.symbol },
                rawSignals = rawSignals,
                reasons = noTradeReasons.toList(),
                warnings = warnings,
                durationMs = System.currentTimeMillis() - startTime,
                configVersion = portfolioConfig.version
            )
        }

        // 1. Signal Normalisation
        val normalisedCandidates = validSignals.map { signal ->
            val symbolRegime = marketRegimes[signal.symbol] ?: MarketRegime.UNKNOWN
            normaliser.normaliseSignal(signal, symbolRegime, portfolioConfig, currentTimeMs)
        }

        // Filter candidates meeting per-strategy min score
        val scoreFilteredCandidates = normalisedCandidates.filter { candidate ->
            val minScore = portfolioConfig.perStrategyMinScore[candidate.signal.strategyId] ?: 60
            candidate.normalisedScore >= minScore
        }

        if (scoreFilteredCandidates.isEmpty()) {
            noTradeReasons.add(NoTradeReason.LOW_SIGNAL_SCORE)
            return createNoTradeDecision(
                evalId = evalId,
                evalTime = currentTimeMs,
                symbols = mtfSnapshots.map { it.symbol },
                rawSignals = rawSignals,
                normalised = normalisedCandidates,
                reasons = noTradeReasons.toList(),
                warnings = warnings,
                durationMs = System.currentTimeMillis() - startTime,
                configVersion = portfolioConfig.version
            )
        }

        // 2. Conflict Detection & Resolution
        val conflictResult = conflictResolver.resolveConflicts(scoreFilteredCandidates, portfolioConfig)

        // 3. Signal Ranking
        val rankedCandidates = ranker.rankCandidates(conflictResult.resolvedCandidates, portfolioConfig)

        val topCandidate = rankedCandidates.firstOrNull()

        // 4. Portfolio Risk Validation
        val riskReport = if (topCandidate != null) {
            riskManager.validatePortfolioRisk(topCandidate.normalisedCandidate, portfolioConfig, currentTimeMs)
        } else {
            PortfolioRiskReport(
                isApproved = false,
                currentRiskPercent = 0.0,
                proposedRiskPercent = 0.0,
                riskAfterTradePercent = 0.0,
                exposureChanges = "None",
                rejectionReasons = listOf(NoTradeReason.LOW_SIGNAL_SCORE),
                warnings = emptyList(),
                recommendedPositionSizeMultiplier = 0.0
            )
        }

        // 5. Best Candidate Selection
        val selectionResult = selector.selectBestCandidate(
            rankedCandidates = rankedCandidates,
            conflictReport = conflictResult.conflictReport,
            riskReport = riskReport,
            config = portfolioConfig
        )

        // 6. Confidence Model Calculation
        val confidenceScore = calculatePortfolioConfidence(
            candidates = rankedCandidates,
            conflictReport = conflictResult.conflictReport,
            riskReport = riskReport
        )

        return PortfolioDecision(
            evaluationId = evalId,
            evaluationTimestamp = currentTimeMs,
            symbolsEvaluated = mtfSnapshots.map { it.symbol },
            strategiesEvaluated = evaluatedStrategies.toList(),
            marketRegimes = marketRegimes,
            rawStrategySignals = rawSignals,
            normalisedCandidates = normalisedCandidates,
            conflictReport = conflictResult.conflictReport,
            portfolioRiskReport = riskReport,
            rankedCandidates = rankedCandidates,
            bestCandidate = selectionResult.bestCandidate,
            finalDecision = selectionResult.decisionOutcome,
            decisionConfidence = confidenceScore,
            noTradeReasons = selectionResult.noTradeReasons + noTradeReasons,
            warnings = warnings,
            evaluationDurationMs = System.currentTimeMillis() - startTime,
            configVersion = portfolioConfig.version
        )
    }

    private fun calculatePortfolioConfidence(
        candidates: List<RankedCandidate>,
        conflictReport: ConflictReport,
        riskReport: PortfolioRiskReport
    ): Double {
        if (candidates.isEmpty()) return 0.0
        val top = candidates.first()
        var baseConf = top.confidence

        if (conflictReport.hasUnresolvedHighOrCritical) baseConf -= 30.0
        if (!riskReport.isApproved) baseConf -= 25.0
        if (candidates.size >= 2) {
            val gap = top.normalisedCandidate.normalisedScore - candidates[1].normalisedCandidate.normalisedScore
            if (gap < 5.0) baseConf -= 10.0
        }

        return baseConf.coerceIn(0.0, 100.0)
    }

    private fun createNoTradeDecision(
        evalId: String,
        evalTime: Long,
        symbols: List<String>,
        rawSignals: List<StrategySignal> = emptyList(),
        normalised: List<NormalisedCandidate> = emptyList(),
        reasons: List<NoTradeReason>,
        warnings: List<String>,
        durationMs: Long,
        configVersion: String
    ): PortfolioDecision {
        return PortfolioDecision(
            evaluationId = evalId,
            evaluationTimestamp = evalTime,
            symbolsEvaluated = symbols,
            strategiesEvaluated = emptyList(),
            marketRegimes = emptyMap(),
            rawStrategySignals = rawSignals,
            normalisedCandidates = normalised,
            conflictReport = ConflictReport(false, emptyList(), "No conflicts evaluated"),
            portfolioRiskReport = PortfolioRiskReport(false, 0.0, 0.0, 0.0, "None", reasons, warnings, 0.0),
            rankedCandidates = emptyList(),
            bestCandidate = null,
            finalDecision = DecisionOutcome.NO_TRADE,
            decisionConfidence = 0.0,
            noTradeReasons = reasons,
            warnings = warnings,
            evaluationDurationMs = durationMs,
            configVersion = configVersion
        )
    }
}
