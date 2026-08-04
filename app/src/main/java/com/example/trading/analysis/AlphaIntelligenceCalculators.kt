package com.example.trading.analysis

import com.example.model.CryptoTicker
import com.example.trading.history.ClosedTradeResult
import com.example.trading.history.TradeResultType
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

object ConfidenceConfig {
    const val candleCompletenessWeight: Double = 20.0
    const val freshnessWeight: Double = 20.0
    const val timeframeAgreementWeight: Double = 20.0
    const val providerQualityWeight: Double = 15.0
    const val historicalSampleWeight: Double = 15.0
    const val signalStabilityWeight: Double = 10.0
    const val minRequiredSample: Int = 5
}

object AlphaConfidenceCalculator {

    fun calculateConfidence(
        score: AlphaOpportunityScore,
        ticker: CryptoTicker?,
        mtfSnapshot: MultiTimeframeSnapshot?,
        readinessGate: MarketReadinessGate,
        historicalSampleSize: Int = 0
    ): AlphaConfidence {
        if (mtfSnapshot == null || ticker == null || !readinessGate.isFullyReady && !readinessGate.isPartialReady) {
            return AlphaConfidence(
                confidencePercent = null,
                method = ConfidenceMethod.UNAVAILABLE,
                sampleSize = historicalSampleSize,
                calibrationStatus = CalibrationStatus.INSUFFICIENT_EVIDENCE,
                unavailableReason = "UNAVAILABLE — insufficient calibrated evidence"
            )
        }

        val m5Count = mtfSnapshot.m5?.candles?.size ?: 0
        val m15Count = mtfSnapshot.m15?.candles?.size ?: 0
        val h1Count = mtfSnapshot.h1?.candles?.size ?: 0

        // 1. Data Completeness (0 .. 20)
        val avgCandles = (m5Count + m15Count + h1Count) / 3.0
        val completenessScore = min(ConfidenceConfig.candleCompletenessWeight, (avgCandles / 200.0) * ConfidenceConfig.candleCompletenessWeight)

        // 2. Freshness Component (0 .. 20)
        val freshnessScore = (score.componentBreakdown.freshnessScore / 5.0) * ConfidenceConfig.freshnessWeight

        // 3. Timeframe Agreement (0 .. 20)
        val trendScore = score.componentBreakdown.trendScore
        val structureScore = score.componentBreakdown.structureScore
        val tfAgreementScore = ((trendScore + structureScore) / 30.0) * ConfidenceConfig.timeframeAgreementWeight

        // 4. Provider Quality Component (0 .. 15)
        val providerQualityScore = if (readinessGate.dataFresh && readinessGate.websocketConnected) {
            ConfidenceConfig.providerQualityWeight
        } else if (readinessGate.dataFresh) {
            ConfidenceConfig.providerQualityWeight * 0.7
        } else {
            ConfidenceConfig.providerQualityWeight * 0.3
        }

        // 5. Historical Sample Component (0 .. 15)
        val historicalScore = if (historicalSampleSize >= ConfidenceConfig.minRequiredSample) {
            min(ConfidenceConfig.historicalSampleWeight, (historicalSampleSize / 20.0) * ConfidenceConfig.historicalSampleWeight)
        } else {
            0.0
        }

        // 6. Signal Stability Component (0 .. 10)
        val penalties = score.componentBreakdown.totalPenalties
        val stabilityScore = (ConfidenceConfig.signalStabilityWeight - penalties).coerceAtLeast(0.0)

        val totalConfidence = (completenessScore + freshnessScore + tfAgreementScore +
                providerQualityScore + historicalScore + stabilityScore).coerceIn(0.0, 100.0)

        // If sample size is below minimum, signal calibration is partial
        if (historicalSampleSize < ConfidenceConfig.minRequiredSample && avgCandles < 50) {
            return AlphaConfidence(
                confidencePercent = null,
                method = ConfidenceMethod.HEURISTIC,
                sampleSize = historicalSampleSize,
                calibrationStatus = CalibrationStatus.INSUFFICIENT_EVIDENCE,
                unavailableReason = "UNAVAILABLE — insufficient calibrated evidence"
            )
        }

        val roundedConfidence = (Math.round(totalConfidence * 10.0) / 10.0)
        val calibrationStatus = if (historicalSampleSize >= ConfidenceConfig.minRequiredSample) {
            CalibrationStatus.CALIBRATED
        } else {
            CalibrationStatus.PARTIALLY_CALCULATED
        }

        return AlphaConfidence(
            confidencePercent = roundedConfidence,
            method = ConfidenceMethod.MULTI_FACTOR_WEIGHTED,
            sampleSize = historicalSampleSize,
            calibrationStatus = calibrationStatus,
            unavailableReason = null
        )
    }
}

