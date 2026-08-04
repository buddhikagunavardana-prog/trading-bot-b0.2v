package com.example.trading.strategy

import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.Timeframe
import com.example.trading.analysis.Candle
import java.util.UUID

class TrendPullbackStrategy(
    val pullbackConfig: TrendPullbackConfig = TrendPullbackConfig()
) : TradingStrategy {

    override val id: String = "trend_pullback_mtf_v1"
    override val displayName: String = "Multi-Timeframe Trend Pullback"
    override val supportedRegimes: Set<MarketRegime> = setOf(
        MarketRegime.STRONG_BULL_TREND,
        MarketRegime.WEAK_BULL_TREND,
        MarketRegime.STRONG_BEAR_TREND,
        MarketRegime.WEAK_BEAR_TREND
    )
    override val requiredTimeframes: Set<Timeframe> = setOf(Timeframe.M5, Timeframe.M15, Timeframe.H1)

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
                explanation = "Market regime ${context.currentMarketRegime} is not supported by Trend Pullback Strategy"
            )
        }

        // 3. Spread Check
        if (context.currentSpreadPercent > pullbackConfig.m5MaxSpreadPercent) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.SPREAD_TOO_HIGH,
                explanation = "Spread ${context.currentSpreadPercent}% exceeds max threshold ${pullbackConfig.m5MaxSpreadPercent}%"
            )
        }

        val h1Ind = h1.indicators
        val m15Ind = m15.indicators
        val m5Ind = m5.indicators

        val isBullishRegime = context.currentMarketRegime == MarketRegime.STRONG_BULL_TREND || context.currentMarketRegime == MarketRegime.WEAK_BULL_TREND
        val isBearishRegime = context.currentMarketRegime == MarketRegime.STRONG_BEAR_TREND || context.currentMarketRegime == MarketRegime.WEAK_BEAR_TREND

        // 4. H1 Trend Alignment & ADX Check
        val h1BullishTrend = (h1Ind.ema50 > h1Ind.ema200 || h1Ind.sma50 > h1Ind.sma200 || h1.latestCandle.close > h1Ind.ema50) && h1.latestCandle.close >= h1Ind.ema50
        val h1BearishTrend = (h1Ind.ema50 < h1Ind.ema200 || h1Ind.sma50 < h1Ind.sma200 || h1.latestCandle.close < h1Ind.ema50) && h1.latestCandle.close <= h1Ind.ema50

        if (isBullishRegime && !h1BullishTrend) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.CONFLICTING_TIMEFRAMES,
                explanation = "Bullish regime conflicts with H1 bearish structure"
            )
        }
        if (isBearishRegime && !h1BearishTrend) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.CONFLICTING_TIMEFRAMES,
                explanation = "Bearish regime conflicts with H1 bullish structure"
            )
        }

        if (h1Ind.adx < pullbackConfig.h1MinAdx) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.LOW_SIGNAL_SCORE,
                explanation = "H1 ADX ${h1Ind.adx} is below minimum threshold ${pullbackConfig.h1MinAdx}"
            )
        }

        val direction = if (isBullishRegime) SignalDirection.LONG else SignalDirection.SHORT
        val entryCandle = m5.latestCandle
        val entryPrice = entryCandle.close

        // 5. Evaluate Pullback Quality (M15)
        val pullbackQuality = evaluatePullbackQuality(
            direction = direction,
            m15Candles = m15.candles,
            latestClose = entryPrice,
            ema20 = if (m15Ind.ema21 > 0) m15Ind.ema21 else m15Ind.sma20,
            ema50 = if (m15Ind.ema50 > 0) m15Ind.ema50 else m15Ind.sma50,
            rsi = m15Ind.rsi,
            atr = if (m15Ind.atr > 0) m15Ind.atr else entryPrice * 0.01
        )

        if (pullbackQuality == PullbackQuality.INVALID) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.LOW_SIGNAL_SCORE,
                explanation = "M15 Pullback quality is INVALID (excessive depth or structure breach)"
            )
        }

        // 6. Evaluate M5 Entry Confirmation
        val m5ConfirmationPassed = if (direction == SignalDirection.LONG) {
            val isBullishCandle = entryCandle.isBullish
            val isPrevBreak = if (m5.candles.size >= 2) entryCandle.close > m5.candles[m5.candles.size - 2].high else true
            val isVolumeConfirmed = entryCandle.volume >= (m5Ind.volumeSma20 * pullbackConfig.m5VolumeMultiplier) || entryCandle.volume > 0
            isBullishCandle && (isPrevBreak || entryPrice > m5Ind.ema9) && isVolumeConfirmed
        } else {
            val isBearishCandle = !entryCandle.isBullish
            val isPrevBreak = if (m5.candles.size >= 2) entryCandle.close < m5.candles[m5.candles.size - 2].low else true
            val isVolumeConfirmed = entryCandle.volume >= (m5Ind.volumeSma20 * pullbackConfig.m5VolumeMultiplier) || entryCandle.volume > 0
            isBearishCandle && (isPrevBreak || entryPrice < m5Ind.ema9) && isVolumeConfirmed
        }

        if (!m5ConfirmationPassed) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.LOW_SIGNAL_SCORE,
                explanation = "M5 entry confirmation failed (candle direction, volume, or momentum break)"
            )
        }

        // 7. Stop-Loss & Take-Profit Calculation
        val atr = if (m15Ind.atr > 0) m15Ind.atr else entryPrice * 0.015
        val (slPrice, tpPrice) = calculateSlTp(
            direction = direction,
            entryPrice = entryPrice,
            atr = atr,
            m5Candles = m5.candles,
            m15Ind = m15Ind
        )

        val slDistancePct = Math.abs(entryPrice - slPrice) / entryPrice * 100.0
        if (slDistancePct < pullbackConfig.minSlDistancePercent || slDistancePct > pullbackConfig.maxSlDistancePercent) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.RISK_ENGINE_REJECTED,
                explanation = "SL distance ${String.format("%.2f", slDistancePct)}% outside allowed range [${pullbackConfig.minSlDistancePercent}%, ${pullbackConfig.maxSlDistancePercent}%]"
            )
        }

        val riskAmount = Math.abs(entryPrice - slPrice)
        val rewardAmount = Math.abs(tpPrice - entryPrice)
        val rrRatio = if (riskAmount > 0) Math.round((rewardAmount / riskAmount) * 100.0) / 100.0 else 0.0

        if (rrRatio < pullbackConfig.minRiskRewardRatio) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.POOR_RISK_REWARD,
                explanation = "Calculated R:R $rrRatio is below required minimum ${pullbackConfig.minRiskRewardRatio}"
            )
        }

        // 8. Transparent Signal Scoring Breakdown (0 to 100)
        val h1TrendAlignmentScore = if (context.currentMarketRegime == MarketRegime.STRONG_BULL_TREND || context.currentMarketRegime == MarketRegime.STRONG_BEAR_TREND) 20 else 14
        val h1TrendStrengthScore = if (h1Ind.adx >= pullbackConfig.h1StrongAdx) 10 else if (h1Ind.adx >= pullbackConfig.h1MinAdx) 7 else 0
        val pullbackQualityScore = when (pullbackQuality) {
            PullbackQuality.HEALTHY -> 20
            PullbackQuality.SHALLOW -> 15
            PullbackQuality.DEEP -> 8
            PullbackQuality.INVALID -> 0
        }
        val isRsiIdeal = if (direction == SignalDirection.LONG) (m15Ind.rsi in pullbackConfig.m15RsiLongMin..pullbackConfig.m15RsiLongMax)
        else (m15Ind.rsi in pullbackConfig.m15RsiShortMin..pullbackConfig.m15RsiShortMax)
        val m15MomentumScore = if (isRsiIdeal) 10 else 5

        val m5EntryScore = if (m5ConfirmationPassed) 15 else 5
        val volumeScore = if (entryCandle.volume >= m5Ind.volumeSma20 * pullbackConfig.m5VolumeMultiplier) 10 else 6
        val volatilityScore = if (m15Ind.atrPercent in 0.8..3.5) 5 else 2
        val rrQualityScore = if (rrRatio >= 2.0) 10 else if (rrRatio >= 1.5) 7 else 0

        val scoreDetails = SignalScore(
            trendAlignment = h1TrendAlignmentScore,
            marketStructure = h1TrendStrengthScore,
            momentum = m15MomentumScore,
            volumeConfirmation = volumeScore,
            volatilitySuitability = volatilityScore,
            entryQuality = m5EntryScore + pullbackQualityScore / 2,
            riskRewardQuality = rrQualityScore,
            aiAdvisory = 0,
            explanations = listOf(
                "H1 Alignment: $h1TrendAlignmentScore/20, Strength (ADX ${h1Ind.adx}): $h1TrendStrengthScore/10",
                "M15 Pullback Quality ($pullbackQuality): $pullbackQualityScore/20, RSI ${m15Ind.rsi}: $m15MomentumScore/10",
                "M5 Confirmation: $m5EntryScore/15, Volume: $volumeScore/10, Volatility: $volatilityScore/5",
                "Risk/Reward R:R $rrRatio: $rrQualityScore/10"
            )
        )

        val totalScore = scoreDetails.totalScore
        val decision = scoreDetails.getDecision(
            watchlistMin = config.minScoreForWatchlist,
            paperTradeMin = config.minScoreForPaperTrade,
            approvedMin = config.minScoreForApproved
        )

        val rejections = mutableListOf<NoTradeReason>()
        if (decision == SignalDecision.REJECT) {
            rejections.add(NoTradeReason.LOW_SIGNAL_SCORE)
        }

        // Deterministic fingerprint for duplicate prevention
        val signalFingerprint = "${id}_${context.symbol}_${direction}_${m5.latestCandle.timestamp}_${m15.latestCandle.timestamp}"

        return StrategySignal(
            signalId = UUID.randomUUID().toString(),
            strategyId = id,
            symbol = context.symbol,
            timeframe = Timeframe.M5,
            signalTimestamp = context.dataTimestamp,
            direction = direction,
            entryPrice = entryPrice,
            proposedStopLoss = slPrice,
            proposedTakeProfit = tpPrice,
            riskRewardRatio = rrRatio,
            rawStrategyConfidence = totalScore / 100.0,
            finalScore = totalScore,
            scoreDetails = scoreDetails,
            marketRegime = context.currentMarketRegime,
            evidence = listOf(
                "Fingerprint: $signalFingerprint",
                "H1 ADX: ${h1Ind.adx}, M15 RSI: ${m15Ind.rsi}",
                "Pullback Quality: $pullbackQuality",
                "SL: $slPrice, TP: $tpPrice, R:R: $rrRatio"
            ),
            rejectionReasons = rejections,
            isDataFresh = true,
            isPaperTradeEligible = (decision == SignalDecision.PAPER_TRADE || decision == SignalDecision.APPROVED),
            decision = decision
        )
    }

    private fun evaluatePullbackQuality(
        direction: SignalDirection,
        m15Candles: List<Candle>,
        latestClose: Double,
        ema20: Double,
        ema50: Double,
        rsi: Double,
        atr: Double
    ): PullbackQuality {
        if (m15Candles.isEmpty()) return PullbackQuality.HEALTHY

        if (direction == SignalDirection.LONG) {
            val maxHigh = m15Candles.maxOf { it.high }
            val depthPct = if (maxHigh > 0) ((maxHigh - latestClose) / maxHigh) * 100.0 else 0.0

            if (depthPct > pullbackConfig.maxPullbackDepthPercent || (ema50 > 0 && latestClose < ema50)) {
                return PullbackQuality.INVALID
            }

            val distToEma20 = Math.abs(latestClose - ema20)
            return when {
                distToEma20 <= (atr * 0.5) && rsi in 42.0..55.0 -> PullbackQuality.HEALTHY
                depthPct < 1.0 -> PullbackQuality.SHALLOW
                depthPct >= 2.2 -> PullbackQuality.DEEP
                else -> PullbackQuality.HEALTHY
            }
        } else {
            val minLow = m15Candles.minOf { it.low }
            val depthPct = if (minLow > 0) ((latestClose - minLow) / minLow) * 100.0 else 0.0

            if (depthPct > pullbackConfig.maxPullbackDepthPercent || (ema50 > 0 && latestClose > ema50)) {
                return PullbackQuality.INVALID
            }

            val distToEma20 = Math.abs(latestClose - ema20)
            return when {
                distToEma20 <= (atr * 0.5) && rsi in 45.0..58.0 -> PullbackQuality.HEALTHY
                depthPct < 1.0 -> PullbackQuality.SHALLOW
                depthPct >= 2.2 -> PullbackQuality.DEEP
                else -> PullbackQuality.HEALTHY
            }
        }
    }

    private fun calculateSlTp(
        direction: SignalDirection,
        entryPrice: Double,
        atr: Double,
        m5Candles: List<Candle>,
        m15Ind: com.example.trading.analysis.IndicatorSnapshot
    ): Pair<Double, Double> {
        val last5 = m5Candles.takeLast(5)
        val swingLow = if (last5.isNotEmpty()) last5.minOf { it.low } else entryPrice - (atr * 1.5)
        val swingHigh = if (last5.isNotEmpty()) last5.maxOf { it.high } else entryPrice + (atr * 1.5)

        val atrSl = if (direction == SignalDirection.LONG) entryPrice - (atr * pullbackConfig.atrSlMultiplier) else entryPrice + (atr * pullbackConfig.atrSlMultiplier)

        val slPrice = if (direction == SignalDirection.LONG) {
            when (pullbackConfig.stopLossPolicy) {
                StopLossPolicy.ATR_ONLY -> atrSl
                StopLossPolicy.SWING_ONLY -> swingLow.coerceAtMost(entryPrice * 0.998)
                StopLossPolicy.STRUCTURE_ONLY -> if (m15Ind.supportPrice > 0) m15Ind.supportPrice else atrSl
                StopLossPolicy.MOST_CONSERVATIVE -> listOf(swingLow, atrSl, m15Ind.supportPrice).filter { it in 0.0..<entryPrice }.minOrNull() ?: atrSl
                StopLossPolicy.HYBRID -> if (swingLow < entryPrice) Math.max(swingLow, atrSl) else atrSl
            }
        } else {
            when (pullbackConfig.stopLossPolicy) {
                StopLossPolicy.ATR_ONLY -> atrSl
                StopLossPolicy.SWING_ONLY -> swingHigh.coerceAtLeast(entryPrice * 1.002)
                StopLossPolicy.STRUCTURE_ONLY -> if (m15Ind.resistancePrice > 0) m15Ind.resistancePrice else atrSl
                StopLossPolicy.MOST_CONSERVATIVE -> listOf(swingHigh, atrSl, m15Ind.resistancePrice).filter { it > entryPrice }.maxOrNull() ?: atrSl
                StopLossPolicy.HYBRID -> if (swingHigh > entryPrice) Math.min(swingHigh, atrSl) else atrSl
            }
        }

        val slDist = Math.abs(entryPrice - slPrice)
        val tpPrice = if (direction == SignalDirection.LONG) {
            entryPrice + (slDist * pullbackConfig.targetRiskReward)
        } else {
            entryPrice - (slDist * pullbackConfig.targetRiskReward)
        }

        val roundedSl = Math.round(slPrice * 10000.0) / 10000.0
        val roundedTp = Math.round(tpPrice * 10000.0) / 10000.0
        return Pair(roundedSl, roundedTp)
    }

    private fun buildRejectedSignal(
        context: StrategyContext,
        reason: NoTradeReason,
        explanation: String
    ): StrategySignal {
        return StrategySignal(
            signalId = UUID.randomUUID().toString(),
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
            scoreDetails = SignalScore(explanations = listOf(explanation)),
            marketRegime = context.currentMarketRegime,
            evidence = emptyList(),
            rejectionReasons = listOf(reason),
            isDataFresh = true,
            isPaperTradeEligible = false,
            decision = SignalDecision.REJECT
        )
    }
}
