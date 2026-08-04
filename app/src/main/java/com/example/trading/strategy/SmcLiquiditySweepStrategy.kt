package com.example.trading.strategy

import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.Timeframe
import com.example.trading.analysis.smc.DealingRangeAnalyzer
import com.example.trading.analysis.smc.EntryZoneCandidate
import com.example.trading.analysis.smc.EntryZoneType
import com.example.trading.analysis.smc.FairValueGapDetector
import com.example.trading.analysis.smc.LiquidityPool
import com.example.trading.analysis.smc.LiquidityPoolDetector
import com.example.trading.analysis.smc.LiquiditySweep
import com.example.trading.analysis.smc.LiquiditySweepDetector
import com.example.trading.analysis.smc.LiquidityType
import com.example.trading.analysis.smc.MarketStructureAnalyzer
import com.example.trading.analysis.smc.MarketStructureType
import com.example.trading.analysis.smc.OrderBlock
import com.example.trading.analysis.smc.OrderBlockDetector
import com.example.trading.analysis.smc.OrderBlockLifecycle
import com.example.trading.analysis.smc.PremiumDiscountAnalyzer
import com.example.trading.analysis.smc.SmcEvidence
import com.example.trading.analysis.smc.StructureEvent
import com.example.trading.analysis.smc.SweepType
import com.example.trading.analysis.smc.SwingDetector