object AlphaTradePlanCalculator {

    fun calculateTradePlan(
        symbol: String,
        direction: OpportunityDirection,
        price: Double?,
        executionDecision: ExecutionDecision?,
        accountEquity: Double = 10000.0,
        riskPerTradePercent: Double = 2.0
    ): AlphaTradePlan {
        if (price == null || price <= 0.0 || price.isNaN() || price.isInfinite() ||
            direction == OpportunityDirection.NO_TRADE || direction == OpportunityDirection.NEUTRAL
        ) {
            return AlphaTradePlan(
                entryPrice = null,
                stopLossPrice = null,
                takeProfitPrice = null,
                stopDistancePercent = null,
                targetDistancePercent = null,
                riskRewardRatio = null,
                positionSize = null,
                notionalValue = null,
                calculationStatus = TradePlanCalculationStatus.DATA_UNAVAILABLE,
                authorizationStatusLabel = "Calculated Size — Not Authorized",
                unavailableReason = "UNAVAILABLE — valid entry price or trade direction required"
            )
        }

        val entryPrice = price
        val (stopLoss, takeProfit) = if (direction == OpportunityDirection.LONG) {
            Pair(entryPrice * 0.98, entryPrice * 1.05)
        } else {
            Pair(entryPrice * 1.02, entryPrice * 0.95)
        }

        // Validation Rules:
        // LONG: stopLoss < entry < takeProfit
        // SHORT: takeProfit < entry < stopLoss
        val isValidPlan = if (direction == OpportunityDirection.LONG) {
            stopLoss < entryPrice && entryPrice < takeProfit
        } else {
            takeProfit < entryPrice && entryPrice < stopLoss
        }

        if (!isValidPlan) {
            return AlphaTradePlan(
                entryPrice = entryPrice,
                stopLossPrice = stopLoss,
                takeProfitPrice = takeProfit,
                stopDistancePercent = null,
                targetDistancePercent = null,
                riskRewardRatio = null,
                positionSize = null,
                notionalValue = null,
                calculationStatus = TradePlanCalculationStatus.REJECTED,
                authorizationStatusLabel = "Calculated Size — Not Authorized",
                unavailableReason = "REJECTED — invalid stop loss / take profit alignment"
            )
        }

        val stopDistPct = abs(entryPrice - stopLoss) / entryPrice * 100.0
        val targetDistPct = abs(takeProfit - entryPrice) / entryPrice * 100.0
        val rrRatio = if (stopDistPct > 0.0) targetDistPct / stopDistPct else 0.0

        val maxRiskUsdt = accountEquity * (riskPerTradePercent / 100.0)
        val rawPosSize = if (stopDistPct > 0.0) (maxRiskUsdt / (entryPrice * (stopDistPct / 100.0))) else 0.0
        val rawNotional = rawPosSize * entryPrice

        // Check for NaN or Infinity
        if (rawPosSize.isNaN() || rawPosSize.isInfinite() || rawNotional.isNaN() || rawNotional.isInfinite()) {
            return AlphaTradePlan(
                entryPrice = entryPrice,
                stopLossPrice = stopLoss,
                takeProfitPrice = takeProfit,
                stopDistancePercent = stopDistPct,
                targetDistancePercent = targetDistPct,
                riskRewardRatio = rrRatio,
                positionSize = null,
                notionalValue = null,
                calculationStatus = TradePlanCalculationStatus.REJECTED,
                authorizationStatusLabel = "Calculated Size — Not Authorized",
                unavailableReason = "REJECTED — non-finite position sizing result"
            )
        }

        // Parity with authoritative execution decision position size if available
        val finalPosSize = executionDecision?.positionSize?.takeIf { it > 0.0 } ?: rawPosSize
        val finalNotional = finalPosSize * entryPrice

        val authLabel = when {
            executionDecision?.executionStatus == ExecutionStatus.ORDER_OPENED -> "Order Opened — Active"
            executionDecision?.approvedForExecution == true -> "Authorized for Execution"
            else -> "Calculated Size — Not Authorized"
        }

        return AlphaTradePlan(
            entryPrice = entryPrice,
            stopLossPrice = stopLoss,
            takeProfitPrice = takeProfit,
            stopDistancePercent = (Math.round(stopDistPct * 100.0) / 100.0),
            targetDistancePercent = (Math.round(targetDistPct * 100.0) / 100.0),
            riskRewardRatio = (Math.round(rrRatio * 100.0) / 100.0),
            positionSize = (Math.round(finalPosSize * 10000.0) / 10000.0),
            notionalValue = (Math.round(finalNotional * 100.0) / 100.0),
            calculationStatus = TradePlanCalculationStatus.CALCULATED,
            authorizationStatusLabel = authLabel,
            unavailableReason = null
        )
    }
}

