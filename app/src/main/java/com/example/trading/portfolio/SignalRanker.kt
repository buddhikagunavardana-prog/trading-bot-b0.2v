package com.example.trading.portfolio

class SignalRanker {

    fun rankCandidates(
        candidates: List<NormalisedCandidate>,
        config: PortfolioConfig
    ): List<RankedCandidate> {
        if (candidates.isEmpty()) return emptyList()

        val rankedList = candidates.map { candidate ->
            val normScoreComponent = (candidate.normalisedScore / 100.0) * 30.0

            val regimeComp = candidate.components.find { it.name == "Regime Compatibility" }?.normalisedValue ?: 10.0
            val rrComp = candidate.components.find { it.name == "Risk/Reward Quality" }?.normalisedValue ?: 10.0
            val freshnessComp = candidate.components.find { it.name == "Signal Freshness" }?.normalisedValue ?: 8.0
            val reliabilityComp = candidate.components.find { it.name == "Strategy Historical Reliability" }?.normalisedValue ?: 5.0

            val divComp = 10.0 // Default full diversification score for single candidate
            val execComp = 5.0  // Default execution quality score
            val conflictComp = 5.0 // Conflict free confidence score

            val totalRankScore = normScoreComponent + regimeComp + rrComp + freshnessComp + divComp + reliabilityComp + execComp + conflictComp
            val confidence = (totalRankScore.coerceIn(0.0, 100.0))

            RankedCandidate(
                normalisedCandidate = candidate,
                rankPosition = 0,
                rawRankScore = totalRankScore,
                weightedRankScore = totalRankScore,
                confidence = confidence,
                evidence = listOf(
                    "Rank score breakdown: normScore=%.1f/30, regime=%.1f/15, rr=%.1f/15, freshness=%.1f/10, reliability=%.1f/10".format(
                        normScoreComponent, regimeComp, rrComp, freshnessComp, reliabilityComp
                    )
                )
            )
        }

        // Apply deterministic sorting and tie-breaking
        val sorted = rankedList.sortedWith(
            Comparator<RankedCandidate> { a, b ->
                // Primary: Weighted Rank Score
                var cmp = b.weightedRankScore.compareTo(a.weightedRankScore)
                if (cmp != 0) return@Comparator cmp

                // 1. Higher regime compatibility
                val regimeA = a.normalisedCandidate.components.find { it.name == "Regime Compatibility" }?.normalisedValue ?: 0.0
                val regimeB = b.normalisedCandidate.components.find { it.name == "Regime Compatibility" }?.normalisedValue ?: 0.0
                cmp = regimeB.compareTo(regimeA)
                if (cmp != 0) return@Comparator cmp

                // 2. Higher risk/reward
                cmp = b.normalisedCandidate.signal.riskRewardRatio.compareTo(a.normalisedCandidate.signal.riskRewardRatio)
                if (cmp != 0) return@Comparator cmp

                // 3. Fresher signal (higher timestamp)
                cmp = b.normalisedCandidate.signal.signalTimestamp.compareTo(a.normalisedCandidate.signal.signalTimestamp)
                if (cmp != 0) return@Comparator cmp

                // 4. Lower portfolio risk impact (higher raw score)
                cmp = b.normalisedCandidate.rawStrategyScore.compareTo(a.normalisedCandidate.rawStrategyScore)
                if (cmp != 0) return@Comparator cmp

                // 5. Higher data confidence
                cmp = b.normalisedCandidate.signal.isDataFresh.compareTo(a.normalisedCandidate.signal.isDataFresh)
                if (cmp != 0) return@Comparator cmp

                // 6. Lexicographical strategy ID fallback
                a.normalisedCandidate.signal.strategyId.compareTo(b.normalisedCandidate.signal.strategyId)
            }
        )

        return sorted.mapIndexed { index, candidate ->
            candidate.copy(rankPosition = index + 1)
        }
    }
}
