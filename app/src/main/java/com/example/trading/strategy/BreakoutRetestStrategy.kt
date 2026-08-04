package com.example.trading.strategy

import com.example.trading.analysis.Candle
import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.Timeframe
import java.util.UUID

class BreakoutRetestStrategy(
    val retestConfig: BreakoutRetestConfig = BreakoutRetestConfig()
) : TradingStrategy {

    override val id: String = "breakout_retest_mtf_v1"
    override val displayName: String = "Multi-Timeframe Breakout Retest"
    override val supportedRegimes: Set<MarketRegime> = setOf(
        MarketRegime.STRONG_BULL_TREND,
        MarketRegime.WEAK_BULL_TREND,
        MarketRegime.STRONG_BEAR_TREND,
        MarketRegime.WEAK_BEAR_TREND,
        MarketRegime.BREAKOUT
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
        if (!supportedRegimes.contains(context.currentMarketRegime) || context.currentMarketRegime == MarketRegime.RANGE) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.UNSUPPORTED_REGIME,
                explanation = "Market regime ${context.currentMarketRegime} is not supported by Breakout Retest Strategy"
            )
        }

        // 3. Spread Check
        if (context.currentSpreadPercent > retestConfig.m5MaxSpreadPercent) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.SPREAD_TOO_HIGH,
                explanation = "Spread ${context.currentSpreadPercent}% exceeds max threshold ${retestConfig.m5MaxSpreadPercent}%"
            )
        }

        val h1Ind = h1.indicators
        val m15Ind = m15.indicators
        val m5Ind = m5.indicators

        val isBullishRegime = context.currentMarketRegime == MarketRegime.STRONG_BULL_TREND || context.currentMarketRegime == MarketRegime.WEAK_BULL_TREND
        val isBearishRegime = context.currentMarketRegime == MarketRegime.STRONG_BEAR_TREND || context.currentMarketRegime == MarketRegime.WEAK_BEAR_TREND

        // 4. Step 1: H1 Trend & ADX Filter
        val h1Bullish = (h1Ind.ema50 > h1Ind.ema200 || h1.latestCandle.close >= h1Ind.ema50)
        val h1Bearish = (h1Ind.ema50 < h1Ind.ema200 || h1.latestCandle.close <= h1Ind.ema50)

        if (isBullishRegime && !h1Bullish) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.CONFLICTING_TIMEFRAMES,
                explanation = "Bullish regime conflicts with H1 bearish structure"
            )
        }
        if (isBearishRegime && !h1Bearish) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.CONFLICTING_TIMEFRAMES,
                explanation = "Bearish regime conflicts with H1 bullish structure"
            )
        }

        if (h1Ind.adx < retestConfig.h1MinAdx) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.LOW_SIGNAL_SCORE,
                explanation = "H1 ADX ${h1Ind.adx} is below minimum threshold ${retestConfig.h1MinAdx}"
            )
        }

        // Determine direction
        val direction = when {
            isBullishRegime -> SignalDirection.LONG
            isBearishRegime -> SignalDirection.SHORT
            m15.latestCandle.close >= (m15Ind.ema21.takeIf { it > 0 } ?: m15.latestCandle.close) -> SignalDirection.LONG
            else -> SignalDirection.SHORT
        }

        // 5. Step 2: M15 Breakout Detection
        val m15Candles = m15.candles
        if (m15Candles.size < 3) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.INSUFFICIENT_DATA,
                explanation = "Insufficient M15 candles for breakout detection"
            )
        }

        // Identify Breakout Level (Support or Resistance)
        val breakoutLevel = if (direction == SignalDirection.LONG) {
            if (m15Ind.resistancePrice > 0) m15Ind.resistancePrice
            else m15Candles.dropLast(2).maxOfOrNull { it.high } ?: m15Candles.first().high
        } else {
            if (m15Ind.supportPrice > 0) m15Ind.supportPrice
            else m15Candles.dropLast(2).minOfOrNull { it.low } ?: m15Candles.first().low
        }

        // Locate breakout candle in recent M15 candles
        val breakoutCandleIndex = m15Candles.indexOfFirst { c ->
            if (direction == SignalDirection.LONG) c.close > breakoutLevel
            else c.close < breakoutLevel
        }

        if (breakoutCandleIndex == -1) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.LOW_SIGNAL_SCORE,
                explanation = "No valid breakout candle detected closing beyond level $breakoutLevel"
            )
        }

        val breakoutCandle = m15Candles[breakoutCandleIndex]
        val breakoutDistPct = Math.abs(breakoutCandle.close - breakoutLevel) / breakoutLevel * 100.0

        if (breakoutDistPct < retestConfig.minBreakoutDistPercent) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.LOW_SIGNAL_SCORE,
                explanation = "Breakout distance ${String.format("%.2f", breakoutDistPct)}% is below minimum threshold ${retestConfig.minBreakoutDistPercent}% (Weak Breakout)"
            )
        }

        // Volume check on breakout candle
        val avgVolume15 = if (m15Ind.volumeSma20 > 0) m15Ind.volumeSma20 else m15Candles.map { it.volume }.average()
        val isBreakoutVolumeSufficient = breakoutCandle.volume >= (avgVolume15 * retestConfig.m15BreakoutVolumeMultiplier)

        if (!isBreakoutVolumeSufficient) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.LOW_SIGNAL_SCORE,
                explanation = "Breakout candle volume ${breakoutCandle.volume} is below required threshold ${avgVolume15 * retestConfig.m15BreakoutVolumeMultiplier}"
            )
        }

        // 6. Step 3: Retest Validation
        val retestStartIndex = if (breakoutCandleIndex + 1 < m15Candles.size) breakoutCandleIndex + 1 else breakoutCandleIndex
        val retestCandles = m15Candles.subList(retestStartIndex, m15Candles.size)
        var retestOccurred = false
        var levelHeld = true
        var retestCandleTs = m15.latestCandle.timestamp

        for (candle in retestCandles) {
            val distToLevelPct = Math.abs(candle.close - breakoutLevel) / breakoutLevel * 100.0
            val wickDistPct = if (direction == SignalDirection.LONG) {
                Math.abs(candle.low - breakoutLevel) / breakoutLevel * 100.0
            } else {
                Math.abs(candle.high - breakoutLevel) / breakoutLevel * 100.0
            }

            if (distToLevelPct <= retestConfig.retestTolerancePercent || wickDistPct <= retestConfig.retestTolerancePercent) {
                retestOccurred = true
                retestCandleTs = candle.timestamp
            }

            // Check structural failure / closing back inside range
            if (direction == SignalDirection.LONG) {
                val maxAllowedIncursion = breakoutLevel * (1.0 - retestConfig.maxRetestIncursionPercent / 100.0)
                if (candle.close < maxAllowedIncursion) {
                    levelHeld = false
                }
            } else {
                val maxAllowedIncursion = breakoutLevel * (1.0 + retestConfig.maxRetestIncursionPercent / 100.0)
                if (candle.close > maxAllowedIncursion) {
                    levelHeld = false
                }
            }
        }

        if (!levelHeld) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.LOW_SIGNAL_SCORE,
                explanation = "Failed retest: Price closed back inside previous range beyond allowed incursion (Fake Breakout)"
            )
        }

        if (!retestOccurred && retestCandles.size > 1) {
            // Check if M5 has retest
            val m5Retest = m5.candles.any { c ->
                val dist = Math.abs(c.close - breakoutLevel) / breakoutLevel * 100.0
                dist <= retestConfig.retestTolerancePercent
            }
            if (m5Retest) {
                retestOccurred = true
                retestCandleTs = m5.latestCandle.timestamp
            }
        }

        if (!retestOccurred) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.LOW_SIGNAL_SCORE,
                explanation = "No valid retest of breakout level $breakoutLevel detected"
            )
        }

        // 7. Step 4: M5 Entry Confirmation
        val entryCandle = m5.latestCandle
        val entryPrice = entryCandle.close

        val m5Confirmation = if (direction == SignalDirection.LONG) {
            val isBullish = entryCandle.isBullish || entryCandle.close > entryCandle.open
            val isAboveEma = if (m5Ind.ema9 > 0) entryPrice >= m5Ind.ema9 else true
            val volumeOk = entryCandle.volume >= (m5Ind.volumeSma20 * retestConfig.m5ConfirmationVolumeMultiplier) || entryCandle.volume > 0
            isBullish && isAboveEma && volumeOk
        } else {
            val isBearish = !entryCandle.isBullish || entryCandle.close < entryCandle.open
            val isBelowEma = if (m5Ind.ema9 > 0) entryPrice <= m5Ind.ema9 else true
            val volumeOk = entryCandle.volume >= (m5Ind.volumeSma20 * retestConfig.m5ConfirmationVolumeMultiplier) || entryCandle.volume > 0
            isBearish && isBelowEma && volumeOk
        }

        if (!m5Confirmation) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.LOW_SIGNAL_SCORE,
                explanation = "M5 entry confirmation failed (candle direction, momentum, or volume)"
            )
        }

        // 8. Stop-Loss & Take-Profit Calculation & Validation
        val atr = if (m15Ind.atr > 0) m15Ind.atr else entryPrice * 0.015
        val (slPrice, tpPrice) = calculateSlTp(
            direction = direction,
            entryPrice = entryPrice,
            breakoutLevel = breakoutLevel,
            atr = atr,
            m5Candles = m5.candles,
            m15Ind = m15Ind
        )

        // Validate SL direction strictly (Never auto-correct invalid SL!)
        val isSlDirectionValid = if (direction == SignalDirection.LONG) slPrice < entryPrice else slPrice > entryPrice
        if (!isSlDirectionValid) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.RISK_ENGINE_REJECTED,
                explanation = "Invalid SL direction: SL ($slPrice) is not ${if (direction == SignalDirection.LONG) "<" else ">"} Entry ($entryPrice)"
            )
        }

        val slDistancePct = Math.abs(entryPrice - slPrice) / entryPrice * 100.0
        if (slDistancePct < retestConfig.minSlDistancePercent || slDistancePct > retestConfig.maxSlDistancePercent) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.RISK_ENGINE_REJECTED,
                explanation = "SL distance ${String.format("%.2f", slDistancePct)}% outside allowed range [${retestConfig.minSlDistancePercent}%, ${retestConfig.maxSlDistancePercent}%]"
            )
        }

        // Validate TP direction
        val isTpDirectionValid = if (direction == SignalDirection.LONG) tpPrice > entryPrice else tpPrice < entryPrice
        if (!isTpDirectionValid) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.POOR_RISK_REWARD,
                explanation = "Invalid TP direction: TP ($tpPrice) is not ${if (direction == SignalDirection.LONG) ">" else "<"} Entry ($entryPrice)"
            )
        }

        val riskAmount = Math.abs(entryPrice - slPrice)
        val rewardAmount = Math.abs(tpPrice - entryPrice)
        val rrRatio = if (riskAmount > 0) Math.round((rewardAmount / riskAmount) * 100.0) / 100.0 else 0.0

        if (rrRatio < retestConfig.minRiskRewardRatio) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.POOR_RISK_REWARD,
                explanation = "Calculated R:R $rrRatio is below required minimum ${retestConfig.minRiskRewardRatio}"
            )
        }

        // 9. Signal Scoring (0 to 100)
        val trendAlignmentScore = if (context.currentMarketRegime == MarketRegime.STRONG_BULL_TREND || context.currentMarketRegime == MarketRegime.STRONG_BEAR_TREND || context.currentMarketRegime == MarketRegime.BREAKOUT) 20 else 14
        val breakoutQualityScore = if (breakoutDistPct >= 0.5 && isBreakoutVolumeSufficient) 15 else 10
        val retestQualityScore = if (levelHeld && retestOccurred) 15 else 8
        val volumeScore = if (entryCandle.volume >= m5Ind.volumeSma20 * retestConfig.m5ConfirmationVolumeMultiplier) 10 else 6
        val momentumScore = if (m5Confirmation) 10 else 5
        val volatilityScore = if (m15Ind.atrPercent in 0.8..3.5) 10 else 5
        val rrQualityScore = if (rrRatio >= 2.0) 20 else if (rrRatio >= 1.5) 14 else 0

        val scoreDetails = SignalScore(
            trendAlignment = trendAlignmentScore,
            marketStructure = breakoutQualityScore,
            momentum = momentumScore + retestQualityScore / 2,
            volumeConfirmation = volumeScore,
            volatilitySuitability = volatilityScore,
            entryQuality = retestQualityScore,
            riskRewardQuality = rrQualityScore,
            aiAdvisory = 0,
            explanations = listOf(
                "H1 Trend Alignment: $trendAlignmentScore/20, ADX ${h1Ind.adx}",
                "Breakout Quality (Dist ${String.format("%.2f", breakoutDistPct)}%): $breakoutQualityScore/15",
                "Retest Quality (Level $breakoutLevel Held): $retestQualityScore/15",
                "M5 Entry & Volume: $volumeScore/10, Momentum: $momentumScore/10",
                "Risk/Reward R:R $rrRatio: $rrQualityScore/20"
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

        // Duplicate prevention fingerprint: strategyId + symbol + direction + breakoutCandleTs + retestCandleTs
        val signalFingerprint = "${id}_${context.symbol}_${direction}_${breakoutCandle.timestamp}_${retestCandleTs}"

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
                "Breakout Level: $breakoutLevel, Retest Held: $levelHeld",
                "Breakout Candle Vol: ${breakoutCandle.volume}, M5 Vol: ${entryCandle.volume}",
                "SL: $slPrice, TP: $tpPrice, R:R: $rrRatio"
            ),
            rejectionReasons = rejections,
            isDataFresh = true,
            isPaperTradeEligible = (decision == SignalDecision.PAPER_TRADE || decision == SignalDecision.APPROVED),
            decision = decision
        )
    }

    private fun calculateSlTp(
        direction: SignalDirection,
        entryPrice: Double,
        breakoutLevel: Double,
        atr: Double,
        m5Candles: List<Candle>,
        m15Ind: com.example.trading.analysis.IndicatorSnapshot
    ): Pair<Double, Double> {
        return if (direction == SignalDirection.LONG) {
            val swingLow = m5Candles.takeLast(10).minOfOrNull { it.low } ?: (entryPrice - atr * 1.5)
            val levelSl = breakoutLevel - (atr * 0.5)
            val atrSl = entryPrice - (atr * retestConfig.atrSlMultiplier)

            val slPrice = when (retestConfig.stopLossPolicy) {
                StopLossPolicy.STRUCTURE_ONLY -> levelSl
                StopLossPolicy.SWING_ONLY -> swingLow
                StopLossPolicy.ATR_ONLY -> atrSl
                StopLossPolicy.HYBRID, StopLossPolicy.MOST_CONSERVATIVE -> minOf(levelSl, swingLow, atrSl)
            }

            val risk = Math.abs(entryPrice - slPrice)
            val targetTp = entryPrice + (risk * retestConfig.targetRiskReward)
            val nextResistance = if (m15Ind.resistancePrice > entryPrice) m15Ind.resistancePrice else targetTp
            val tpPrice = maxOf(targetTp, nextResistance)

            Pair(slPrice, tpPrice)
        } else {
            val swingHigh = m5Candles.takeLast(10).maxOfOrNull { it.high } ?: (entryPrice + atr * 1.5)
            val levelSl = breakoutLevel + (atr * 0.5)
            val atrSl = entryPrice + (atr * retestConfig.atrSlMultiplier)

            val slPrice = when (retestConfig.stopLossPolicy) {
                StopLossPolicy.STRUCTURE_ONLY -> levelSl
                StopLossPolicy.SWING_ONLY -> swingHigh
                StopLossPolicy.ATR_ONLY -> atrSl
                StopLossPolicy.HYBRID, StopLossPolicy.MOST_CONSERVATIVE -> maxOf(levelSl, swingHigh, atrSl)
            }

            val risk = Math.abs(slPrice - entryPrice)
            val targetTp = entryPrice - (risk * retestConfig.targetRiskReward)
            val nextSupport = if (m15Ind.supportPrice > 0 && m15Ind.supportPrice < entryPrice) m15Ind.supportPrice else targetTp
            val tpPrice = minOf(targetTp, nextSupport)

            Pair(slPrice, tpPrice)
        }
    }

    private fun buildRejectedSignal(
        context: StrategyContext,
        reason: NoTradeReason,
        explanation: String
    ): StrategySignal {
        val entryPrice = context.m5Snapshot?.latestCandle?.close ?: 0.0
        return StrategySignal(
            signalId = UUID.randomUUID().toString(),
            strategyId = id,
            symbol = context.symbol,
            timeframe = Timeframe.M5,
            signalTimestamp = context.dataTimestamp,
            direction = SignalDirection.NEUTRAL,
            entryPrice = entryPrice,
            proposedStopLoss = entryPrice,
            proposedTakeProfit = entryPrice,
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
