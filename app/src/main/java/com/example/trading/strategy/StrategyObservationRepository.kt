package com.example.trading.strategy

import java.util.concurrent.ConcurrentHashMap

data class StrategyObservationRecord(
    val observationId: String,
    val sessionId: String,
    val symbol: String,
    val evaluationTimestamp: Long,
    val candleCloseTimestamp: Long,
    val marketRegime: String,
    val strategyId: String,
    val rawScore: Double,
    val normalisedScore: Double,
    val proposedDirection: String,
    val proposedEntry: Double,
    val proposedSL: Double,
    val proposedTP: Double,
    val signalDecision: String,
    val rejectionReason: String = "NONE",
    val portfolioDecision: String,
    val riskDecision: String,
    val isExecutionEligible: Boolean
)

/**
 * Strategy Observation Repository for Phase 11.
 * Maintains bounded records of every strategy evaluation event for statistical validation & auditing.
 */
class StrategyObservationRepository(
    private val maxCapacity: Int = 200
) {

    private val observations = ArrayDeque<StrategyObservationRecord>()

    @Synchronized
    fun recordObservation(record: StrategyObservationRecord) {
        observations.addLast(record)
        while (observations.size > maxCapacity) {
            observations.removeFirst()
        }
    }

    @Synchronized
    fun getRecentObservations(limit: Int = 50): List<StrategyObservationRecord> {
        return observations.takeLast(limit)
    }

    @Synchronized
    fun getRejectionCount(): Int {
        return observations.count { !it.isExecutionEligible }
    }
}
