package com.example.trading.strategy

import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.Timeframe
import java.util.UUID

class BaselineTrendFollowStrategy : TradingStrategy {
    override val id: String = "BASELINE_TREND_FOLLOW_M5_M15"
    override val displayName: String = "Baseline M5/M15 Trend Follow"
    override val supportedRegimes: Set<MarketRegime> = setOf(
        MarketRegime.STRONG_BULL_TREND,
        MarketRegime.WEAK_BULL_TREND,
        MarketRegime.STRONG_BEAR_TREND,
        MarketRegime.WEAK_BEAR_TREND,
        MarketRegime.BREAKOUT
    )
    override val requiredTimeframes: Set<Timeframe> = setOf(Timeframe.M5, Timeframe.M15)

    override suspend fun evaluate(
        context: StrategyContext,
        config: StrategyConfig
    ): StrategySignal {
        val m15 = context.m15Snapshot
        val m5 = context.m5Snapshot

        if (m15 == null || m5 == null) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.INSUFFICIENT_DATA,
                explanation = "Missing required M5 or M15 timeframe data"
            )
        }

        val primaryInd = m15.indicators
        val entryCandle = m5.latestCandle
        val entryPrice = entryCandle.close

        val isBullishRegime = context.currentMarketRegime == MarketRegime.STRONG_BULL_TREND || context.currentMarketRegime == MarketRegime.WEAK_BULL_TREND
        val isBearishRegime = context.currentMarketRegime == MarketRegime.STRONG_BEAR_TREND || context.currentMarketRegime == MarketRegime.WEAK_BEAR_TREND

        val direction = when {
            isBullishRegime -> SignalDirection.LONG
            isBearishRegime -> SignalDirection.SHORT
            else -> SignalDirection.NEUTRAL
        }

        if (direction == SignalDirection.NEUTRAL) {
            return buildRejectedSignal(
                context = context,
                reason = NoTradeReason.UNSUPPORTED_REGIME,
                explanation = "Market regime ${context.currentMarketRegime} did not produce a directional bias"
            )
        }

        // Calculate SL / TP
        val atr = if (primaryInd.atr > 0) primaryInd.atr else entryPrice * 0.015
        val (slPrice, tpPrice) = if (direction == SignalDirection.LONG) {
            val sl = Math.round((entryPrice - (atr * 1.5)) * 10000.0) / 10000.0
            val tp = Math.round((entryPrice + (atr * 3.0)) * 10000.0) / 10000.0
            Pair(sl, tp)
        } else {
            val sl = Math.round((entryPrice + (atr * 1.5)) * 10000.0) / 10000.0
            val tp = Math.round((entryPrice - (atr * 3.0)) * 10000.0) / 10000.0
            Pair(sl, tp)
        }

        val rr = if (Math.abs(entryPrice - slPrice) > 0) Math.abs(tpPrice - entryPrice) / Math.abs(entryPrice - slPrice) else 0.0

        // Score component breakdown
        val trendScore = if (context.currentMarketRegime == MarketRegime.STRONG_BULL_TREND || context.currentMarketRegime == MarketRegime.STRONG_BEAR_TREND) 20 else 14
        val structureScore = if (primaryInd.supportPrice > 0 && primaryInd.resistancePrice > 0) 12 else 8
        val momentumScore = if (primaryInd.rsi in 45.0..65.0) 13 else 9
        val volumeScore = if (m5.latestCandle.volume > primaryInd.volumeSma20) 9 else 6
        val volatilityScore = if (primaryInd.atrPercent in 0.8..3.5) 8 else 5
        val entryQualityScore = 8
        val rrQualityScore = if (rr >= 2.0) 15 else if (rr >= 1.5) 11 else 5
        val aiScore = 0 // AI score defaults to 0, cannot override deterministic checks

        val scoreDetails = SignalScore(
            trendAlignment = trendScore,
            marketStructure = structureScore,
            momentum = momentumScore,
            volumeConfirmation = volumeScore,
            volatilitySuitability = volatilityScore,
            entryQuality = entryQualityScore,
            riskRewardQuality = rrQualityScore,
            aiAdvisory = aiScore,
            explanations = listOf(
                "Regime ${context.currentMarketRegime} -> Trend score $trendScore/20",
                "RSI ${primaryInd.rsi} -> Momentum score $momentumScore/15",
                "Risk/Reward R:R ratio ${String.format("%.2f", rr)} -> R/R score $rrQualityScore/15"
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

        return StrategySignal(
            signalId = UUID.randomUUID().toString(),
            strategyId = id,
            symbol = context.symbol,
            timeframe = Timeframe.M15,
            signalTimestamp = context.dataTimestamp,
            direction = direction,
            entryPrice = entryPrice,
            proposedStopLoss = slPrice,
            proposedTakeProfit = tpPrice,
            riskRewardRatio = Math.round(rr * 100.0) / 100.0,
            rawStrategyConfidence = totalScore / 100.0,
            finalScore = totalScore,
            scoreDetails = scoreDetails,
            marketRegime = context.currentMarketRegime,
            evidence = listOf(
                "M15 EMA50=${primaryInd.ema50}, EMA200=${primaryInd.ema200}",
                "ADX=${primaryInd.adx}, ATR=${primaryInd.atr}",
                "Calculated R:R = ${String.format("%.2f", rr)}"
            ),
            rejectionReasons = rejections,
            isDataFresh = true,
            isPaperTradeEligible = (decision == SignalDecision.PAPER_TRADE || decision == SignalDecision.APPROVED),
            decision = decision
        )
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
            timeframe = Timeframe.M15,
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
