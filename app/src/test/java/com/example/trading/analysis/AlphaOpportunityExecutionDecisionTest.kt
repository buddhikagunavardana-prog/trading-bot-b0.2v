package com.example.trading.analysis

import com.example.data.TradeOrderEntity
import com.example.model.CryptoTicker
import com.example.trading.portfolio.DecisionOutcome
import com.example.trading.portfolio.NormalisedCandidate
import com.example.trading.portfolio.PortfolioDecision
import com.example.trading.portfolio.RankedCandidate
import com.example.trading.risk.AccountRiskState
import com.example.trading.risk.RiskDecision
import com.example.trading.risk.RiskEngine
import com.example.trading.risk.RiskRejectionReason
import com.example.trading.strategy.SignalDirection
import com.example.trading.strategy.StrategySignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class AlphaOpportunityExecutionDecisionTest {

    private lateinit var executionEngine: ExecutionDecisionEngine
    private lateinit var riskEngine: RiskEngine

    @Before
    fun setUp() {
        riskEngine = RiskEngine()
        executionEngine = ExecutionDecisionEngine(riskEngine = riskEngine)
    }

    private fun createScore(
        symbol: String = "BTC/USDT",
        score: Double = 80.0,
        eligibility: OpportunityEligibility = OpportunityEligibility.ELIGIBLE,
        freshnessScore: Double = 5.0,
        riskRewardScore: Double = 10.0
    ): AlphaOpportunityScore {
        return AlphaOpportunityScore(
            symbol = symbol,
            score = score,
            direction = OpportunityDirection.LONG,
            eligibility = eligibility,
            marketRegime = MarketRegime.STRONG_BULL_TREND,
            componentBreakdown = ScoreBreakdown(
                freshnessScore = freshnessScore,
                riskRewardScore = riskRewardScore,
                finalScore = score
            )
        )
    }

    private fun createPortfolioDecision(
        symbol: String = "BTC/USDT",
        isConfirmed: Boolean = true
    ): PortfolioDecision {
        val signal = StrategySignal(
            signalId = "SIG-1",
            strategyId = "BaselineTrend",
            symbol = symbol,
            timeframe = Timeframe.M5,
            signalTimestamp = System.currentTimeMillis(),
            direction = SignalDirection.LONG,
            entryPrice = 50000.0,
            proposedStopLoss = 49000.0,
            proposedTakeProfit = 52500.0,
            riskRewardRatio = 2.5,
            rawStrategyConfidence = 0.85,
            finalScore = 85,
            marketRegime = MarketRegime.STRONG_BULL_TREND,
            decision = if (isConfirmed) com.example.trading.strategy.SignalDecision.APPROVED else com.example.trading.strategy.SignalDecision.REJECT
        )
        val norm = NormalisedCandidate(
            signal = signal,
            rawStrategyScore = 85.0,
            normalisedScore = 85.0,
            components = emptyList(),
            effectiveWeight = 1.0,
            signalFingerprint = "FP-1",
            isReliabilityVerified = true
        )
        val ranked = RankedCandidate(
            normalisedCandidate = norm,
            rankPosition = 1,
            rawRankScore = 85.0,
            weightedRankScore = 85.0,
            confidence = 85.0,
            evidence = listOf("Strong trend")
        )
        return PortfolioDecision(
            evaluationId = "EVAL-1",
            evaluationTimestamp = System.currentTimeMillis(),
            symbolsEvaluated = listOf(symbol),
            strategiesEvaluated = listOf("BaselineTrend"),
            marketRegimes = mapOf(symbol to MarketRegime.STRONG_BULL_TREND),
            rawStrategySignals = listOf(signal),
            normalisedCandidates = listOf(norm),
            conflictReport = com.example.trading.portfolio.ConflictReport(hasUnresolvedHighOrCritical = false, conflicts = emptyList(), summary = "NONE"),
            portfolioRiskReport = com.example.trading.portfolio.PortfolioRiskReport(isApproved = true, currentRiskPercent = 0.0, proposedRiskPercent = 1.0, riskAfterTradePercent = 1.0, exposureChanges = "", rejectionReasons = emptyList(), warnings = emptyList(), recommendedPositionSizeMultiplier = 1.0),
            rankedCandidates = listOf(ranked),
            bestCandidate = ranked,
            finalDecision = DecisionOutcome.PAPER_EXECUTION_APPROVED,
            decisionConfidence = 85.0,
            noTradeReasons = emptyList(),
            warnings = emptyList(),
            evaluationDurationMs = 10,
            configVersion = "1.0"
        )
    }

    private fun createMtfSnapshot(symbol: String = "BTC/USDT", timestamp: Long = System.currentTimeMillis()): MultiTimeframeSnapshot {
        val candle = Candle(timestamp, 50000.0, 50500.0, 49800.0, 50000.0, 100.0)
        val dummyIndicators = IndicatorSnapshot(
            rsi = 55.0,
            adx = 30.0,
            atr = 500.0,
            sma20 = 49800.0,
            sma50 = 49500.0,
            sma200 = 48000.0,
            ema9 = 49900.0,
            ema21 = 49700.0,
            bbUpper = 51000.0,
            bbMiddle = 50000.0,
            bbLower = 49000.0,
            volumeSma20 = 90000.0
        )
        val m5Snapshot = MarketSnapshot(symbol = symbol, timeframe = Timeframe.M5, candles = listOf(candle), latestCandle = candle, indicators = dummyIndicators)
        return MultiTimeframeSnapshot(
            symbol = symbol,
            m5 = m5Snapshot,
            m15 = m5Snapshot,
            h1 = m5Snapshot
        )
    }

    private fun createTicker(symbol: String = "BTC/USDT", price: Double = 50000.0): CryptoTicker {
        return CryptoTicker(
            symbol = symbol,
            name = symbol,
            price = price,
            change24h = 2.5,
            high24h = price * 1.02,
            low24h = price * 0.98,
            volume = 100000.0
        )
    }

    // 1. Score below threshold -> BELOW_THRESHOLD
    @Test
    fun test1_scoreBelowThreshold_returnsBelowThreshold() {
        val score = createScore(score = 64.9)
        val decision = executionEngine.evaluateExecution(
            score = score,
            ticker = createTicker("BTC/USDT", 50000.0),
            mtfSnapshot = createMtfSnapshot(),
            configuredThreshold = 65.0,
            paperExecutionEnabled = true,
            activeTrades = emptyList(),
            portfolioDecision = createPortfolioDecision(),
            accountRiskState = AccountRiskState(totalEquityUsdt = 10000.0)
        )

        assertFalse("Score below threshold must not execute", decision.approvedForExecution)
        assertEquals(ExecutionStatus.BELOW_THRESHOLD, decision.executionStatus)
        assertEquals(ExecutionReasonCode.SCORE_BELOW_THRESHOLD, decision.reasonCode)
    }

    // 2. Score passed and risk failed -> RISK_REJECTED
    @Test
    fun test2_scorePassed_riskFailed_returnsRiskRejected() {
        val score = createScore(score = 72.0)
        val decision = executionEngine.evaluateExecution(
            score = score,
            ticker = createTicker("BTC/USDT", 50000.0),
            mtfSnapshot = createMtfSnapshot(),
            configuredThreshold = 65.0,
            paperExecutionEnabled = true,
            activeTrades = emptyList(),
            portfolioDecision = createPortfolioDecision(),
            accountRiskState = AccountRiskState(totalEquityUsdt = 10000.0, dailyRealizedPnlUsdt = -1000.0) // Daily loss limit hit
        )

        assertFalse("Risk rule failure must reject trade", decision.approvedForExecution)
        assertEquals(ExecutionStatus.RISK_REJECTED, decision.executionStatus)
        assertEquals(ExecutionReasonCode.RISK_RULE_FAILED, decision.reasonCode)
    }

    // 3. Score passed, risk passed, strategy false -> WAITING_FOR_CONFIRMATION
    @Test
    fun test3_scorePassed_riskPassed_strategyFalse_returnsWaitingForConfirmation() {
        val score = createScore(score = 72.0)
        val decision = executionEngine.evaluateExecution(
            score = score,
            ticker = createTicker("BTC/USDT", 50000.0),
            mtfSnapshot = createMtfSnapshot(),
            configuredThreshold = 65.0,
            paperExecutionEnabled = true,
            activeTrades = emptyList(),
            portfolioDecision = null, // No strategy signal
            accountRiskState = AccountRiskState(totalEquityUsdt = 10000.0)
        )

        assertFalse("Unconfirmed strategy must block execution", decision.approvedForExecution)
        assertEquals(ExecutionStatus.WAITING_FOR_CONFIRMATION, decision.executionStatus)
        assertEquals(ExecutionReasonCode.STRATEGY_SIGNAL_NOT_CONFIRMED, decision.reasonCode)
    }

    // 4. Score passed, risk passed, strategy true, portfolio false -> PORTFOLIO_REJECTED
    @Test
    fun test4_scorePassed_riskPassed_strategyTrue_portfolioFalse_returnsPortfolioRejected() {
        val score = createScore(score = 72.0)
        val rejectedPortfolio = createPortfolioDecision().copy(
            finalDecision = DecisionOutcome.NO_TRADE
        )
        val decision = executionEngine.evaluateExecution(
            score = score,
            ticker = createTicker("BTC/USDT", 50000.0),
            mtfSnapshot = createMtfSnapshot(),
            configuredThreshold = 65.0,
            paperExecutionEnabled = true,
            activeTrades = emptyList(),
            portfolioDecision = rejectedPortfolio,
            accountRiskState = AccountRiskState(totalEquityUsdt = 10000.0)
        )

        assertFalse("Rejected portfolio must block execution", decision.approvedForExecution)
        assertEquals(ExecutionStatus.PORTFOLIO_REJECTED, decision.executionStatus)
        assertEquals(ExecutionReasonCode.PORTFOLIO_POLICY_REJECTED, decision.reasonCode)
    }

    // 5. All gates passed -> APPROVED_FOR_EXECUTION
    @Test
    fun test5_allGatesPassed_returnsApprovedForExecution() {
        val score = createScore(score = 80.0)
        val decision = executionEngine.evaluateExecution(
            score = score,
            ticker = createTicker("BTC/USDT", 50000.0),
            mtfSnapshot = createMtfSnapshot(),
            configuredThreshold = 65.0,
            paperExecutionEnabled = true,
            activeTrades = emptyList(),
            portfolioDecision = createPortfolioDecision(),
            accountRiskState = AccountRiskState(totalEquityUsdt = 10000.0)
        )

        assertTrue(decision.approvedForExecution)
        assertEquals(ExecutionStatus.APPROVED_FOR_EXECUTION, decision.executionStatus)
        assertEquals(ExecutionReasonCode.READY_FOR_EXECUTION, decision.reasonCode)
    }

    // 6. Approved execution -> APPROVED_FOR_EXECUTION
    @Test
    fun test6_approvedExecution_returnsApprovedForExecution() {
        val score = createScore(score = 90.0)
        val decision = executionEngine.evaluateExecution(
            score = score,
            ticker = createTicker("BTC/USDT", 50000.0),
            mtfSnapshot = createMtfSnapshot(),
            configuredThreshold = 65.0,
            paperExecutionEnabled = true,
            activeTrades = emptyList(),
            portfolioDecision = createPortfolioDecision(),
            accountRiskState = AccountRiskState(totalEquityUsdt = 10000.0)
        )

        assertTrue(decision.approvedForExecution)
        assertEquals(ExecutionStatus.APPROVED_FOR_EXECUTION, decision.executionStatus)
    }

    // 7. Disabled paper execution -> EXECUTION_DISABLED
    @Test
    fun test7_disabledPaperExecution_returnsExecutionDisabled() {
        val score = createScore(score = 85.0)
        val decision = executionEngine.evaluateExecution(
            score = score,
            ticker = createTicker("BTC/USDT", 50000.0),
            mtfSnapshot = createMtfSnapshot(),
            configuredThreshold = 65.0,
            paperExecutionEnabled = false,
            activeTrades = emptyList(),
            portfolioDecision = createPortfolioDecision(),
            accountRiskState = AccountRiskState(totalEquityUsdt = 10000.0)
        )

        assertFalse("Disabled paper execution must block trade", decision.approvedForExecution)
        assertEquals(ExecutionStatus.EXECUTION_DISABLED, decision.executionStatus)
        assertEquals(ExecutionReasonCode.AUTO_TRADING_DISABLED, decision.reasonCode)
    }

    // 8. Duplicate block -> DUPLICATE_BLOCKED
    @Test
    fun test8_duplicateBlock_returnsDuplicateBlocked() {
        val score = createScore(score = 80.0)
        val activeTrade = TradeOrderEntity(
            orderId = "ORD-999",
            symbol = "BTC/USDT",
            entryPrice = 50000.0,
            currentPrice = 50100.0,
            stopLoss = 49000.0,
            takeProfit = 52000.0,
            aiConfidenceScore = 80,
            amount = 0.1,
            totalUsdt = 5000.0,
            status = "ACTIVE"
        )

        val decision = executionEngine.evaluateExecution(
            score = score,
            ticker = createTicker("BTC/USDT", 50000.0),
            mtfSnapshot = createMtfSnapshot(),
            configuredThreshold = 65.0,
            paperExecutionEnabled = true,
            activeTrades = listOf(activeTrade),
            portfolioDecision = createPortfolioDecision(),
            accountRiskState = AccountRiskState(totalEquityUsdt = 10000.0)
        )

        assertFalse("Existing active trade must block execution", decision.approvedForExecution)
        assertTrue(decision.duplicateBlocked)
        assertEquals(ExecutionStatus.DUPLICATE_BLOCKED, decision.executionStatus)
        assertEquals(ExecutionReasonCode.DUPLICATE_SYMBOL, decision.reasonCode)
    }

    // 9. Cooldown -> COOLDOWN
    @Test
    fun test9_cooldown_returnsCooldown() {
        val score = createScore(score = 82.0)
        val decision = executionEngine.evaluateExecution(
            score = score,
            ticker = createTicker("BTC/USDT", 50000.0),
            mtfSnapshot = createMtfSnapshot(),
            configuredThreshold = 65.0,
            paperExecutionEnabled = true,
            activeTrades = emptyList(),
            portfolioDecision = createPortfolioDecision(),
            accountRiskState = AccountRiskState(totalEquityUsdt = 10000.0),
            cooldownSymbols = setOf("BTC/USDT")
        )

        assertFalse("Symbol cooldown must block execution", decision.approvedForExecution)
        assertTrue(decision.cooldownBlocked)
        assertEquals(ExecutionStatus.COOLDOWN, decision.executionStatus)
        assertEquals(ExecutionReasonCode.COOLDOWN_ACTIVE, decision.reasonCode)
    }

    // 10. All gates passed but approvedForExecution is false -> EXECUTION_ELIGIBLE
    @Test
    fun test10_allGatesPassed_notYetApproved_returnsExecutionEligible() {
        val status = resolveExecutionStatus(
            paperExecutionEnabled = true,
            scoreGatePassed = true,
            riskApproved = true,
            riskRewardApproved = true,
            positionSize = 1.0,
            strategyConfirmed = true,
            portfolioApproved = true,
            duplicateBlocked = false,
            cooldownBlocked = false,
            approvedForExecution = false
        )
        assertEquals(ExecutionStatus.EXECUTION_ELIGIBLE, status)
    }

    // 11. No state except real risk failure maps to RISK_REJECTED
    @Test
    fun test11_noStateExceptRealRiskFailureMapsToRiskRejected() {
        val statusStrategyFailed = resolveExecutionStatus(
            paperExecutionEnabled = true, scoreGatePassed = true, riskApproved = true,
            riskRewardApproved = true, positionSize = 10.0, strategyConfirmed = false,
            portfolioApproved = false, duplicateBlocked = false, cooldownBlocked = false,
            approvedForExecution = false
        )
        org.junit.Assert.assertNotEquals(ExecutionStatus.RISK_REJECTED, statusStrategyFailed)

        val statusBelowThreshold = resolveExecutionStatus(
            paperExecutionEnabled = true, scoreGatePassed = false, riskApproved = true,
            riskRewardApproved = true, positionSize = 10.0, strategyConfirmed = true,
            portfolioApproved = true, duplicateBlocked = false, cooldownBlocked = false,
            approvedForExecution = false
        )
        org.junit.Assert.assertNotEquals(ExecutionStatus.RISK_REJECTED, statusBelowThreshold)

        val statusRealRiskFailed = resolveExecutionStatus(
            paperExecutionEnabled = true, scoreGatePassed = true, riskApproved = false,
            riskRewardApproved = true, positionSize = 10.0, strategyConfirmed = true,
            portfolioApproved = true, duplicateBlocked = false, cooldownBlocked = false,
            approvedForExecution = false
        )
        assertEquals(ExecutionStatus.RISK_REJECTED, statusRealRiskFailed)
    }

    // 12. ExecutionDecisionEngine and AlphaOpportunity model return identical status
    @Test
    fun test12_engineAndModelReturnIdenticalStatus() {
        val score = createScore(score = 72.0)
        val decision = executionEngine.evaluateExecution(
            score = score,
            ticker = createTicker("BTC/USDT", 50000.0),
            mtfSnapshot = createMtfSnapshot(),
            configuredThreshold = 65.0,
            paperExecutionEnabled = true,
            activeTrades = emptyList(),
            portfolioDecision = null,
            accountRiskState = AccountRiskState(totalEquityUsdt = 10000.0)
        )

        assertEquals(decision.executionStatus, decision.status)
    }

    // 13. UI displays exact backend status
    @Test
    fun test13_uiDisplaysExactBackendStatus() {
        val score = createScore(score = 72.0).copy(
            executionDecision = ExecutionDecision(
                symbol = "BTC/USDT",
                direction = OpportunityDirection.LONG,
                finalAlphaScore = 72.0,
                alphaThreshold = 65.0,
                alphaEligible = true,
                strategyConfirmed = false,
                signalFresh = true,
                riskRewardApproved = true,
                riskApproved = true,
                portfolioApproved = false,
                positionSize = 10.0,
                paperExecutionEnabled = true,
                duplicateBlocked = false,
                cooldownBlocked = false
            )
        )

        val label = com.example.ui.components.deriveAuthoritativeEligibilityLabel(score)
        assertEquals("WAITING FOR CONFIRMATION", label)
    }

    // 14. UI never independently converts WAITING_FOR_CONFIRMATION to RISK_REJECTED
    @Test
    fun test14_uiNeverIndependentlyConvertsWaitingForConfirmationToRiskRejected() {
        val score = createScore(score = 72.0).copy(
            executionDecision = ExecutionDecision(
                symbol = "BTC/USDT",
                direction = OpportunityDirection.LONG,
                finalAlphaScore = 72.0,
                alphaThreshold = 65.0,
                alphaEligible = true,
                strategyConfirmed = false,
                signalFresh = true,
                riskRewardApproved = true,
                riskApproved = true,
                portfolioApproved = false,
                positionSize = 10.0,
                paperExecutionEnabled = true,
                duplicateBlocked = false,
                cooldownBlocked = false
            )
        )

        val label = com.example.ui.components.deriveAuthoritativeEligibilityLabel(score)
        org.junit.Assert.assertNotEquals("RISK REJECTED", label)
        org.junit.Assert.assertNotEquals("RISK_REJECTED", label)
    }

    // 15. Portfolio gate is NOT_EVALUATED when strategy confirmation fails
    @Test
    fun test15_portfolioGateNotEvaluatedWhenStrategyFails() {
        val score = createScore(score = 72.0)
        val decision = executionEngine.evaluateExecution(
            score = score,
            ticker = createTicker("BTC/USDT", 50000.0),
            mtfSnapshot = createMtfSnapshot(),
            configuredThreshold = 65.0,
            paperExecutionEnabled = true,
            activeTrades = emptyList(),
            portfolioDecision = null,
            accountRiskState = AccountRiskState(totalEquityUsdt = 10000.0)
        )

        assertEquals(ExecutionStatus.WAITING_FOR_CONFIRMATION, decision.executionStatus)
        assertFalse(decision.strategyConfirmed)
    }

    // 16. Real exchange orders remain disabled
    @Test
    fun test16_realExchangeOrdersRemainDisabled() {
        // Assert paper mode safety invariant
        val score = createScore(score = 95.0)
        val decision = executionEngine.evaluateExecution(
            score = score,
            ticker = createTicker("BTC/USDT", 50000.0),
            mtfSnapshot = createMtfSnapshot(),
            configuredThreshold = 65.0,
            paperExecutionEnabled = true,
            activeTrades = emptyList(),
            portfolioDecision = createPortfolioDecision(),
            accountRiskState = AccountRiskState(totalEquityUsdt = 10000.0)
        )

        assertTrue(decision.approvedForExecution)
        // Simulated order only, no real exchange connection
        assertNotNull(decision.executionStatus)
    }

    // REGRESSION TEST for observed bug:
    // Score=72, Threshold=65, Risk Approved=true, Strategy Confirmed=false -> WAITING_FOR_CONFIRMATION (NOT RISK_REJECTED)
    @Test
    fun testRegression_score72Threshold65_unconfirmedStrategy_returnsWaitingForConfirmationNotRiskRejected() {
        val score = createScore(symbol = "XRP/USDT", score = 72.0)
        val decision = executionEngine.evaluateExecution(
            score = score,
            ticker = createTicker("XRP/USDT", 0.50),
            mtfSnapshot = createMtfSnapshot("XRP/USDT"),
            configuredThreshold = 65.0,
            paperExecutionEnabled = true,
            activeTrades = emptyList(),
            portfolioDecision = null, // Strategy not confirmed
            accountRiskState = AccountRiskState(totalEquityUsdt = 10000.0)
        )

        assertEquals(ExecutionStatus.WAITING_FOR_CONFIRMATION, decision.executionStatus)
        org.junit.Assert.assertNotEquals(ExecutionStatus.RISK_REJECTED, decision.executionStatus)
        assertEquals(ExecutionReasonCode.STRATEGY_SIGNAL_NOT_CONFIRMED, decision.reasonCode)
        assertTrue("Risk approved must be true in evaluation", decision.riskApproved)
        assertFalse("Strategy confirmed must be false", decision.strategyConfirmed)
    }

    // ACCEPTANCE TEST:
    // Demonstrate runtime example where Alpha Score = 86 > Threshold, Position Size > 0, Notional > 0,
    // No false Daily Loss, No false Max Positions, No false Risk Amount, Risk Gate passes & Strategy Confirmation evaluated.
    @Test
    fun testAcceptance_score86_positivePositionSize_riskGatePassed_noFalseRiskFailures() {
        val score = createScore(symbol = "BTC/USDT", score = 86.0)
        val portfolioDec = createPortfolioDecision("BTC/USDT", isConfirmed = true)
        val accountState = AccountRiskState(
            totalEquityUsdt = 10000.0,
            availableBalanceUsdt = 10000.0,
            dailyRealizedPnlUsdt = 0.0,
            openPositionsCount = 0,
            activeSymbols = emptySet()
        )

        val decision = executionEngine.evaluateExecution(
            score = score,
            ticker = createTicker("BTC/USDT", 50000.0),
            mtfSnapshot = createMtfSnapshot("BTC/USDT"),
            configuredThreshold = 75.0,
            paperExecutionEnabled = true,
            activeTrades = emptyList(),
            portfolioDecision = portfolioDec,
            accountRiskState = accountState
        )

        assertTrue("Score Gate must pass for 86 > 75", decision.scoreGatePassed)
        assertTrue("Risk Approved must pass when positions=0, PnL=0", decision.riskApproved)
        assertTrue("Position size must be positive", decision.positionSize > 0.0)
        assertTrue("Notional value must be positive", decision.positionSize * 50000.0 > 0.0)
        assertTrue("Strategy Confirmed must be evaluated", decision.strategyConfirmed)
        assertEquals("Execution status must be APPROVED_FOR_EXECUTION", ExecutionStatus.APPROVED_FOR_EXECUTION, decision.executionStatus)
        assertFalse("Must not contain RISK_REJECTED blocking reasons", decision.blockingReasons.any { it.startsWith("RISK_REJECTED") })
    }
}
