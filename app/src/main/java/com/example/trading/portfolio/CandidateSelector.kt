package com.example.trading.portfolio

import com.example.trading.strategy.NoTradeReason

data class SelectionResult(
    val bestCandidate: RankedCandidate?,
    val decisionOutcome: DecisionOutcome,
    val noTradeReasons: List<NoTradeReason>,
    val explanation: String
)

class CandidateSelector {

    fun selectBestCandidate(
        rankedCandidates: List<RankedCandidate>,
        conflictReport: ConflictReport,
        riskReport: PortfolioRiskReport,
        config: PortfolioConfig
    ): SelectionResult {
        val noTradeReasons = mutableListOf<NoTradeReason>()

        // 1. Kill Switch or Critical Conflict check
        if (config.isGlobalKillSwitchActive) {
            noTradeReasons.add(NoTradeReason.RISK_ENGINE_REJECTED)
            return SelectionResult(
                bestCandidate = null,
                decisionOutcome = DecisionOutcome.NO_TRADE,
                noTradeReasons = noTradeReasons,
                explanation = "Global Kill Switch active. Decision: NO_TRADE"
            )
        }

        if (conflictReport.hasUnresolvedHighOrCritical) {
            noTradeReasons.add(NoTradeReason.RISK_ENGINE_REJECTED)
            return SelectionResult(
                bestCandidate = null,
                decisionOutcome = DecisionOutcome.NO_TRADE,
                noTradeReasons = noTradeReasons,
                explanation = "Unresolved HIGH or CRITICAL conflicts exist. Decision: NO_TRADE"
            )
        }

        if (!riskReport.isApproved) {
            noTradeReasons.addAll(riskReport.rejectionReasons)
            return SelectionResult(
                bestCandidate = null,
                decisionOutcome = DecisionOutcome.NO_TRADE,
                noTradeReasons = noTradeReasons,
                explanation = "Portfolio risk validation rejected candidate. Reasons: ${riskReport.rejectionReasons}"
            )
        }

        if (rankedCandidates.isEmpty()) {
            noTradeReasons.add(NoTradeReason.LOW_SIGNAL_SCORE)
            return SelectionResult(
                bestCandidate = null,
                decisionOutcome = DecisionOutcome.NO_TRADE,
                noTradeReasons = noTradeReasons,
                explanation = "No ranked candidates available. Decision: NO_TRADE"
            )
        }

        val topCandidate = rankedCandidates.first()

        // 2. Score threshold check
        if (topCandidate.normalisedCandidate.normalisedScore < config.minNormalisedScore) {
            noTradeReasons.add(NoTradeReason.LOW_SIGNAL_SCORE)
            return SelectionResult(
                bestCandidate = null,
                decisionOutcome = DecisionOutcome.NO_TRADE,
                noTradeReasons = noTradeReasons,
                explanation = "Top candidate score %.1f is below min score threshold %.1f".format(
                    topCandidate.normalisedCandidate.normalisedScore, config.minNormalisedScore
                )
            )
        }

        // 3. Score gap check if second candidate exists
        if (rankedCandidates.size >= 2) {
            val secondCandidate = rankedCandidates[1]
            val gap = topCandidate.normalisedCandidate.normalisedScore - secondCandidate.normalisedCandidate.normalisedScore

            if (gap < config.minScoreGapBetweenTopCandidates) {
                // If top two are on different directions or same symbol, enforce score gap
                if (topCandidate.normalisedCandidate.signal.direction != secondCandidate.normalisedCandidate.signal.direction) {
                    noTradeReasons.add(NoTradeReason.LOW_SIGNAL_SCORE)
                    return SelectionResult(
                        bestCandidate = null,
                        decisionOutcome = DecisionOutcome.NO_TRADE,
                        noTradeReasons = noTradeReasons,
                        explanation = "Top candidate score gap %.1f is below required gap %.1f over 2nd candidate".format(
                            gap, config.minScoreGapBetweenTopCandidates
                        )
                    )
                }
            }
        }

        // 4. R:R check
        if (topCandidate.normalisedCandidate.signal.riskRewardRatio < config.minRewardToRiskRatio) {
            noTradeReasons.add(NoTradeReason.POOR_RISK_REWARD)
            return SelectionResult(
                bestCandidate = null,
                decisionOutcome = DecisionOutcome.NO_TRADE,
                noTradeReasons = noTradeReasons,
                explanation = "Top candidate R:R %.2f is below minimum required %.2f".format(
                    topCandidate.normalisedCandidate.signal.riskRewardRatio, config.minRewardToRiskRatio
                )
            )
        }

        // Top candidate passes all hard checks
        val outcome = if (topCandidate.normalisedCandidate.signal.isPaperTradeEligible) {
            DecisionOutcome.PAPER_TRADE_CANDIDATE
        } else {
            DecisionOutcome.WATCHLIST
        }

        return SelectionResult(
            bestCandidate = topCandidate,
            decisionOutcome = outcome,
            noTradeReasons = emptyList(),
            explanation = "Candidate ${topCandidate.normalisedCandidate.signal.strategyId} on ${topCandidate.normalisedCandidate.signal.symbol} approved as $outcome"
        )
    }
}
