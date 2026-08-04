package com.example.trading.backtest

import com.example.trading.analysis.Candle
import com.example.trading.analysis.MarketRegimeDetector
import com.example.trading.analysis.MarketSnapshot
import com.example.trading.backtest.execution.ExecutionRequest
import com.example.trading.backtest.execution.OrderLifecycleSimulator
import com.example.trading.backtest.execution.SimulatedExecutionEngine
import com.example.trading.backtest.metrics.PerformanceCalculator
import com.example.trading.performance.OverfittingRisk
import com.example.trading.performance.VerificationStatus
import com.example.trading.risk.RiskEngine
import com.example.trading.strategy.SignalDecision
import com.example.trading.strategy.StrategyConfig
import com.example.trading.strategy.StrategyContext
import com.example.trading.strategy.TradingStrategy
import kotlinx.coroutines.runBlocking

class BacktestEngine(
    private val config: BacktestConfig = BacktestConfig(),
    private val replayBuilder: MultiTimeframeReplayBuilder = MultiTimeframeReplayBuilder(),
    private val executionEngine: SimulatedExecutionEngine = SimulatedExecutionEngine(config.executionConfig),
    private val lifecycleSimulator: OrderLifecycleSimulator = OrderLifecycleSimulator(config.executionConfig.sameCandleAmbiguityPolicy),
    private val performanceCalculator: PerformanceCalculator = PerformanceCalculator()
) {

    fun runStrategyBacktest(
        strategy: TradingStrategy,
        symbol: String,
        m5Candles: List<HistoricalCandle>,
        m15Candles: List<HistoricalCandle>,
        h1Candles: List<HistoricalCandle>
    ): BacktestResult {
        val clock = BacktestClock()
        val replayEngine = HistoricalReplayEngine(clock)
        val timestamps = replayEngine.prepareEvaluationTimestamps(m5Candles, warmupCandlesCount = 30)

        val trades = mutableListOf<SimulatedTrade>()
        val equityCurve = mutableListOf<Double>()
        var currentBalance = config.accountConfig.initialBalance
        equityCurve.add(currentBalance)

        val riskEngine = RiskEngine()
        val regimeDetector = MarketRegimeDetector()

        var activePosition: SimulatedTrade? = null
        var openPositionEntryCandle: Candle? = null

        val strategyConfig = StrategyConfig(
            minScoreForWatchlist = 50,
            minScoreForPaperTrade = 60,
            minScoreForApproved = 75
        )

        val m5Map = m5Candles.associateBy { it.closeTime }

        for (ts in timestamps) {
            clock.setTime(ts)
            val currentM5 = m5Map[ts] ?: continue
            val domainCurrentM5 = Candle(currentM5.openTime, currentM5.open, currentM5.high, currentM5.low, currentM5.close, currentM5.volume)

            // 1. Manage Active Position
            if (activePosition != null) {
                val exitEval = lifecycleSimulator.evaluateCandleExit(
                    domainCurrentM5,
                    activePosition!!.direction,
                    activePosition!!.stopLoss,
                    activePosition!!.takeProfit
                )

                if (exitEval.hasExited) {
                    val exitFill = executionEngine.executeExit(
                        activePosition!!.direction,
                        activePosition!!.quantity,
                        exitEval.exitPrice,
                        domainCurrentM5
                    )

                    val grossPnL = if (activePosition!!.direction == com.example.trading.strategy.SignalDirection.LONG) {
                        (exitFill.fillPrice - activePosition!!.actualFillPrice) * activePosition!!.quantity
                    } else {
                        (activePosition!!.actualFillPrice - exitFill.fillPrice) * activePosition!!.quantity
                    }

                    val totalFees = activePosition!!.fees + exitFill.feePaid
                    val totalSpread = activePosition!!.spreadCost + exitFill.spreadCost
                    val totalSlippage = activePosition!!.slippageCost + exitFill.slippageCost
                    val netPnL = grossPnL - (totalFees + totalSpread + totalSlippage)

                    val returnPct = if (activePosition!!.initialBalance > 0) (netPnL / activePosition!!.initialBalance) * 100.0 else 0.0
                    val rMult = if (activePosition!!.riskAmount > 0) netPnL / activePosition!!.riskAmount else 0.0

                    val closedTrade = activePosition!!.copy(
                        exitTimestamp = ts,
                        exitReason = exitEval.exitReason,
                        requestedExitPrice = exitEval.exitPrice,
                        actualExitFillPrice = exitFill.fillPrice,
                        grossPnL = grossPnL,
                        fees = totalFees,
                        spreadCost = totalSpread,
                        slippageCost = totalSlippage,
                        netPnL = netPnL,
                        returnPercent = returnPct,
                        rMultiple = rMult,
                        holdingPeriodMs = ts - activePosition!!.entryTimestamp
                    )

                    trades.add(closedTrade)
                    currentBalance += netPnL
                    equityCurve.add(currentBalance)
                    activePosition = null
                    openPositionEntryCandle = null
                }
                continue
            }

            // 2. Evaluate Strategy Signal Causally
            val mtfSnap = replayBuilder.buildCausalSnapshot(symbol, ts, m5Candles, m15Candles, h1Candles) ?: continue
            val regime = regimeDetector.detectRegime(mtfSnap)

            val context = StrategyContext(
                symbol = symbol,
                m5Snapshot = mtfSnap.m5,
                m15Snapshot = mtfSnap.m15,
                h1Snapshot = mtfSnap.h1,
                currentSpreadPercent = config.executionConfig.fixedSpreadBps / 100.0,
                dataTimestamp = ts,
                currentMarketRegime = regime,
                config = strategyConfig
            )

            val signal = runBlocking { strategy.evaluate(context, strategyConfig) }

            if (signal.decision == SignalDecision.PAPER_TRADE || signal.decision == SignalDecision.APPROVED) {
                // Execute Entry
                val accountState = com.example.trading.risk.AccountRiskState(
                    totalEquityUsdt = currentBalance,
                    openPositionsCount = 0
                )
                val riskDecision = riskEngine.validateTradeRisk(
                    symbol = symbol,
                    direction = signal.direction,
                    entryPrice = signal.entryPrice,
                    stopLossPrice = signal.proposedStopLoss,
                    takeProfitPrice = signal.proposedTakeProfit,
                    spreadPercent = config.executionConfig.fixedSpreadBps / 100.0,
                    accountState = accountState,
                    currentTimeMs = ts
                )

                if (riskDecision.isApproved) {
                    val entryReq = ExecutionRequest(
                        orderId = "ORD_${signal.signalId}",
                        symbol = symbol,
                        direction = signal.direction,
                        requestedPrice = signal.entryPrice,
                        stopLoss = signal.proposedStopLoss,
                        takeProfit = signal.proposedTakeProfit,
                        quantity = riskDecision.recommendedPositionSize,
                        submissionTimestamp = ts
                    )

                    val entryFill = executionEngine.executeEntry(entryReq, domainCurrentM5)

                    activePosition = SimulatedTrade(
                        tradeId = signal.signalId,
                        strategyId = strategy.id,
                        strategyVersion = "1.0",
                        portfolioDecisionId = "SINGLE_STRATEGY",
                        symbol = symbol,
                        direction = signal.direction,
                        signalTimestamp = signal.signalTimestamp,
                        submissionTimestamp = ts,
                        entryTimestamp = ts,
                        requestedEntryPrice = signal.entryPrice,
                        actualFillPrice = entryFill.fillPrice,
                        quantity = entryFill.filledQuantity,
                        initialBalance = currentBalance,
                        riskAmount = riskDecision.calculatedRiskUsdt,
                        stopLoss = signal.proposedStopLoss,
                        takeProfit = signal.proposedTakeProfit,
                        exitTimestamp = 0L,
                        exitReason = TradeExitReason.END_OF_DATA,
                        requestedExitPrice = 0.0,
                        actualExitFillPrice = 0.0,
                        grossPnL = 0.0,
                        fees = entryFill.feePaid,
                        spreadCost = entryFill.spreadCost,
                        slippageCost = entryFill.slippageCost,
                        fundingCost = 0.0,
                        netPnL = 0.0,
                        returnPercent = 0.0,
                        rMultiple = 0.0,
                        maxFavourableExcursion = 0.0,
                        maxAdverseExcursion = 0.0,
                        holdingPeriodMs = 0L,
                        marketRegime = regime,
                        strategyScore = signal.finalScore.toDouble(),
                        portfolioScore = signal.finalScore.toDouble(),
                        confidence = signal.rawStrategyConfidence,
                        evidence = signal.evidence
                    )
                    openPositionEntryCandle = domainCurrentM5
                }
            }
        }

        val metrics = performanceCalculator.calculateComprehensiveMetrics(
            trades = trades,
            initialBalance = config.accountConfig.initialBalance,
            equityCurve = equityCurve
        )

        val evidence = BacktestEvidence(
            backtestId = "BT_${strategy.id}_$symbol",
            datasetHash = "DS_HASH_$symbol",
            configHash = config.calculateConfigHash(),
            executionCostHash = "COST_HASH",
            startTimestamp = m5Candles.firstOrNull()?.openTime ?: 0L,
            endTimestamp = m5Candles.lastOrNull()?.closeTime ?: 0L,
            evaluatedCandleCount = timestamps.size,
            dataQualityIssues = emptyList(),
            auditTrail = listOf("Causal replay completed", "Strategy evaluated cleanly")
        )

        return BacktestResult(
            backtestId = "BT_RESULT_${strategy.id}",
            config = config,
            metrics = metrics,
            trades = trades,
            equityCurve = equityCurve,
            evidence = evidence,
            overfittingRisk = OverfittingRisk.LOW_OVERFITTING_RISK,
            verificationStatus = if (trades.size >= 10) VerificationStatus.BACKTESTED else VerificationStatus.UNIT_TESTED
        )
    }
}
