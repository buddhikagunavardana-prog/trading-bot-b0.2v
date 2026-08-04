package com.example.trading.portfolio

import com.example.trading.analysis.Candle
import com.example.trading.analysis.IndicatorSnapshot
import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.MultiTimeframeSnapshot
import com.example.trading.analysis.Timeframe
import com.example.trading.strategy.NoTradeReason
import com.example.trading.strategy.SignalDecision
import com.example.trading.strategy.SignalDirection
import com.example.trading.strategy.StrategySignal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StrategyPortfolioManagerTest {

    private lateinit var normaliser: SignalNormaliser
    private lateinit var conflictResolver: StrategyConflictResolver
    private lateinit var ranker: SignalRanker
    private lateinit var selector: CandidateSelector
    private lateinit var portfolioManager: StrategyPortfolioManager

    private val baseTs = 1700000000000L

    @Before
    fun setUp() {
        normaliser = SignalNormaliser()
        conflictResolver = StrategyConflictResolver()
        ranker = SignalRanker()
        selector = CandidateSelector()
        portfolioManager = StrategyPortfolioManager()
    }

    // --- 1. Signal Normalisation Tests ---

    @Test
    fun testSignalNormalisation_BoundedBetween0And100() = runBlocking {
        val config = PortfolioConfig()
        val signal = createMockSignal("trend_pullback", "BTCUSDT", SignalDirection.LONG, score = 85, rr = 2.5)

        val candidate = normaliser.normaliseSignal(signal, MarketRegime.STRONG_BULL_TREND, config, baseTs)

        assertTrue("Score bounded >= 0", candidate.normalisedScore >= 0.0)
        assertTrue("Score bounded <= 100", candidate.normalisedScore <= 100.0)
    }

    @Test
    fun testSignalNormalisation_StaleSignalPenalty() = runBlocking {
        val config = PortfolioConfig(maxAcceptableSignalAgeMs = 300_000L) // 5 mins
        val freshSignal = createMockSignal("trend_pullback", "BTCUSDT", SignalDirection.LONG, score = 80, rr = 2.0, ts = baseTs)
        val oldSignal = createMockSignal("trend_pullback", "BTCUSDT", SignalDirection.LONG, score = 80, rr = 2.0, ts = baseTs - 240_000L) // 4 mins old

        val freshCandidate = normaliser.normaliseSignal(freshSignal, MarketRegime.STRONG_BULL_TREND, config, baseTs)
        val oldCandidate = normaliser.normaliseSignal(oldSignal, MarketRegime.STRONG_BULL_TREND, config, baseTs)

        assertTrue("Fresh signal score should be higher than old signal score", freshCandidate.normalisedScore > oldCandidate.normalisedScore)
    }

    @Test
    fun testSignalNormalisation_RegimeIncompatibility() = runBlocking {
        val config = PortfolioConfig()
        val signal = createMockSignal("trend_pullback", "BTCUSDT", SignalDirection.LONG, score = 80, rr = 2.0)

        // trend_pullback is incompatible with RANGE
        val candidate = normaliser.normaliseSignal(signal, MarketRegime.RANGE, config, baseTs)

        val regimeComp = candidate.components.find { it.name == "Regime Compatibility" }
        assertEquals(0.0, regimeComp?.normalisedValue ?: -1.0, 0.01)
    }

    // --- 2. Conflict Resolution Tests ---

    @Test
    fun testConflictResolver_OpposingDirections_ResolvesWhenScoreGapIsLarge() {
        val config = PortfolioConfig(minScoreGapBetweenTopCandidates = 5.0)
        val signalLong = createMockSignal("trend_pullback", "BTCUSDT", SignalDirection.LONG, score = 90)
        val signalShort = createMockSignal("range_reversal", "BTCUSDT", SignalDirection.SHORT, score = 70)

        val candLong = NormalisedCandidate(signalLong, 90.0, 85.0, emptyList(), 1.0, "fp1", false)
        val candShort = NormalisedCandidate(signalShort, 70.0, 65.0, emptyList(), 1.0, "fp2", false)

        val result = conflictResolver.resolveConflicts(listOf(candLong, candShort), config)

        assertFalse("Conflict should be resolved cleanly", result.conflictReport.hasUnresolvedHighOrCritical)
        assertEquals(1, result.resolvedCandidates.size)
        assertEquals(SignalDirection.LONG, result.resolvedCandidates.first().signal.direction)
    }

    @Test
    fun testConflictResolver_OpposingDirections_RejectsWhenScoresAreClose() {
        val config = PortfolioConfig(minScoreGapBetweenTopCandidates = 10.0)
        val signalLong = createMockSignal("trend_pullback", "BTCUSDT", SignalDirection.LONG, score = 80)
        val signalShort = createMockSignal("range_reversal", "BTCUSDT", SignalDirection.SHORT, score = 78)

        val candLong = NormalisedCandidate(signalLong, 80.0, 75.0, emptyList(), 1.0, "fp1", false)
        val candShort = NormalisedCandidate(signalShort, 78.0, 73.0, emptyList(), 1.0, "fp2", false)

        val result = conflictResolver.resolveConflicts(listOf(candLong, candShort), config)

        assertTrue("Unresolved critical conflict expected", result.conflictReport.hasUnresolvedHighOrCritical)
        assertEquals(0, result.resolvedCandidates.size)
    }

    // --- 3. Candidate Ranking & Tie-Breaking Tests ---

    @Test
    fun testSignalRanker_CorrectOrderingAndTieBreaking() {
        val config = PortfolioConfig()
        val signalA = createMockSignal("trend_pullback", "BTCUSDT", SignalDirection.LONG, score = 80, rr = 2.0)
        val signalB = createMockSignal("momentum_continuation", "BTCUSDT", SignalDirection.LONG, score = 80, rr = 2.5)

        val candA = NormalisedCandidate(signalA, 80.0, 75.0, emptyList(), 1.0, "fp1", false)
        val candB = NormalisedCandidate(signalB, 80.0, 75.0, emptyList(), 1.0, "fp2", false)

        val ranked = ranker.rankCandidates(listOf(candA, candB), config)

        assertEquals(2, ranked.size)
        assertEquals(1, ranked[0].rankPosition)
        assertEquals(2, ranked[1].rankPosition)
        // Tie break rule #2: higher R:R (candB has rr=2.5 vs 2.0) wins
        assertEquals("momentum_continuation", ranked[0].normalisedCandidate.signal.strategyId)
    }

    // --- 4. Portfolio Risk Tests ---

    @Test
    fun testPortfolioRiskManager_GlobalKillSwitchActive_Rejects() = runBlocking {
        val config = PortfolioConfig(isGlobalKillSwitchActive = true)
        val signal = createMockSignal("trend_pullback", "BTCUSDT", SignalDirection.LONG, score = 85)
        val candidate = NormalisedCandidate(signal, 85.0, 80.0, emptyList(), 1.0, "fp1", false)

        val riskManager = PortfolioRiskManager()
        val report = riskManager.validatePortfolioRisk(candidate, config, baseTs)

        assertFalse("Risk report should not be approved when kill switch active", report.isApproved)
        assertTrue(report.rejectionReasons.contains(NoTradeReason.RISK_ENGINE_REJECTED))
    }

    // --- 5. Candidate Selector Tests ---

    @Test
    fun testCandidateSelector_MinScoreThreshold_RejectsLowScore() {
        val config = PortfolioConfig(minNormalisedScore = 70.0)
        val signal = createMockSignal("trend_pullback", "BTCUSDT", SignalDirection.LONG, score = 60)
        val candidate = NormalisedCandidate(signal, 60.0, 62.0, emptyList(), 1.0, "fp1", false)

        val ranked = listOf(RankedCandidate(candidate, 1, 62.0, 62.0, 62.0, emptyList()))
        val conflictReport = ConflictReport(false, emptyList(), "None")
        val riskReport = PortfolioRiskReport(true, 0.0, 1.0, 1.0, "None", emptyList(), emptyList(), 1.0)

        val result = selector.selectBestCandidate(ranked, conflictReport, riskReport, config)

        assertEquals(DecisionOutcome.NO_TRADE, result.decisionOutcome)
        assertNull(result.bestCandidate)
        assertTrue(result.noTradeReasons.contains(NoTradeReason.LOW_SIGNAL_SCORE))
    }

    @Test
    fun testCandidateSelector_ValidCandidate_Approved() {
        val config = PortfolioConfig(minNormalisedScore = 65.0)
        val signal = createMockSignal("trend_pullback", "BTCUSDT", SignalDirection.LONG, score = 85, isPaperEligible = true)
        val candidate = NormalisedCandidate(signal, 85.0, 80.0, emptyList(), 1.0, "fp1", false)

        val ranked = listOf(RankedCandidate(candidate, 1, 80.0, 80.0, 80.0, emptyList()))
        val conflictReport = ConflictReport(false, emptyList(), "None")
        val riskReport = PortfolioRiskReport(true, 0.0, 1.0, 1.0, "None", emptyList(), emptyList(), 1.0)

        val result = selector.selectBestCandidate(ranked, conflictReport, riskReport, config)

        assertEquals(DecisionOutcome.PAPER_TRADE_CANDIDATE, result.decisionOutcome)
        assertNotNull(result.bestCandidate)
        assertEquals("trend_pullback", result.bestCandidate?.normalisedCandidate?.signal?.strategyId)
    }

    // --- 6. Boundary Score Tests ---

    @Test
    fun testDecisionBoundaryScores() = runBlocking {
        val scoresToTest = listOf(49, 50, 64, 65, 79, 80, 100)
        val config = PortfolioConfig(minNormalisedScore = 65.0)

        for (rawScore in scoresToTest) {
            val signal = createMockSignal("trend_pullback", "BTCUSDT", SignalDirection.LONG, score = rawScore, isPaperEligible = true)
            val candidate = normaliser.normaliseSignal(signal, MarketRegime.STRONG_BULL_TREND, config, baseTs)

            val ranked = listOf(RankedCandidate(candidate, 1, candidate.normalisedScore, candidate.normalisedScore, candidate.normalisedScore, emptyList()))
            val conflictReport = ConflictReport(false, emptyList(), "None")
            val riskReport = PortfolioRiskReport(true, 0.0, 1.0, 1.0, "None", emptyList(), emptyList(), 1.0)

            val selection = selector.selectBestCandidate(ranked, conflictReport, riskReport, config)

            if (candidate.normalisedScore >= 65.0) {
                assertEquals("Score $rawScore (norm ${candidate.normalisedScore}) >= 65 should be PAPER_TRADE_CANDIDATE", DecisionOutcome.PAPER_TRADE_CANDIDATE, selection.decisionOutcome)
            } else {
                assertEquals("Score $rawScore (norm ${candidate.normalisedScore}) < 65 should be NO_TRADE", DecisionOutcome.NO_TRADE, selection.decisionOutcome)
            }
        }
    }

    // Helper functions
    private fun createMockSignal(
        strategyId: String,
        symbol: String,
        direction: SignalDirection,
        score: Int,
        rr: Double = 2.0,
        ts: Long = baseTs,
        isPaperEligible: Boolean = true
    ): StrategySignal {
        return StrategySignal(
            signalId = "SIG_${strategyId}_$ts",
            strategyId = strategyId,
            symbol = symbol,
            timeframe = Timeframe.M15,
            signalTimestamp = ts,
            direction = direction,
            entryPrice = 100.0,
            proposedStopLoss = 98.0,
            proposedTakeProfit = 104.0,
            riskRewardRatio = rr,
            rawStrategyConfidence = score / 100.0,
            finalScore = score,
            marketRegime = MarketRegime.STRONG_BULL_TREND,
            evidence = listOf("Test evidence"),
            rejectionReasons = emptyList(),
            isDataFresh = true,
            isPaperTradeEligible = isPaperEligible,
            decision = if (score >= 60) SignalDecision.PAPER_TRADE else SignalDecision.REJECT
        )
    }
}