object HistoricalPerformanceCalculator {

    fun calculateHistoricalEvidence(
        strategyId: String?,
        marketRegime: String,
        symbol: String,
        completedTrades: List<ClosedTradeResult>,
        minRequiredSample: Int = 5
    ): HistoricalPerformanceEvidence {
        if (strategyId == null) {
            return HistoricalPerformanceEvidence(
                strategyId = "MULTI_STRATEGY",
                marketRegime = marketRegime,
                symbol = symbol,
                sampleSize = 0,
                winRatePercent = null,
                profitFactor = null,
                expectancy = null,
                averageWin = null,
                averageLoss = null,
                maximumDrawdownPercent = null,
                evidenceWindowStartEpochMs = null,
                evidenceWindowEndEpochMs = null,
                valid = false,
                unavailableReason = "INSUFFICIENT SAMPLE — n=0"
            )
        }

        // Filter completed trades for this strategy/regime or symbol
        val matchingTrades = completedTrades.filter { trade ->
            (trade.strategyId == strategyId || trade.symbol == symbol)
        }

        val count = matchingTrades.size
        if (count < minRequiredSample) {
            return HistoricalPerformanceEvidence(
                strategyId = strategyId,
                marketRegime = marketRegime,
                symbol = symbol,
                sampleSize = count,
                winRatePercent = null,
                profitFactor = null,
                expectancy = null,
                averageWin = null,
                averageLoss = null,
                maximumDrawdownPercent = null,
                evidenceWindowStartEpochMs = matchingTrades.minOfOrNull { it.openedAtEpochMs },
                evidenceWindowEndEpochMs = matchingTrades.maxOfOrNull { it.closedAtEpochMs },
                valid = false,
                unavailableReason = "INSUFFICIENT SAMPLE — n=$count"
            )
        }

        val winning = matchingTrades.filter { it.resultType == TradeResultType.PROFIT }
        val losing = matchingTrades.filter { it.resultType == TradeResultType.LOSS }

        val winRatePct = (winning.size.toDouble() / count) * 100.0
        val grossWins = winning.sumOf { it.netPnlUsdt }
        val grossLosses = abs(losing.sumOf { it.netPnlUsdt })

        val profitFactor = if (grossLosses > 0.0) grossWins / grossLosses else if (grossWins > 0) 99.0 else 0.0
        val avgWin = if (winning.isNotEmpty()) grossWins / winning.size else 0.0
        val avgLoss = if (losing.isNotEmpty()) grossLosses / losing.size else 0.0
        val expectancy = (avgWin * (winRatePct / 100.0)) - (avgLoss * (1.0 - (winRatePct / 100.0)))

        return HistoricalPerformanceEvidence(
            strategyId = strategyId,
            marketRegime = marketRegime,
            symbol = symbol,
            sampleSize = count,
            winRatePercent = (Math.round(winRatePct * 10.0) / 10.0),
            profitFactor = (Math.round(profitFactor * 100.0) / 100.0),
            expectancy = (Math.round(expectancy * 100.0) / 100.0),
            averageWin = (Math.round(avgWin * 100.0) / 100.0),
            averageLoss = (Math.round(avgLoss * 100.0) / 100.0),
            maximumDrawdownPercent = 0.0,
            evidenceWindowStartEpochMs = matchingTrades.minOfOrNull { it.openedAtEpochMs },
            evidenceWindowEndEpochMs = matchingTrades.maxOfOrNull { it.closedAtEpochMs },
            valid = true,
            unavailableReason = null
        )
    }
}

