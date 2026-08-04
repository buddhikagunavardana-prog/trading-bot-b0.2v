package com.example.trading.analysis

import com.example.data.TradeOrderEntity
import com.example.model.CryptoTicker
import com.example.trading.portfolio.PortfolioDecision
import com.example.trading.risk.AccountRiskState
import com.example.trading.risk.RiskDecision
import com.example.trading.risk.RiskEngine
import com.example.trading.risk.RiskRejectionReason
import com.example.trading.strategy.SignalDirection
import com.example.trading.validation.SymbolNormalizer
import java.time.Instant
import java.util.Locale

class ExecutionDecisionEngine(
    private val riskEngine: RiskEngine = RiskEngine()
) {

    fun evaluateExecution(
        score: AlphaOpportunityScore,
        ticker: CryptoTicker?,
        mtfSnapshot: MultiTimeframeSnapshot?,
        configuredThreshold: Double = score.eligibilityThresholdUsed,
        paperExecutionEnabled: Boolean,
        activeTrades: List<TradeOrderEntity>,
        portfolioDecision: PortfolioDecision?,
        accountRiskState: AccountRiskState,
        cooldownSymbols: Set<String> = emptySet(),
        globalKillSwitch: Boolean = false,
        currentTimeMs: Long = System.currentTimeMillis(),
        settingsVersion: Long = score.thresholdSettingsVersion,
        scanStartedAtEpochMs: Long = score.scanStartedAtEpochMs
    ): ExecutionDecision {
        val symbol = score.symbol
        val direction = score.direction
        val finalAlphaScore = score.score
        val alphaEligible = (score.eligibility == OpportunityEligibility.ELIGIBLE) && (finalAlphaScore >= configuredThreshold)

        val scoreGateResult = if (finalAlphaScore >= configuredThreshold) "PASSED" else "FAILED"
        safeLogI("AlphaThresholdPipeline", "STAGE 4 [EXECUTION_ENGINE] Symbol=$symbol Score=${String.format(Locale.US, "%.1f", finalAlphaScore)} Threshold=${String.format(Locale.US, "%.1f", configuredThreshold)} ScoreGate=$scoreGateResult")

        val blockingReasons = mutableListOf<String>()
        val scoreGatePassed = (finalAlphaScore >= configuredThreshold) && (score.eligibility == OpportunityEligibility.ELIGIBLE)

        if (!scoreGatePassed) {
            if (finalAlphaScore < configuredThreshold) {
                blockingReasons.add("SCORE_BELOW_THRESHOLD (${String.format(Locale.US, "%.1f", finalAlphaScore)} < $configuredThreshold)")
            }
            if (score.eligibility != OpportunityEligibility.ELIGIBLE) {
                blockingReasons.add("NOT_ALPHA_ELIGIBLE (${score.eligibility.name})")
            }
        }

        // Downstream gate evaluations occur sequentially only if preceding gates allow
        val latestCandleTs = mtfSnapshot?.m5?.latestCandle?.closeTimestamp ?: 0L
        val ageMs = if (latestCandleTs > 0) Math.abs(currentTimeMs - latestCandleTs) else Long.MAX_VALUE
        val signalFresh = (ageMs <= 15 * 60_000L) && (score.componentBreakdown.freshnessScore > 0.0)

        val riskRewardApproved = score.componentBreakdown.riskRewardScore > 0.0

        // Strategy Confirmation Check (Only evaluate if score gate passed)
        val matchingCandidate = portfolioDecision?.rankedCandidates?.find {
            SymbolNormalizer.isSameSymbol(it.normalisedCandidate.signal.symbol, symbol)
        } ?: portfolioDecision?.bestCandidate?.takeIf {
            SymbolNormalizer.isSameSymbol(it.normalisedCandidate.signal.symbol, symbol)
        }
        val strategyConfirmed = if (scoreGatePassed) {
            matchingCandidate != null && (
                matchingCandidate.normalisedCandidate.signal.decision == com.example.trading.strategy.SignalDecision.APPROVED ||
                matchingCandidate.normalisedCandidate.signal.decision == com.example.trading.strategy.SignalDecision.PAPER_TRADE ||
                matchingCandidate.normalisedCandidate.signal.isPaperTradeEligible
            )
        } else false

        if (scoreGatePassed && !strategyConfirmed) {
            blockingReasons.add("STRATEGY_NOT_CONFIRMED")
        }

        if (scoreGatePassed && !signalFresh) {
            blockingReasons.add("SIGNAL_STALE (${if (latestCandleTs > 0) ageMs / 60000 else -1}m old)")
        }

        if (scoreGatePassed && !riskRewardApproved) {
            blockingReasons.add("RISK_REWARD_NOT_APPROVED")
        }

        // Risk Engine Evaluation (Only evaluate if score gate passed)
        val entryPrice = ticker?.price ?: 0.0
        val (stopLoss, takeProfit) = if (direction == OpportunityDirection.LONG) {
            Pair(entryPrice * 0.98, entryPrice * 1.05)
        } else {
            Pair(entryPrice * 1.02, entryPrice * 0.95)
        }

        val riskDecision = if (scoreGatePassed && entryPrice > 0.0 && (direction == OpportunityDirection.LONG || direction == OpportunityDirection.SHORT)) {
            riskEngine.validateTradeRisk(
                symbol = symbol,
                direction = if (direction == OpportunityDirection.LONG) SignalDirection.LONG else SignalDirection.SHORT,
                entryPrice = entryPrice,
                stopLossPrice = stopLoss,
                takeProfitPrice = takeProfit,
                spreadPercent = 0.05,
                accountState = accountRiskState,
                currentTimeMs = currentTimeMs
            )
        } else {
            RiskDecision(
                isApproved = false,
                calculatedRiskUsdt = 0.0,
                recommendedPositionSize = 0.0,
                riskRewardRatio = 0.0,
                rejectionReasons = if (!scoreGatePassed) emptyList() else listOf(RiskRejectionReason.INVALID_ENTRY_PRICE)
            )
        }

        val riskApproved = if (scoreGatePassed) riskDecision.isApproved else false
        val positionSize = if (riskApproved) riskDecision.recommendedPositionSize else 0.0

        if (scoreGatePassed && !riskApproved) {
            riskDecision.rejectionReasons.forEach { reason ->
                blockingReasons.add("RISK_REJECTED: $reason")
            }
        }

        if (scoreGatePassed && riskApproved && positionSize <= 0.0) {
            blockingReasons.add("POSITION_SIZE_ZERO")
        }

        // Portfolio Approved Check (Only evaluate if score gate passed)
        val portfolioApproved = if (scoreGatePassed) {
            portfolioDecision == null ||
            portfolioDecision.finalDecision == com.example.trading.portfolio.DecisionOutcome.PAPER_EXECUTION_APPROVED ||
            portfolioDecision.finalDecision == com.example.trading.portfolio.DecisionOutcome.PAPER_TRADE_CANDIDATE
        } else false

        if (scoreGatePassed && !portfolioApproved) {
            blockingReasons.add("PORTFOLIO_REJECTED")
        }

        if (!paperExecutionEnabled) {
            blockingReasons.add("PAPER_EXECUTION_DISABLED")
        }

        // Duplicate Check
        val duplicateBlocked = activeTrades.any {
            SymbolNormalizer.isSameSymbol(it.symbol, symbol) && it.status == "ACTIVE"
        }

        if (scoreGatePassed && duplicateBlocked) {
            blockingReasons.add("DUPLICATE_POSITION_EXISTS")
        }

        // Cooldown Check
        val canonicalSymbol = SymbolNormalizer.toCanonicalDisplay(symbol)
        val cooldownBlocked = cooldownSymbols.contains(canonicalSymbol) || cooldownSymbols.contains(symbol)

        if (scoreGatePassed && cooldownBlocked) {
            blockingReasons.add("SYMBOL_COOLDOWN_ACTIVE")
        }

        if (globalKillSwitch) {
            blockingReasons.add("GLOBAL_KILL_SWITCH_ACTIVE")
        }

        // Strict Authoritative Rule:
        val approvedForExecution =
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
            !globalKillSwitch

        val executionStatus = resolveExecutionStatus(
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
        )

        val reasonCode = resolveExecutionReasonCode(executionStatus, blockingReasons)
        val humanReadableReason = resolveHumanReadableReason(executionStatus, reasonCode, blockingReasons)

        val scoreGateStr = if (scoreGatePassed) "PASS" else "FAIL"
        val riskStr = if (!scoreGatePassed) "NOT_EVALUATED" else if (riskApproved && riskRewardApproved && positionSize > 0.0) "PASS" else "FAIL"
        val strategyStr = if (!scoreGatePassed || riskStr == "FAIL" || riskStr == "NOT_EVALUATED") "NOT_EVALUATED" else if (strategyConfirmed) "PASS" else "FAIL"
        val portfolioStr = if (strategyStr != "PASS") "NOT_EVALUATED" else if (portfolioApproved) "PASS" else "FAIL"

        safeLogI(
            "ExecutionDecisionEngine",
            "$symbol score=${String.format(Locale.US, "%.1f", finalAlphaScore)} threshold=${String.format(Locale.US, "%.1f", configuredThreshold)} scoreGate=$scoreGateStr risk=$riskStr strategy=$strategyStr portfolio=$portfolioStr status=$executionStatus reason=${reasonCode.name}"
        )

        return ExecutionDecision(
            symbol = symbol,
            direction = direction,
            finalAlphaScore = finalAlphaScore,
            alphaThreshold = configuredThreshold,
            alphaEligible = alphaEligible,
            strategyConfirmed = strategyConfirmed,
            signalFresh = signalFresh,
            riskRewardApproved = riskRewardApproved,
            riskApproved = riskApproved,
            portfolioApproved = portfolioApproved,
            positionSize = positionSize,
            paperExecutionEnabled = paperExecutionEnabled,
            duplicateBlocked = duplicateBlocked,
            cooldownBlocked = cooldownBlocked,
            killSwitchActive = globalKillSwitch,
            scoreGatePassed = scoreGatePassed,
            approvedForExecution = approvedForExecution,
            executionStatus = executionStatus,
            status = executionStatus,
            reasonCode = reasonCode,
            humanReadableReason = humanReadableReason,
            blockingReasons = if (approvedForExecution) emptyList() else blockingReasons.distinct(),
            evaluatedAt = Instant.ofEpochMilli(currentTimeMs),
            thresholdUsed = configuredThreshold,
            thresholdSettingsVersion = settingsVersion,
            settingsVersion = settingsVersion,
            scanStartedAtEpochMs = scanStartedAtEpochMs,
            decisionCreatedAtEpochMs = currentTimeMs,
            createdAtEpochMs = currentTimeMs
        )
    }

    private fun safeLogI(tag: String, msg: String) {
        runCatching { android.util.Log.i(tag, msg) }
    }
}
