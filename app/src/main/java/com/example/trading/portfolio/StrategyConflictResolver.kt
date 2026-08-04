package com.example.trading.portfolio

import com.example.trading.strategy.SignalDirection
import kotlin.math.abs

data class ConflictResolutionResult(
    val resolvedCandidates: List<NormalisedCandidate>,
    val conflictReport: ConflictReport
)

class StrategyConflictResolver {

    fun resolveConflicts(
        candidates: List<NormalisedCandidate>,
        config: PortfolioConfig
    ): ConflictResolutionResult {
        if (candidates.size <= 1) {
            return ConflictResolutionResult(
                resolvedCandidates = candidates,
                conflictReport = ConflictReport(
                    hasUnresolvedHighOrCritical = false,
                    conflicts = emptyList(),
                    summary = "No conflicts detected (<= 1 candidate)"
                )
            )
        }

        val conflictItems = mutableListOf<ConflictItem>()
        val acceptedCandidates = mutableListOf<NormalisedCandidate>()
        var unresolvedHighOrCritical = false

        // Group candidates by symbol
        val candidatesBySymbol = candidates.groupBy { it.signal.symbol }

        for ((symbol, symbolCandidates) in candidatesBySymbol) {
            if (symbolCandidates.size == 1) {
                acceptedCandidates.add(symbolCandidates.first())
                continue
            }

            // Check for opposite direction conflicts on the same symbol
            val longs = symbolCandidates.filter { it.signal.direction == SignalDirection.LONG }
            val shorts = symbolCandidates.filter { it.signal.direction == SignalDirection.SHORT }

            if (longs.isNotEmpty() && shorts.isNotEmpty()) {
                val bestLong = longs.maxByOrNull { it.normalisedScore }!!
                val bestShort = shorts.maxByOrNull { it.normalisedScore }!!

                val scoreGap = abs(bestLong.normalisedScore - bestShort.normalisedScore)
                val conflictId = "CONFLICT_${symbol}_OPPOSING_DIRECTIONS"

                if (scoreGap >= config.minScoreGapBetweenTopCandidates) {
                    val winner = if (bestLong.normalisedScore > bestShort.normalisedScore) bestLong else bestShort
                    val loser = if (winner == bestLong) bestShort else bestLong

                    conflictItems.add(
                        ConflictItem(
                            conflictId = conflictId,
                            symbols = listOf(symbol),
                            strategyIds = listOf(bestLong.signal.strategyId, bestShort.signal.strategyId),
                            directions = listOf(SignalDirection.LONG, SignalDirection.SHORT),
                            severity = ConflictSeverity.HIGH,
                            evidence = listOf(
                                "Long strategy score: %.1f".format(bestLong.normalisedScore),
                                "Short strategy score: %.1f".format(bestShort.normalisedScore),
                                "Score gap: %.1f >= min gap %.1f".format(scoreGap, config.minScoreGapBetweenTopCandidates)
                            ),
                            outcome = ConflictResolutionOutcome.RESOLVED_SINGLE_WINNER,
                            explanation = "Resolved in favor of ${winner.signal.strategyId} (${winner.signal.direction}) with score %.1f over ${loser.signal.strategyId} (%.1f)".format(winner.normalisedScore, loser.normalisedScore)
                        )
                    )
                    acceptedCandidates.add(winner)
                } else {
                    unresolvedHighOrCritical = true
                    conflictItems.add(
                        ConflictItem(
                            conflictId = conflictId,
                            symbols = listOf(symbol),
                            strategyIds = symbolCandidates.map { it.signal.strategyId },
                            directions = listOf(SignalDirection.LONG, SignalDirection.SHORT),
                            severity = ConflictSeverity.CRITICAL,
                            evidence = listOf(
                                "Long score: %.1f".format(bestLong.normalisedScore),
                                "Short score: %.1f".format(bestShort.normalisedScore),
                                "Score gap %.1f < min required gap %.1f".format(scoreGap, config.minScoreGapBetweenTopCandidates)
                            ),
                            outcome = ConflictResolutionOutcome.UNRESOLVED_REJECT_ALL,
                            explanation = "Unresolved opposing directions on $symbol. Scores too close. Rejecting all opposing candidates."
                        )
                    )
                }
                continue
            }

            // Same direction conflicts (Breakout vs Range Reversal, Trend Pullback vs Momentum, or Duplicate Signals)
            val sameDirectionCandidates = symbolCandidates.sortedByDescending { it.normalisedScore }
            val direction = sameDirectionCandidates.first().signal.direction

            val breakoutAndReversal = sameDirectionCandidates.partition {
                it.signal.strategyId == "breakout_retest" || it.signal.strategyId == "momentum_continuation"
            }
            val breakoutCandidates = breakoutAndReversal.first
            val rangeReversalCandidates = sameDirectionCandidates.filter { it.signal.strategyId == "range_reversal" }

            if (breakoutCandidates.isNotEmpty() && rangeReversalCandidates.isNotEmpty()) {
                val bestBreakout = breakoutCandidates.maxByOrNull { it.normalisedScore }!!
                val bestRange = rangeReversalCandidates.maxByOrNull { it.normalisedScore }!!

                val conflictId = "CONFLICT_${symbol}_BREAKOUT_VS_RANGE_REVERSAL"
                val winner = if (bestBreakout.normalisedScore >= bestRange.normalisedScore) bestBreakout else bestRange

                conflictItems.add(
                    ConflictItem(
                        conflictId = conflictId,
                        symbols = listOf(symbol),
                        strategyIds = listOf(bestBreakout.signal.strategyId, bestRange.signal.strategyId),
                        directions = listOf(direction),
                        severity = ConflictSeverity.MEDIUM,
                        evidence = listOf(
                            "Breakout strategy score: %.1f".format(bestBreakout.normalisedScore),
                            "Range Reversal score: %.1f".format(bestRange.normalisedScore)
                        ),
                        outcome = ConflictResolutionOutcome.RESOLVED_SINGLE_WINNER,
                        explanation = "Resolved conflict between Breakout and Range Reversal in favor of ${winner.signal.strategyId} (Score: %.1f)".format(winner.normalisedScore)
                    )
                )
                acceptedCandidates.add(winner)
            } else {
                // Duplicate / Multiple same-direction signals
                when (config.mergePolicy) {
                    MergePolicy.HIGHEST_SCORE_ONLY, MergePolicy.NO_MERGE -> {
                        val top = sameDirectionCandidates.first()
                        conflictItems.add(
                            ConflictItem(
                                conflictId = "CONFLICT_${symbol}_MULTIPLE_SAME_DIRECTION",
                                symbols = listOf(symbol),
                                strategyIds = sameDirectionCandidates.map { it.signal.strategyId },
                                directions = listOf(direction),
                                severity = ConflictSeverity.LOW,
                                evidence = sameDirectionCandidates.map { "${it.signal.strategyId}: score=%.1f".format(it.normalisedScore) },
                                outcome = ConflictResolutionOutcome.RESOLVED_SINGLE_WINNER,
                                explanation = "Selected single highest scoring candidate ${top.signal.strategyId} for $symbol $direction"
                            )
                        )
                        acceptedCandidates.add(top)
                    }
                    MergePolicy.CONSERVATIVE_RISK, MergePolicy.WEIGHTED_ENTRY, MergePolicy.STRONGEST_STRUCTURE -> {
                        val merged = mergeCandidates(sameDirectionCandidates, config)
                        conflictItems.add(
                            ConflictItem(
                                conflictId = "CONFLICT_${symbol}_MERGED_SAME_DIRECTION",
                                symbols = listOf(symbol),
                                strategyIds = sameDirectionCandidates.map { it.signal.strategyId },
                                directions = listOf(direction),
                                severity = ConflictSeverity.INFO,
                                evidence = sameDirectionCandidates.map { "${it.signal.strategyId}: score=%.1f".format(it.normalisedScore) },
                                outcome = ConflictResolutionOutcome.RESOLVED_MERGED,
                                explanation = "Merged ${sameDirectionCandidates.size} same-direction candidates using ${config.mergePolicy}"
                            )
                        )
                        acceptedCandidates.add(merged)
                    }
                }
            }
        }

        return ConflictResolutionResult(
            resolvedCandidates = acceptedCandidates,
            conflictReport = ConflictReport(
                hasUnresolvedHighOrCritical = unresolvedHighOrCritical,
                conflicts = conflictItems,
                summary = "Conflict resolution finished. Candidates in: ${candidates.size}, out: ${acceptedCandidates.size}, unresolved critical: $unresolvedHighOrCritical"
            )
        )
    }

    private fun mergeCandidates(
        candidates: List<NormalisedCandidate>,
        config: PortfolioConfig
    ): NormalisedCandidate {
        val highest = candidates.maxByOrNull { it.normalisedScore }!!
        if (candidates.size == 1) return highest

        val contributingIds = candidates.map { it.signal.strategyId }
        val combinedScore = candidates.map { it.normalisedScore }.average()
        val mergedEvidence = candidates.flatMap { it.signal.evidence }.distinct() +
                "Merged from strategies: ${contributingIds.joinToString(", ")}"

        val mergedSignal = highest.signal.copy(
            finalScore = combinedScore.toInt(),
            evidence = mergedEvidence
        )

        return highest.copy(
            signal = mergedSignal,
            normalisedScore = combinedScore,
            signalFingerprint = "MERGED_${highest.signalFingerprint}"
        )
    }
}