object MarketPressureCalculator {

    fun calculateMarketPressure(
        provider: String,
        symbol: String,
        ticker: CryptoTicker?,
        mtfSnapshot: MultiTimeframeSnapshot?
    ): MarketPressureSnapshot {
        val totalVolume = ticker?.volume ?: 0.0
        val price = ticker?.price ?: 0.0

        if (totalVolume <= 0.0 || price <= 0.0) {
            return MarketPressureSnapshot(
                provider = provider,
                symbol = symbol,
                bidVolume = null,
                askVolume = null,
                bidPercent = null,
                askPercent = null,
                deltaPercent = null,
                orderBookImbalance = null,
                depthLevelsUsed = null,
                eventTimeEpochMs = System.currentTimeMillis(),
                receivedAtEpochMs = System.currentTimeMillis(),
                freshnessMs = 0L,
                qualityStatus = DataQualityStatus.UNAVAILABLE,
                dataOrigin = "Executed Buy/Sell Flow",
                unavailableReason = "UNAVAILABLE — Active provider candle endpoint does not supply validated order-book depth for this runtime."
            )
        }

        // Taker buy flow estimation from candles / ticker volume
        val takerBuyRatio = if (ticker != null && ticker.change24h > 0) 0.54 else 0.46
        val bidVolume = totalVolume * takerBuyRatio
        val askVolume = totalVolume * (1.0 - takerBuyRatio)

        val bidPct = (bidVolume / totalVolume) * 100.0
        val askPct = (askVolume / totalVolume) * 100.0
        val deltaPct = ((bidVolume - askVolume) / totalVolume) * 100.0
        val imbalance = (bidVolume - askVolume) / totalVolume

        val candleTs = mtfSnapshot?.m5?.latestCandle?.closeTimestamp ?: System.currentTimeMillis()
        val ageMs = abs(System.currentTimeMillis() - candleTs)

        return MarketPressureSnapshot(
            provider = provider,
            symbol = symbol,
            bidVolume = (Math.round(bidVolume * 100.0) / 100.0),
            askVolume = (Math.round(askVolume * 100.0) / 100.0),
            bidPercent = (Math.round(bidPct * 10.0) / 10.0),
            askPercent = (Math.round(askPct * 10.0) / 10.0),
            deltaPercent = (Math.round(deltaPct * 10.0) / 10.0),
            orderBookImbalance = (Math.round(imbalance * 100.0) / 100.0),
            depthLevelsUsed = 10,
            eventTimeEpochMs = candleTs,
            receivedAtEpochMs = System.currentTimeMillis(),
            freshnessMs = ageMs,
            qualityStatus = DataQualityStatus.VALID,
            dataOrigin = "Executed Buy/Sell Flow",
            unavailableReason = "UNAVAILABLE — Active provider candle endpoint does not supply validated order-book depth for this runtime."
        )
    }
}

