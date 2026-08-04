package com.example.trading.strategy

import com.example.trading.analysis.Candle
import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.Timeframe
import com.example.trading.analysis.momentum.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MomentumContinuationStrategy(
    val continuationConfig: MomentumContinuationConfig = MomentumContinuationConfig()
) : TradingStrategy {

    override val id: String = "momentum_continuation_mtf_v1"
    override val displayName: String = "Multi-Timeframe Momentum Continuation"
    override val supportedRegimes: Set<MarketRegime> = setOf(
        MarketRegime.STRONG_BULL_TREND,
        MarketRegime.WEAK_BULL_TREND,
        MarketRegime.STRONG_BEAR_TREND,
        MarketRegime.WEAK_BEAR_TREND,
        MarketRegime.BREAKOUT,
        MarketRegime.HIGH_VOLATILITY
    )
    override val requiredTimeframes: Set<Timeframe> = setOf(Timeframe.M5, Timeframe.M15, Timeframe.H1)

    private val expansionDetector = MomentumExpansionDetector()
    private val consolidationDetector = MomentumConsolidationDetector()
    private val volumeAnalyzer = VolumeAccelerationAnalyzer()
    private val exhaustionDetector = MomentumExhaustionDetector()
    private val breakoutDetector = ContinuationBreakoutDetector()

    override suspend fun evaluate(
        context: StrategyContext,
        config: StrategyConfig
    ): StrategySignal {
        val h1 = context.h1Snapshot
        val m15 = context.m15Snapshot
        val m5 = context.m5Snapshot

        // 1. Timeframe Check
        if (h1 == null || m15 == null || m5 == null) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.INSUFFICIENT_DATA,
                explanation = "Missing required timeframe snapshot (H1, M15, or M5)"
            )
        }

        // 2. Regime Check
        if (!supportedRegimes.contains(context.currentMarketRegime)) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.UNSUPPORTED_REGIME,
                explanation = "Market regime ${context.currentMarketRegime} is not supported by Momentum Continuation Strategy"
            )
        }

        // 3. Spread Check
        if (context.currentSpreadPercent > continuationConfig.m5MaxSpreadPercent) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.SPREAD_TOO_HIGH,
                explanation = "Spread ${context.currentSpreadPercent}% exceeds max allowable ${continuationConfig.m5MaxSpreadPercent}%"
            )
        }

        val h1Ind = h1.indicators
        val m15Ind = m15.indicators
        val m5Ind = m5.indicators

        // 4. H1 Directional Bias & ADX Check
        val h1BullishEma = h1Ind.ema21 > h1Ind.ema50 || (h1Ind.ema21 == 0.0 && h1Ind.ema50 > h1Ind.ema200) || h1.latestCandle.close > h1Ind.ema21
        val h1BearishEma = h1Ind.ema21 < h1Ind.ema50 || (h1Ind.ema21 == 0.0 && h1Ind.ema50 < h1Ind.ema200) || h1.latestCandle.close < h1Ind.ema21

        val isBullishRegime = context.currentMarketRegime == MarketRegime.STRONG_BULL_TREND ||
                context.currentMarketRegime == MarketRegime.WEAK_BULL_TREND ||
                (context.currentMarketRegime == MarketRegime.BREAKOUT && h1.latestCandle.close > h1.candles.first().open) ||
                (context.currentMarketRegime == MarketRegime.HIGH_VOLATILITY && h1BullishEma)

        val isBearishRegime = context.currentMarketRegime == MarketRegime.STRONG_BEAR_TREND ||
                context.currentMarketRegime == MarketRegime.WEAK_BEAR_TREND ||
                (context.currentMarketRegime == MarketRegime.BREAKOUT && h1.latestCandle.close < h1.candles.first().open) ||
                (context.currentMarketRegime == MarketRegime.HIGH_VOLATILITY && h1BearishEma)

        val direction = when {
            isBullishRegime && h1BullishEma -> SignalDirection.LONG
            isBearishRegime && h1BearishEma -> SignalDirection.SHORT
            else -> SignalDirection.NEUTRAL
        }

        if (direction == SignalDirection.NEUTRAL) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.CONFLICTING_TIMEFRAMES,
                explanation = "Conflicting or neutral directional bias across H1 structure and market regime"
            )
        }

        if (h1Ind.adx < continuationConfig.h1MinAdx) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.UNSUPPORTED_REGIME,
                explanation = "H1 ADX (${h1Ind.adx}) below minimum trend threshold (${continuationConfig.h1MinAdx})"
            )
        }

        // 5. M15 Momentum Expansion Detection
        val expansionResult = expansionDetector.detectExpansion(
            candles = m15.candles,
            indicators = m15Ind,
            direction = direction,
            config = continuationConfig
        )

        if (!expansionResult.isValid) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.INVALID_INDICATORS,
                explanation = "No valid M15 momentum expansion detected (classification: ${expansionResult.classification})"
            )
        }

        // 6. Consolidation Detection
        val consolidationResult = consolidationDetector.detectConsolidation(
            candles = m15.candles,
            expansionResult = expansionResult,
            indicators = m15Ind,
            direction = direction,
            config = continuationConfig
        )

        if (!consolidationResult.isPreserved || consolidationResult.type == com.example.trading.analysis.momentum.ConsolidationType.INVALID) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.INVALID_INDICATORS,
                explanation = "M15/M5 consolidation failed structure preservation or width checks (type: ${consolidationResult.type}, retracement: ${String.format("%.1f", consolidationResult.retracementPercent)}%)"
            )
        }

        // 7. M5 Continuation Breakout Detection
        val latestM5Candle = m5.latestCandle
        val breakoutResult = breakoutDetector.detectBreakout(
            m5Candle = latestM5Candle,
            consolidationResult = consolidationResult,
            indicators = m5Ind,
            direction = direction,
            config = continuationConfig
        )

        if (!breakoutResult.isBreakout) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.INVALID_INDICATORS,
                explanation = "M5 continuation breakout unconfirmed or out of distance boundaries (close: ${latestM5Candle.close}, CLV: ${String.format("%.2f", breakoutResult.clv)})"
            )
        }

        // 8. Volume Acceleration Analysis
        val volumeResult = volumeAnalyzer.analyzeVolume(
            expansionResult = expansionResult,
            consolidationResult = consolidationResult,
            breakoutCandle = latestM5Candle,
            indicators = m5Ind
        )

        if (!volumeResult.isSequenceValid) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.INVALID_INDICATORS,
                explanation = "Volume sequence invalid for momentum continuation (sequence: ${volumeResult.sequenceType}, re-accel: ${String.format("%.2f", volumeResult.reAccelerationMultiplier)})"
            )
        }

        // 9. Momentum Exhaustion Detection
        val exhaustionResult = exhaustionDetector.detectExhaustion(
            candle = latestM5Candle,
            indicators = m15Ind,
            direction = direction,
            config = continuationConfig
        )

        if (exhaustionResult.isExhausted) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.INVALID_INDICATORS,
                explanation = "Momentum exhaustion detected (level: ${exhaustionResult.level}, reasons: ${exhaustionResult.reasons.joinToString("; ")})"
            )
        }

        // 10. Entry Quality Rating
        val entryQualityResult = breakoutDetector.evaluateEntryQuality(
            entryPrice = latestM5Candle.close,
            breakoutResult = breakoutResult,
            consolidationResult = consolidationResult,
            config = continuationConfig
        )

        if (entryQualityResult.rating == EntryQualityRating.EXTENDED || entryQualityResult.rating == EntryQualityRating.INVALID) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.INVALID_PRICE,
                explanation = "Entry quality rated ${entryQualityResult.rating}: ${entryQualityResult.explanation}"
            )
        }

        // 11. Calculate SL and TP
        val entryPrice = latestM5Candle.close
        val atr = if (m5Ind.atr > 0.0) m5Ind.atr else (entryPrice * 0.01)

        val stopLoss = calculateStopLoss(
            entryPrice = entryPrice,
            direction = direction,
            consolidationResult = consolidationResult,
            m5Candles = m5.candles,
            atr = atr,
            config = continuationConfig
        )

        val takeProfit = calculateTakeProfit(
            entryPrice = entryPrice,
            stopLoss = stopLoss,
            direction = direction,
            expansionResult = expansionResult,
            config = continuationConfig
        )

        val riskDistance = abs(entryPrice - stopLoss)
        val rewardDistance = abs(takeProfit - entryPrice)
        val riskRewardRatio = if (riskDistance > 0.0) rewardDistance / riskDistance else 0.0

        val slPercent = (riskDistance / entryPrice) * 100.0
        if (slPercent < continuationConfig.minSlPercent || slPercent > continuationConfig.maxSlPercent) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.POOR_RISK_REWARD,
                explanation = "SL distance ${String.format("%.2f", slPercent)}% outside configured bounds [${continuationConfig.minSlPercent}%, ${continuationConfig.maxSlPercent}%]"
            )
        }

        if (riskRewardRatio < continuationConfig.minRewardToRisk) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.POOR_RISK_REWARD,
                explanation = "Risk-to-reward ratio ${String.format("%.2f", riskRewardRatio)} is below minimum ${continuationConfig.minRewardToRisk}"
            )
        }

        // 12. Score Signal (0 - 100)
        val scoreDetails = calculateSignalScore(
            h1Adx = h1Ind.adx,
            expansionResult = expansionResult,
            consolidationResult = consolidationResult,
            volumeResult = volumeResult,
            breakoutResult = breakoutResult,
            exhaustionResult = exhaustionResult,
            entryQualityResult = entryQualityResult,
            riskRewardRatio = riskRewardRatio
        )

        val totalScore = scoreDetails.values.sum().coerceIn(0, 100)
        val decision = when {
            totalScore >= continuationConfig.minScoreApproved -> SignalDecision.APPROVED
            totalScore >= continuationConfig.minScorePaperTrade -> SignalDecision.PAPER_TRADE
            totalScore >= continuationConfig.minScoreWatchlist -> SignalDecision.WATCHLIST
            else -> SignalDecision.REJECT
        }

        if (decision == SignalDecision.REJECT) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.LOW_SIGNAL_SCORE,
                explanation = "Signal score $totalScore below watchlist minimum threshold (${continuationConfig.minScoreWatchlist})"
            )
        }

        // Build Evidence
        val explanations = listOf(
            "H1 Trend: ADX=${String.format("%.1f", h1Ind.adx)}, Regime=${context.currentMarketRegime}",
            "M15 Expansion: ${expansionResult.classification}, volumeMult=${String.format("%.2f", expansionResult.avgVolumeMultiplier)}",
            "Consolidation: ${consolidationResult.type}, width=${String.format("%.2f", consolidationResult.width)}",
            "M5 Breakout: close=${breakoutResult.breakoutCandleClose}, CLV=${String.format("%.2f", breakoutResult.clv)}",
            "Volume Sequence: ${volumeResult.sequenceType}, reAccel=${String.format("%.2f", volumeResult.reAccelerationMultiplier)}",
            "SL=${String.format("%.2f", stopLoss)}, TP=${String.format("%.2f", takeProfit)}, R:R=${String.format("%.2f", riskRewardRatio)}",
            "Score=$totalScore ($scoreDetails)"
        )

        val signalId = "momentum_continuation_mtf_v1_${context.symbol}_${direction}_${expansionResult.startTimestamp}_${breakoutResult.breakoutTimestamp}"

        return StrategySignal(
            signalId = signalId,
            strategyId = id,
            symbol = context.symbol,
            timeframe = Timeframe.M5,
            signalTimestamp = context.dataTimestamp,
            direction = direction,
            entryPrice = entryPrice,
            proposedStopLoss = stopLoss,
            proposedTakeProfit = takeProfit,
            riskRewardRatio = riskRewardRatio,
            rawStrategyConfidence = totalScore / 100.0,
            finalScore = totalScore,
            scoreDetails = SignalScore(
                trendAlignment = scoreDetails["trend"] ?: 0,
                marketStructure = scoreDetails["expansion"] ?: 0,
                momentum = scoreDetails["consolidation"] ?: 0,
                volumeConfirmation = scoreDetails["volume"] ?: 0,
                volatilitySuitability = scoreDetails["breakout"] ?: 0,
                entryQuality = scoreDetails["entry"] ?: 0,
                riskRewardQuality = scoreDetails["rr"] ?: 0,
                explanations = explanations
            ),
            marketRegime = context.currentMarketRegime,
            evidence = explanations,
            rejectionReasons = emptyList(),
            isDataFresh = true,
            isPaperTradeEligible = decision == SignalDecision.PAPER_TRADE || decision == SignalDecision.APPROVED,
            decision = decision
        )
    }

    private fun calculateStopLoss(
        entryPrice: Double,
        direction: SignalDirection,
        consolidationResult: ConsolidationResult,
        m5Candles: List<Candle>,
        atr: Double,
        config: MomentumContinuationConfig
    ): Double {
        val buffer = atr * 0.2
        val recentM5Low = m5Candles.takeLast(5).minOfOrNull { it.low } ?: consolidationResult.low
        val recentM5High = m5Candles.takeLast(5).maxOfOrNull { it.high } ?: consolidationResult.high

        return if (direction == SignalDirection.LONG) {
            when (config.slPolicy) {
                SlPolicy.CONSOLIDATION_BOUNDARY -> consolidationResult.low - buffer
                SlPolicy.RECENT_SWING -> recentM5Low - buffer
                SlPolicy.MOST_CONSERVATIVE -> min(consolidationResult.low, recentM5Low) - buffer
                else -> consolidationResult.low - buffer
            }
        } else {
            when (config.slPolicy) {
                SlPolicy.CONSOLIDATION_BOUNDARY -> consolidationResult.high + buffer
                SlPolicy.RECENT_SWING -> recentM5High + buffer
                SlPolicy.MOST_CONSERVATIVE -> max(consolidationResult.high, recentM5High) + buffer
                else -> consolidationResult.high + buffer
            }
        }
    }

    private fun calculateTakeProfit(
        entryPrice: Double,
        stopLoss: Double,
        direction: SignalDirection,
        expansionResult: MomentumExpansionResult,
        config: MomentumContinuationConfig
    ): Double {
        val risk = abs(entryPrice - stopLoss)
        val measuredMove = expansionResult.legHeight * config.continuationFactor

        return if (direction == SignalDirection.LONG) {
            when (config.tpPolicy) {
                TpPolicy.MEASURED_MOVE -> entryPrice + measuredMove
                TpPolicy.FIXED_RR -> entryPrice + (risk * config.minRewardToRisk)
                else -> entryPrice + max(measuredMove, risk * config.minRewardToRisk)
            }
        } else {
            when (config.tpPolicy) {
                TpPolicy.MEASURED_MOVE -> entryPrice - measuredMove
                TpPolicy.FIXED_RR -> entryPrice - (risk * config.minRewardToRisk)
                else -> entryPrice - max(measuredMove, risk * config.minRewardToRisk)
            }
        }
    }

    private fun calculateSignalScore(
        h1Adx: Double,
        expansionResult: MomentumExpansionResult,
        consolidationResult: ConsolidationResult,
        volumeResult: VolumeAnalysisResult,
        breakoutResult: BreakoutResult,
        exhaustionResult: ExhaustionResult,
        entryQualityResult: EntryQualityResult,
        riskRewardRatio: Double
    ): Map<String, Int> {
        val scores = mutableMapOf<String, Int>()

        // 1. H1 Trend (0-25)
        val trendScore = when {
            h1Adx >= 30.0 -> 25
            h1Adx >= 25.0 -> 20
            h1Adx >= 20.0 -> 15
            else -> 10
        }
        scores["trend"] = trendScore

        // 2. Momentum Expansion (0-15)
        val expansionScore = when (expansionResult.classification) {
            com.example.trading.analysis.momentum.ExpansionClassification.STRONG -> 15
            com.example.trading.analysis.momentum.ExpansionClassification.EXTREME -> 12
            com.example.trading.analysis.momentum.ExpansionClassification.MODERATE -> 10
            else -> 5
        }
        scores["expansion"] = expansionScore

        // 3. Consolidation Quality (0-15)
        val consolidationScore = when (consolidationResult.type) {
            com.example.trading.analysis.momentum.ConsolidationType.BULL_FLAG, com.example.trading.analysis.momentum.ConsolidationType.BEAR_FLAG -> 15
            com.example.trading.analysis.momentum.ConsolidationType.TIGHT_RANGE -> 13
            com.example.trading.analysis.momentum.ConsolidationType.SHALLOW_PULLBACK -> 10
            else -> 7
        }
        scores["consolidation"] = consolidationScore

        // 4. Volume Sequence (0-10)
        val volumeScore = when (volumeResult.sequenceType) {
            com.example.trading.analysis.momentum.VolumeSequenceType.ACCELERATING -> 10
            com.example.trading.analysis.momentum.VolumeSequenceType.NORMAL -> 7
            else -> 4
        }
        scores["volume"] = volumeScore

        // 5. M5 Breakout Confirmation (0-15)
        val breakoutScore = when {
            breakoutResult.clv >= 0.80 || breakoutResult.clv <= 0.20 -> 15
            breakoutResult.clv >= 0.70 || breakoutResult.clv <= 0.30 -> 12
            else -> 8
        }
        scores["breakout"] = breakoutScore

        // 6. Exhaustion Safety (0-10)
        val exhaustionScore = when (exhaustionResult.level) {
            com.example.trading.analysis.momentum.ExhaustionLevel.NONE -> 10
            com.example.trading.analysis.momentum.ExhaustionLevel.LOW -> 8
            com.example.trading.analysis.momentum.ExhaustionLevel.MODERATE -> 5
            else -> 0
        }
        scores["exhaustion"] = exhaustionScore

        // 7. Entry Quality (0-5)
        val entryScore = when (entryQualityResult.rating) {
            EntryQualityRating.OPTIMAL -> 5
            EntryQualityRating.EARLY -> 4
            EntryQualityRating.LATE -> 2
            else -> 0
        }
        scores["entry"] = entryScore

        // 8. Risk / Reward Quality (0-5)
        val rrScore = when {
            riskRewardRatio >= 2.5 -> 5
            riskRewardRatio >= 2.0 -> 4
            riskRewardRatio >= 1.5 -> 3
            else -> 1
        }
        scores["rr"] = rrScore

        return scores
    }

    private fun buildRejectedSignal(
        context: StrategyContext,
        reason: NoTradeReason,
        explanation: String
    ): StrategySignal {
        return StrategySignal(
            signalId = "momentum_continuation_mtf_v1_${context.symbol}_rejected_${System.currentTimeMillis()}",
            strategyId = id,
            symbol = context.symbol,
            timeframe = Timeframe.M5,
            signalTimestamp = context.dataTimestamp,
            direction = SignalDirection.NEUTRAL,
            entryPrice = 0.0,
            proposedStopLoss = 0.0,
            proposedTakeProfit = 0.0,
            riskRewardRatio = 0.0,
            rawStrategyConfidence = 0.0,
            finalScore = 0,
            scoreDetails = null,
            marketRegime = context.currentMarketRegime,
            evidence = listOf(explanation),
            rejectionReasons = listOf(reason),
            isDataFresh = true,
            isPaperTradeEligible = false,
            decision = SignalDecision.REJECT
        )
    }
}
