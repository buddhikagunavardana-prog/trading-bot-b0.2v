package com.example.trading.strategy

import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.Timeframe
import com.example.trading.analysis.range.BoundaryRejectionDetector
import com.example.trading.analysis.range.FalseBreakoutDetector
import com.example.trading.analysis.range.FalseBreakoutType
import com.example.trading.analysis.range.MeanReversionAnalyzer
import com.example.trading.analysis.range.RangeDetector
import com.example.trading.analysis.range.RangeQualityAnalyzer
import com.example.trading.analysis.range.RangeTargetPolicy
import com.example.trading.analysis.range.RejectionQuality

class RangeReversalStrategy(
    val rangeConfig: RangeReversalConfig = RangeReversalConfig()
) : TradingStrategy {

    override val id: String = "range_reversal_mtf_v1"
    override val displayName: String = "Multi-Timeframe Range Reversal"
    override val supportedRegimes: Set<MarketRegime> = setOf(
        MarketRegime.RANGE,
        MarketRegime.LOW_VOLATILITY,
        MarketRegime.WEAK_BULL_TREND,
        MarketRegime.WEAK_BEAR_TREND
    )
    override val requiredTimeframes: Set<Timeframe> = setOf(Timeframe.M5, Timeframe.M15, Timeframe.H1)

    private val rangeDetector = RangeDetector()
    private val rejectionDetector = BoundaryRejectionDetector()
    private val falseBreakoutDetector = FalseBreakoutDetector()
    private val meanReversionAnalyzer = MeanReversionAnalyzer()

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
        if (context.currentSpreadPercent > rangeConfig.maxSpreadPercent) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.SPREAD_TOO_HIGH),
                evidence = listOf("Current spread ${context.currentSpreadPercent}% exceeds limit ${rangeConfig.maxSpreadPercent}%")
            )
        }

        val h1Indicators = h1Snap.indicators
        val m15Candles = m15Snap.candles
        val m5Candles = m5Snap.candles
        val m15Atr = m15Snap.indicators.atr.coerceAtLeast(0.0001)

        // 1. H1 Trend Exclusion Check
        val h1Adx = h1Indicators.adx
        if (h1Adx > rangeConfig.maxH1Adx) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.UNSUPPORTED_REGIME),
                evidence = listOf("H1 ADX ($h1Adx) exceeds maximum range threshold (${rangeConfig.maxH1Adx})")
            )
        }

        if (context.currentMarketRegime == MarketRegime.STRONG_BULL_TREND ||
            context.currentMarketRegime == MarketRegime.STRONG_BEAR_TREND ||
            context.currentMarketRegime == MarketRegime.BREAKOUT ||
            context.currentMarketRegime == MarketRegime.HIGH_VOLATILITY ||
            context.currentMarketRegime == MarketRegime.UNSTABLE ||
            context.currentMarketRegime == MarketRegime.UNKNOWN
        ) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.UNSUPPORTED_REGIME),
                evidence = listOf("Current market regime ${context.currentMarketRegime} does not support range trading")
            )
        }

        // 2. M15 Range Detection
        val confirmedRange = rangeDetector.detectRange(
            symbol = symbol,
            timeframe = Timeframe.M15,
            candles = m15Candles,
            adx = h1Adx,
            atr = m15Atr,
            minTouchesPerBoundary = rangeConfig.minTouchesPerBoundary,
            minWidthAtrMultiple = rangeConfig.minWidthAtrMultiple,
            maxWidthAtrMultiple = rangeConfig.maxWidthAtrMultiple,
            minQualityScore = rangeConfig.minRangeQualityScore
        )

        if (confirmedRange == null) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.LOW_SIGNAL_SCORE),
                evidence = listOf("No confirmed M15 trading range detected or quality score below threshold")
            )
        }

        evidenceExplanations.add("Confirmed Range: ${confirmedRange.id} | Width: ${String.format("%.2f", confirmedRange.rangeWidth)}")

        // 3. Location relative to range boundaries
        val currentPrice = m5Candles.last().close
        val rangeWidth = confirmedRange.rangeWidth
        val distFromLower = currentPrice - confirmedRange.lowerBoundary.level
        val distFromUpper = confirmedRange.upperBoundary.level - currentPrice
        val positionPercent = (currentPrice - confirmedRange.lowerBoundary.level) / rangeWidth

        // Check if price is too close to midpoint (middle 40% of range: 30% to 70%)
        if (positionPercent in 0.30..0.70) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.LOW_SIGNAL_SCORE),
                evidence = listOf("Price (${currentPrice}) is near range midpoint (${confirmedRange.midpoint}), position: ${(positionPercent * 100).toInt()}%")
            )
        }

        val direction = if (positionPercent < 0.30) SignalDirection.LONG else SignalDirection.SHORT

        // 4. False Breakout & True Breakout Detection
        val falseBreakoutEvent = falseBreakoutDetector.detectFalseBreakout(
            candles = m15Candles,
            range = confirmedRange,
            direction = direction,
            atr = m15Atr,
            maxPenetrationAtrFraction = rangeConfig.maxPenetrationAtrFraction
        )

        if (falseBreakoutEvent != null && falseBreakoutEvent.type == FalseBreakoutType.TRUE_BREAKOUT) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.LOW_SIGNAL_SCORE),
                evidence = listOf(falseBreakoutEvent.explanation)
            )
        }

        // 5. Boundary Rejection Detection
        val rejection = rejectionDetector.detectRejection(
            candles = m15Candles,
            range = confirmedRange,
            direction = direction,
            atr = m15Atr
        )

        if (rejection == null || rejection.quality == RejectionQuality.INVALID) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.LOW_SIGNAL_SCORE),
                evidence = listOf("No valid boundary rejection confirmed at ${if (direction == SignalDirection.LONG) "lower" else "upper"} boundary")
            )
        }

        // 6. Mean Reversion Indicator Confluence
        val meanRevEval = meanReversionAnalyzer.evaluate(
            indicators = m15Snap.indicators,
            direction = direction,
            longRsiThreshold = rangeConfig.longRsiThreshold,
            shortRsiThreshold = rangeConfig.shortRsiThreshold
        )

        // 7. M5 Entry Confirmation
        val m5Last = m5Candles.last()
        val m5Confirmed = if (direction == SignalDirection.LONG) m5Last.isBullish else !m5Last.isBullish
        if (rangeConfig.requireM5Confirmation && !m5Confirmed) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.LOW_SIGNAL_SCORE),
                evidence = listOf("M5 candle does not confirm reversal in direction $direction")
            )
        }

        // 8. Stop-Loss & Take-Profit Logic
        val entryPrice = currentPrice
        val buffer = m15Atr * rangeConfig.atrSlBufferMultiple

        val proposedSl = if (direction == SignalDirection.LONG) {
            Math.min(rejection.extremePrice, confirmedRange.lowerBoundary.zoneLow) - buffer
        } else {
            Math.max(rejection.extremePrice, confirmedRange.upperBoundary.zoneHigh) + buffer
        }

        val proposedTp = if (rangeConfig.targetPolicy == RangeTargetPolicy.OPPOSITE_BOUNDARY) {
            if (direction == SignalDirection.LONG) confirmedRange.upperBoundary.level else confirmedRange.lowerBoundary.level
        } else {
            confirmedRange.midpoint
        }

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
        if (rrRatio < rangeConfig.minRiskRewardRatio) {
            return buildRejectedSignal(
                symbol = symbol,
                timestamp = context.dataTimestamp,
                reasons = listOf(NoTradeReason.POOR_RISK_REWARD),
                evidence = listOf("Risk-to-Reward ratio ${String.format("%.2f", rrRatio)} below minimum ${rangeConfig.minRiskRewardRatio}")
            )
        }

        // 9. Transparent 100-Point Signal Scoring
        val scoreRangeQuality = (confirmedRange.qualityScore / 5.0).toInt().coerceIn(0, 20)
        val scoreTouchCount = if (direction == SignalDirection.LONG) confirmedRange.lowerTouchCount * 3 else confirmedRange.upperTouchCount * 3
        val scoreBoundary = scoreTouchCount.coerceIn(0, 10)
        val scoreLocation = if (positionPercent <= 0.15 || positionPercent >= 0.85) 10 else 6
        val scoreRsi = if (meanRevEval.rsiPassed) 10 else 5
        val scoreBollinger = if (meanRevEval.bollingerPassed) 10 else 5
        val scoreRejection = when (rejection.quality) {
            RejectionQuality.STRONG -> 15
            RejectionQuality.MODERATE -> 10
            RejectionQuality.WEAK -> 5
            RejectionQuality.INVALID -> 0
        }
        val scoreFalseBreak = if (falseBreakoutEvent != null && falseBreakoutEvent.isReclaimed) 10 else 5
        val scoreM5 = if (m5Confirmed) 10 else 0
        val scoreRr = if (rrRatio >= rangeConfig.targetRiskRewardRatio) 5 else 3

        val totalScore = scoreRangeQuality + scoreBoundary + scoreLocation + scoreRsi +
                scoreBollinger + scoreRejection + scoreFalseBreak + scoreM5 + scoreRr

        val signalScore = SignalScore(
            trendAlignment = scoreRangeQuality,     // 0-20
            marketStructure = scoreRejection,        // 0-15
            momentum = scoreRsi + scoreBollinger,    // 0-20 (normalized by sum)
            volumeConfirmation = scoreFalseBreak,   // 0-10
            volatilitySuitability = scoreBoundary,   // 0-10
            entryQuality = scoreM5,                  // 0-10
            riskRewardQuality = scoreRr * 3,         // 0-15
            aiAdvisory = 0,                          // 0-5
            explanations = listOf(
                "Range Reversal Score $totalScore/100 | Rejection: ${rejection.quality} | Location: ${(positionPercent * 100).toInt()}% | R:R: ${String.format("%.2f", rrRatio)}"
            )
        )

        val decision = signalScore.getDecision(
            watchlistMin = config.minScoreForWatchlist,
            paperTradeMin = config.minScoreForPaperTrade,
            approvedMin = config.minScoreForApproved
        )

        val signalFingerprint = "RANGE_REV_${symbol}_${direction}_${confirmedRange.id}_${rejection.rejectionCandleTimestamp}"

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
                "Range Strategy ID: $id",
                "Direction: $direction",
                "Confirmed Range: ${confirmedRange.id} [${confirmedRange.lowerBoundary.level} - ${confirmedRange.upperBoundary.level}]",
                "Boundary Rejection: ${rejection.explanation}",
                "Mean Reversion: ${meanRevEval.explanation}",
                "Risk/Reward: ${String.format("%.2f", rrRatio)}"
            ),
            rejectionReasons = if (decision == SignalDecision.REJECT) listOf(NoTradeReason.LOW_SIGNAL_SCORE) else emptyList(),
            isDataFresh = true,
            isPaperTradeEligible = decision == SignalDecision.PAPER_TRADE || decision == SignalDecision.APPROVED,
            decision = decision
        )
    }

    private fun buildRejectedSignal(
        symbol: String,
        timestamp: Long,
        reasons: List<NoTradeReason>,
        evidence: List<String>
    ): StrategySignal {
        return StrategySignal(
            signalId = "RANGE_REV_REJECT_${symbol}_$timestamp",
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