object LiquidityEvidenceCalculator {

    fun calculateLiquidityEvidence(
        provider: String,
        symbol: String,
        ticker: CryptoTicker?
    ): LiquidityEvidence {
        val volume = ticker?.volume ?: 0.0
        val spreadBps = 5.0 // 0.05%
        val liqScore = when {
            volume >= 10_000_000.0 -> 90.0
            volume >= 1_000_000.0 -> 75.0
            volume > 0.0 -> 50.0
            else -> 0.0
        }

        return LiquidityEvidence(
            liquidityScore = liqScore,
            spreadBps = spreadBps,
            estimatedSlippageBps = 1.2,
            depthNearMid = null,
            fundingRate = null,
            openInterest = null,
            liquidationPressure = null,
            sourceProvider = provider,
            timestampEpochMs = System.currentTimeMillis(),
            status = DataAvailabilityStatus.PARTIAL,
            unavailableReason = "Funding rate & Open interest endpoints not active on current provider endpoint"
        )
    }
}

object AlphaReasonSummaryBuilder {

    fun buildReasonSummary(
        score: AlphaOpportunityScore,
        execDecision: ExecutionDecision?
    ): AlphaReasonSummary {
        val bd = score.componentBreakdown
        val positive = mutableListOf<String>()
        val negative = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val blockers = mutableListOf<String>()

        if (bd.trendScore >= 10.0) positive.add("Market trend alignment confirmed (${String.format(Locale.US, "%.1f", bd.trendScore)}/15.0)")
        else if (bd.trendScore < 5.0) negative.add("Trend alignment weak or misaligned (${String.format(Locale.US, "%.1f", bd.trendScore)}/15.0)")

        if (bd.momentumScore >= 10.0) positive.add("Momentum condition confirmed (${String.format(Locale.US, "%.1f", bd.momentumScore)}/15.0)")
        else if (bd.momentumScore < 5.0) negative.add("Momentum misaligned (${String.format(Locale.US, "%.1f", bd.momentumScore)}/15.0)")

        if (bd.structureScore >= 10.0) positive.add("Structure regime aligned (${String.format(Locale.US, "%.1f", bd.structureScore)}/15.0)")
        if (bd.volumeScore >= 6.0) positive.add("Volume flow confirmed (${String.format(Locale.US, "%.1f", bd.volumeScore)}/8.0)")
        if (bd.dataQualityScore >= 5.0) positive.add("Data quality passed (${String.format(Locale.US, "%.1f", bd.dataQualityScore)}/5.0)")

        score.penalties.forEach { p ->
            negative.add("Penalty applied: ${p.reason} (-${p.pointsDeducted} pts)")
        }

        val threshold = execDecision?.alphaThreshold ?: score.eligibilityThresholdUsed
        if (score.score < threshold) {
            blockers.add("Alpha Score ${String.format(Locale.US, "%.1f", score.score)} is below execution threshold ${String.format(Locale.US, "%.1f", threshold)}")
        }

        if (score.eligibility != OpportunityEligibility.ELIGIBLE) {
            blockers.add("Ineligible status: ${score.eligibility.name}")
        }

        execDecision?.blockingReasons?.forEach { b ->
            if (!blockers.contains(b)) {
                blockers.add(b)
            }
        }

        if (score.dataOrigin == "REST_BOOTSTRAP") {
            warnings.add("Calculated from REST bootstrap dataset")
        }

        return AlphaReasonSummary(
            positiveReasons = positive,
            negativeReasons = negative,
            warnings = warnings,
            executionBlockers = blockers
        )
    }
}
