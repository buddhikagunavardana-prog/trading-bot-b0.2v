package com.example.trading.strategy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClosedCandleEvaluationCoordinatorTest {

    private lateinit var repo: StrategyObservationRepository

    @Before
    fun setUp() {
        repo = StrategyObservationRepository(maxCapacity = 10)
    }

    @Test
    fun testObservationRecordingAndRejectionTracking() {
        val record1 = StrategyObservationRecord(
            observationId = "OBS_01",
            sessionId = "SESS_123",
            symbol = "BTC/USDT",
            evaluationTimestamp = 1700000000000L,
            candleCloseTimestamp = 1700000000000L,
            marketRegime = "BULL_TREND",
            strategyId = "MOMENTUM_CONTINUATION",
            rawScore = 0.82,
            normalisedScore = 0.80,
            proposedDirection = "LONG",
            proposedEntry = 60000.0,
            proposedSL = 59000.0,
            proposedTP = 62000.0,
            signalDecision = "APPROVED",
            portfolioDecision = "APPROVED",
            riskDecision = "APPROVED",
            isExecutionEligible = true
        )

        val record2 = record1.copy(
            observationId = "OBS_02",
            signalDecision = "REJECTED_LOW_SCORE",
            rejectionReason = "SCORE_BELOW_THRESHOLD",
            isExecutionEligible = false
        )

        repo.recordObservation(record1)
        repo.recordObservation(record2)

        val recent = repo.getRecentObservations()
        assertEquals(2, recent.size)
        assertEquals(1, repo.getRejectionCount())
    }
}