class SmcLiquiditySweepStrategy(
    val smcConfig: SmcLiquiditySweepConfig = SmcLiquiditySweepConfig()
) : TradingStrategy {

    override val id: String = "smc_liquidity_reversal_mtf_v1"
    override val displayName: String = "SMC Liquidity Sweep Reversal"
    override val supportedRegimes: Set<MarketRegime> = setOf(
        MarketRegime.STRONG_BULL_TREND,
        MarketRegime.WEAK_BULL_TREND,
        MarketRegime.STRONG_BEAR_TREND,
        MarketRegime.WEAK_BEAR_TREND,
        MarketRegime.BREAKOUT,
        MarketRegime.RANGE
    )
    override val requiredTimeframes: Set<Timeframe> = setOf(Timeframe.M5, Timeframe.M15, Timeframe.H1)

    private val swingDetector = SwingDetector()
    private val structureAnalyzer = MarketStructureAnalyzer()
    private val liquidityPoolDetector = LiquidityPoolDetector()
    private val liquiditySweepDetector = LiquiditySweepDetector()
    private val orderBlockDetector = OrderBlockDetector()
    private val fvgDetector = FairValueGapDetector()
    private val dealingRangeAnalyzer = DealingRangeAnalyzer()
    private val premiumDiscountAnalyzer = PremiumDiscountAnalyzer()

    override suspend fun evaluate(
        context: StrategyContext,
        config: StrategyConfig
    ): StrategySignal {
        val symbol = context.symbol
        val rejectionReasons = mutableListOf<NoTradeReason>()
        val evidenceExplanations = mutableListOf<String>()

        val h1Snap = context.h1Snapshot
        val m15Snap = context.m15Snapshot
        val m5Snap = context.m5Snapshot

        if (h1Snap == null || m15Snap == null || m5Snap == null ||
            h1Snap.candles.isEmpty() || m15Snap.candles.isEmpty() || m5Snap.candles.isEmpty()
        ) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.INSUFFICIENT_DATA),
                evidence = listOf("Required multi-timeframe candle data (H1, M15, M5) is missing or empty")
            )
        }

        // Check Spread
        if (context.currentSpreadPercent > smcConfig.maxSpreadPercent) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.SPREAD_TOO_HIGH),
                evidence = listOf("Current spread ${context.currentSpreadPercent}% exceeds limit ${smcConfig.maxSpreadPercent}%")
            )
        }

        val h1Candles = h1Snap.candles
        val m15Candles = m15Snap.candles
        val m5Candles = m5Snap.candles

        val h1Atr = h1Snap.indicators.atr.coerceAtLeast(0.0001)
        val m15Atr = m15Snap.indicators.atr.coerceAtLeast(0.0001)

        // 1. H1 Directional Structure & Dealing Range
        val h1Swings = swingDetector.detectSwings(h1Candles, smcConfig.swingLeftBars, smcConfig.swingRightBars, smcConfig.swingPolicy, h1Atr)
        val h1Structure = structureAnalyzer.analyzeStructure(h1Candles, h1Swings, Timeframe.H1, atr = h1Atr)
        val h1DealingRange = dealingRangeAnalyzer.calculateDealingRange(h1Candles, h1Swings, Timeframe.H1)
        val h1Bias = h1Structure.currentBias

        evidenceExplanations.add("H1 Bias: $h1Bias")

        // 2. M15 Liquidity Pools & Sweeps
        val m15Swings = swingDetector.detectSwings(m15Candles, smcConfig.swingLeftBars, smcConfig.swingRightBars, smcConfig.swingPolicy, m15Atr)
        val m15Pools = liquidityPoolDetector.detectPools(m15Candles, m15Swings, m15Atr, smcConfig.equalLevelToleranceAtrFraction)
        val m15Sweeps = liquiditySweepDetector.detectSweeps(m15Candles, m15Pools, Timeframe.M15, smcConfig.minSweepExcursionAtrFraction, smcConfig.maxDeepSweepAtrMultiple, m15Atr)

        val latestSweep = m15Sweeps.lastOrNull { it.isConfirmed && it.sweepType != SweepType.FAILED_SWEEP }

        if (latestSweep == null) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.LOW_SIGNAL_SCORE),
                evidence = listOf("No confirmed M15 liquidity sweep detected")
            )
        }

        val direction = if (latestSweep.pool.type == LiquidityType.SELL_SIDE) SignalDirection.LONG else SignalDirection.SHORT

        // Directional alignment check against H1
        if (!smcConfig.isCounterTrendAllowed) {
            if (direction == SignalDirection.LONG && h1Bias == "BEARISH") {
                return buildRejectedSignal(
                    symbol = symbol,
                    timestamp = context.dataTimestamp,
                    reasons = listOf(NoTradeReason.CONFLICTING_TIMEFRAMES),
                    evidence = listOf("Proposed LONG signal conflicts with H1 Bearish bias")
                )
            }
            if (direction == SignalDirection.SHORT && h1Bias == "BULLISH") {
                return buildRejectedSignal(
                    symbol = symbol,
                    timestamp = context.dataTimestamp,
                    reasons = listOf(NoTradeReason.CONFLICTING_TIMEFRAMES),
                    evidence = listOf("Proposed SHORT signal conflicts with H1 Bullish bias")
                )
            }
        }

        // 3. M15 CHOCH / MSS Confirmation after sweep
        val postSweepCandles = m15Candles.filter { it.timestamp >= latestSweep.sweepCandleTimestamp }
        val m15Structure = structureAnalyzer.analyzeStructure(postSweepCandles, m15Swings, Timeframe.M15, atr = m15Atr)

        val relevantStructureEvent = m15Structure.recentEvents.lastOrNull { event ->
            if (direction == SignalDirection.LONG) {
                event.type == MarketStructureType.BULLISH_CHOCH || event.type == MarketStructureType.BULLISH_BOS || event.type == MarketStructureType.BULLISH_MSS
            } else {
                event.type == MarketStructureType.BEARISH_CHOCH || event.type == MarketStructureType.BEARISH_BOS || event.type == MarketStructureType.BEARISH_MSS
            }
        }

        if (relevantStructureEvent == null) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.LOW_SIGNAL_SCORE),
                evidence = listOf("No M15 CHOCH/BOS/MSS confirmation found after sweep at ${latestSweep.sweepCandleTimestamp}")
            )
        }

        // 4. Order Block / FVG Entry Zone Detection
        val orderBlocks = orderBlockDetector.detectOrderBlocks(m15Candles, listOf(relevantStructureEvent), Timeframe.M15, smcConfig.orderBlockZonePolicy, smcConfig.obMaxCandleDistance, m15Atr)
        val fvgs = fvgDetector.detectFvgs(m15Candles, Timeframe.M15, smcConfig.minFvgGapAtrFraction, m15Atr)

        val activeOb = orderBlocks.lastOrNull { it.direction == direction && (it.state == OrderBlockLifecycle.ACTIVE || it.state == OrderBlockLifecycle.PARTIALLY_MITIGATED) }
        val activeFvg = fvgs.lastOrNull { it.direction == direction && it.state.name != "INVALIDATED" && it.state.name != "FULLY_FILLED" }

        if (activeOb == null && activeFvg == null) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.LOW_SIGNAL_SCORE),
                evidence = listOf("No active Order Block or FVG entry zone formed after structure shift")
            )
        }

        // Determine Entry Zone Candidate
        val entryCandidate = selectEntryCandidate(activeOb, activeFvg, direction, smcConfig)

        // 5. Premium / Discount Location Check
        val currentPrice = m5Candles.last().close
        val premDiscEval = premiumDiscountAnalyzer.evaluatePriceLocation(currentPrice, h1DealingRange, direction)

        if (!premDiscEval.isAcceptableForDirection) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.LOW_SIGNAL_SCORE),
                evidence = listOf("Location rejection: ${premDiscEval.explanation}")
            )
        }

        // 6. M5 Confirmation
        val m5Last = m5Candles.last()
        val m5Confirmed = if (direction == SignalDirection.LONG) m5Last.isBullish else !m5Last.isBullish
        if (!m5Confirmed) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.LOW_SIGNAL_SCORE),
                evidence = listOf("M5 trigger candle is not confirming direction $direction")
            )
        }

        // 7. Stop-Loss & Take-Profit Logic
        val entryPrice = currentPrice
        val proposedSl = calculateStopLoss(direction, latestSweep, activeOb, relevantStructureEvent, entryPrice, m15Atr, smcConfig)
        val proposedTp = calculateTakeProfit(direction, entryPrice, proposedSl, m15Pools, h1DealingRange, smcConfig)

        val slDistance = Math.abs(entryPrice - proposedSl)
        val tpDistance = Math.abs(proposedTp - entryPrice)

        if (slDistance <= 0.0 || (direction == SignalDirection.LONG && proposedSl >= entryPrice) || (direction == SignalDirection.SHORT && proposedSl <= entryPrice)) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.POOR_RISK_REWARD),
                evidence = listOf("Invalid Stop-Loss calculation: Entry=$entryPrice, SL=$proposedSl")
            )
        }

        val rrRatio = tpDistance / slDistance
        if (rrRatio < smcConfig.minRiskRewardRatio) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.POOR_RISK_REWARD),
                evidence = listOf("Risk-to-Reward ratio ${String.format("%.2f", rrRatio)} below minimum ${smcConfig.minRiskRewardRatio}")
            )
        }

        // 8. 100-Point Transparent Scoring
        var scoreH1 = if (h1Bias == if (direction == SignalDirection.LONG) "BULLISH" else "BEARISH") 10 else 5
        var scoreLocation = premDiscEval.scoreBonusOrPenalty.toInt().coerceIn(0, 10)
        var scorePool = (latestSweep.pool.strengthScore / 2.0).toInt().coerceIn(0, 10)
        var scoreSweep = if (latestSweep.sweepType == SweepType.CLOSE_AND_RECLAIM || latestSweep.sweepType == SweepType.WICK_SWEEP) 15 else 10
        var scoreStructure = if (relevantStructureEvent.type == MarketStructureType.BULLISH_MSS || relevantStructureEvent.type == MarketStructureType.BEARISH_MSS) 15 else 12
        var scoreDisplacement = if (relevantStructureEvent.isDisplacementConfirmed) 10 else 6
        var scoreOb = if (activeOb != null) 10 else 0
        var scoreFvg = if (activeFvg != null) 10 else 0
        var scoreM5 = if (m5Confirmed) 5 else 0
        var scoreRiskReward = if (rrRatio >= smcConfig.targetRiskRewardRatio) 5 else 3

        val totalScore = scoreH1 + scoreLocation + scorePool + scoreSweep + scoreStructure +
                scoreDisplacement + scoreOb + scoreFvg + scoreM5 + scoreRiskReward

        val signalScore = SignalScore(
            trendAlignment = scoreH1 * 2,           // 0-20
            marketStructure = scoreStructure,       // 0-15
            momentum = scoreDisplacement + 5,      // 0-15
            volumeConfirmation = scoreOb,           // 0-10
            volatilitySuitability = scoreLocation,  // 0-10
            entryQuality = (scoreOb + scoreFvg) / 2,// 0-10
            riskRewardQuality = scoreRiskReward * 3, // 0-15
            aiAdvisory = 0,                         // 0-5
            explanations = listOf(
                "SMC Score $totalScore/100 | Sweep: ${latestSweep.sweepType} | Zone: ${entryCandidate.type} | R:R: ${String.format("%.2f", rrRatio)}"
            )
        )

        val decision = signalScore.getDecision(
            watchlistMin = config.minScoreForWatchlist,
            paperTradeMin = config.minScoreForPaperTrade,
            approvedMin = config.minScoreForApproved
        )

        val signalFingerprint = "SMC_${symbol}_${direction}_${latestSweep.pool.id}_${latestSweep.sweepCandleTimestamp}_${entryCandidate.id}"

        return StrategySignal(
            signalId = signalFingerprint,
            strategyId = id,
            symbol = symbol,
            timeframe = Timeframe.M15,
            signalTimestamp = context.dataTimestamp,
            direction = direction,
            entryPrice = entryPrice,
            proposedStopLoss = proposedSl,
            proposedTakeProfit = proposedTp,
            riskRewardRatio = rrRatio,
            rawStrategyConfidence = totalScore / 100.0,
            finalScore = totalScore,
            scoreDetails = signalScore,
            marketRegime = context.currentMarketRegime,
            evidence = listOf(
                "SMC Strategy ID: $id",
                "Direction: $direction",
                "Sweep Event: ${latestSweep.sweepType} on ${latestSweep.pool.type} @ ${latestSweep.extremePrice}",
                "Structure Event: ${relevantStructureEvent.type} @ ${relevantStructureEvent.breakPrice}",
                "Entry Zone: ${entryCandidate.type} [${entryCandidate.bottomPrice} - ${entryCandidate.topPrice}]",
                "Dealing Range Context: ${premDiscEval.explanation}",
                "Risk/Reward: ${String.format("%.2f", rrRatio)}"
            ),
            rejectionReasons = if (decision == SignalDecision.REJECT) listOf(NoTradeReason.LOW_SIGNAL_SCORE) else emptyList(),
            isDataFresh = true,
            isPaperTradeEligible = decision == SignalDecision.PAPER_TRADE || decision == SignalDecision.APPROVED,
            decision = decision
        )
    }

    private fun selectEntryCandidate(
        ob: OrderBlock?,
        fvg: com.example.trading.analysis.smc.FairValueGap?,
        direction: SignalDirection,
        config: SmcLiquiditySweepConfig
    ): EntryZoneCandidate {
        if (ob != null && fvg != null) {
            val top = Math.max(ob.topPrice, fvg.topPrice)
            val bottom = Math.min(ob.bottomPrice, fvg.bottomPrice)
            val entry = if (direction == SignalDirection.LONG) top else bottom
            return EntryZoneCandidate(
                id = "CONFLUENCE_${ob.id}_${fvg.id}",
                type = EntryZoneType.ORDER_BLOCK_AND_FVG_CONFLUENCE,
                topPrice = top,
                bottomPrice = bottom,
                entryTargetPrice = entry,
                stopLossRefPrice = if (direction == SignalDirection.LONG) bottom else top,
                orderBlock = ob,
                fvg = fvg,
                qualityScore = 100.0
            )
        } else if (ob != null) {
            val entry = if (direction == SignalDirection.LONG) ob.topPrice else ob.bottomPrice
            return EntryZoneCandidate(
                id = ob.id,
                type = EntryZoneType.ORDER_BLOCK_ONLY,
                topPrice = ob.topPrice,
                bottomPrice = ob.bottomPrice,
                entryTargetPrice = entry,
                stopLossRefPrice = if (direction == SignalDirection.LONG) ob.bottomPrice else ob.topPrice,
                orderBlock = ob,
                fvg = null,
                qualityScore = 80.0
            )
        } else {
            val f = fvg!!
            val entry = if (direction == SignalDirection.LONG) f.topPrice else f.bottomPrice
            return EntryZoneCandidate(
                id = f.id,
                type = EntryZoneType.FVG_ONLY,
                topPrice = f.topPrice,
                bottomPrice = f.bottomPrice,
                entryTargetPrice = entry,
                stopLossRefPrice = if (direction == SignalDirection.LONG) f.bottomPrice else f.topPrice,
                orderBlock = null,
                fvg = f,
                qualityScore = 75.0
            )
        }
    }

    private fun calculateStopLoss(
        direction: SignalDirection,
        sweep: LiquiditySweep,
        ob: OrderBlock?,
        structureEvent: StructureEvent,
        entryPrice: Double,
        atr: Double,
        config: SmcLiquiditySweepConfig
    ): Double {
        val buffer = atr * config.atrSlBufferMultiple

        if (direction == SignalDirection.LONG) {
            val sweepSl = sweep.extremePrice - buffer
            val obSl = (ob?.bottomPrice ?: sweep.extremePrice) - buffer
            val structureSl = (structureEvent.brokenSwing?.price ?: sweep.extremePrice) - buffer

            return when (config.stopLossPolicy) {
                SmcStopLossPolicy.SWEEP_EXTREME -> sweepSl
                SmcStopLossPolicy.ORDER_BLOCK_BOUNDARY -> obSl
                SmcStopLossPolicy.STRUCTURE_BOUNDARY -> structureSl
                SmcStopLossPolicy.MOST_CONSERVATIVE -> Math.min(sweepSl, Math.min(obSl, structureSl))
                else -> Math.min(sweepSl, obSl)
            }
        } else {
            val sweepSl = sweep.extremePrice + buffer
            val obSl = (ob?.topPrice ?: sweep.extremePrice) + buffer
            val structureSl = (structureEvent.brokenSwing?.price ?: sweep.extremePrice) + buffer

            return when (config.stopLossPolicy) {
                SmcStopLossPolicy.SWEEP_EXTREME -> sweepSl
                SmcStopLossPolicy.ORDER_BLOCK_BOUNDARY -> obSl
                SmcStopLossPolicy.STRUCTURE_BOUNDARY -> structureSl
                SmcStopLossPolicy.MOST_CONSERVATIVE -> Math.max(sweepSl, Math.max(obSl, structureSl))
                else -> Math.max(sweepSl, obSl)
            }
        }
    }

    private fun calculateTakeProfit(
        direction: SignalDirection,
        entryPrice: Double,
        stopLoss: Double,
        pools: List<LiquidityPool>,
        dealingRange: com.example.trading.analysis.smc.DealingRange?,
        config: SmcLiquiditySweepConfig
    ): Double {
        val slDistance = Math.abs(entryPrice - stopLoss)
        val defaultTp = if (direction == SignalDirection.LONG) {
            entryPrice + (slDistance * config.targetRiskRewardRatio)
        } else {
            entryPrice - (slDistance * config.targetRiskRewardRatio)
        }

        if (direction == SignalDirection.LONG) {
            val opposingPool = pools.filter { it.type == LiquidityType.BUY_SIDE && !it.isSwept && it.priceLevel > entryPrice }
                .minByOrNull { it.priceLevel }
            val poolTp = opposingPool?.priceLevel
            val rangeTp = dealingRange?.rangeHigh

            val candidateTp = listOfNotNull(poolTp, rangeTp, defaultTp).filter { it > entryPrice }.maxOrNull()
            return candidateTp ?: defaultTp
        } else {
            val opposingPool = pools.filter { it.type == LiquidityType.SELL_SIDE && !it.isSwept && it.priceLevel < entryPrice }
                .maxByOrNull { it.priceLevel }
            val poolTp = opposingPool?.priceLevel
            val rangeTp = dealingRange?.rangeLow

            val candidateTp = listOfNotNull(poolTp, rangeTp, defaultTp).filter { it < entryPrice }.minOrNull()
            return candidateTp ?: defaultTp
        }
    }

    private fun buildRejectedSignal(
        symbol: String,
        timestamp: Long,
        reasons: List<NoTradeReason>,
        evidence: List<String>
    ): StrategySignal {
        return StrategySignal(
            signalId = "SMC_REJECT_${symbol}_$timestamp",
            strategyId = id,
            symbol = symbol,
            timeframe = Timeframe.M15,
            signalTimestamp = timestamp,
            direction = SignalDirection.NEUTRAL,
            entryPrice = 0.0,
            proposedStopLoss = 0.0,
            proposedTakeProfit = 0.0,
            riskRewardRatio = 0.0,
            rawStrategyConfidence = 0.0,
            finalScore = 0,
            marketRegime = MarketRegime.UNKNOWN,
            evidence = evidence,
            rejectionReasons = reasons,
            isDataFresh = true,
            isPaperTradeEligible = false,
            decision = SignalDecision.REJECT
        )
    }
}
