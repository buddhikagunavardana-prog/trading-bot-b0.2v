package com.example.trading.analysis

import com.example.model.CryptoTicker
import com.example.trading.paper.TelegramAlphaIdentityReporter
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

/**
 * Production-Grade Alpha Opportunity Scanner for Phase 11.
 * Evaluates all 10 configured cryptocurrency pairs using live Binance market data & multi-timeframe technical analysis,
 * calculates a bounded deterministic Alpha Opportunity Score (0.0 to 100.0), assigns direction, eligibility,
 * ranks all pairs, and highlights top opportunity.
 */
class AlphaOpportunityScanner(
    private val dataQualityValidator: DataQualityValidator = DataQualityValidator(),
    private val warmupTracker: WarmupReadinessTracker = WarmupReadinessTracker(),
    private val marketRegimeDetector: MarketRegimeDetector = MarketRegimeDetector(),
    val defaultOpportunityThreshold: Double = 75.0,
    opportunityThreshold: Double = defaultOpportunityThreshold
) {
    val activeDefaultThreshold: Double = opportunityThreshold

    val canonicalUniverse = listOf(
        "BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT",
        "ADA/USDT", "DOGE/USDT", "AVAX/USDT", "DOT/USDT", "POL/USDT"
    )

    fun scanAllPairs(
        tickers: List<CryptoTicker>,
        isFeedConnected: Boolean = true,
        isFeedStale: Boolean = false,
        readinessGate: MarketReadinessGate = MarketReadinessGate(
            websocketConnected = true,
            bootstrapComplete = true,
            warmupComplete = true,
            snapshotComplete = true,
            dataFresh = true,
            genuineSourceOnly = true
        ),
        mtfSnapshots: Map<String, MultiTimeframeSnapshot> = emptyMap(),
        sessionId: String = "SESS_ALPHA_PAPER_LIVE",
        alertOnEligible: Boolean = true,
        opportunityThreshold: Double = activeDefaultThreshold,
        thresholdSettingsVersion: Long = 1L,
        scanStartedAtEpochMs: Long = System.currentTimeMillis()
    ): AlphaOpportunityScanResult {
        val now = Instant.ofEpochMilli(scanStartedAtEpochMs)
        safeLogI("AlphaThresholdPipeline", "STAGE 3 [SCANNER] Running scanAllPairs with Threshold = $opportunityThreshold, Version = $thresholdSettingsVersion")
        val scoredList = mutableListOf<AlphaOpportunityScore>()

        for (rawSymbol in canonicalUniverse) {
            val canonicalSymbol = com.example.trading.validation.SymbolNormalizer.toCanonicalDisplay(rawSymbol)
            val ticker = tickers.find { com.example.trading.validation.SymbolNormalizer.isSameSymbol(it.symbol, canonicalSymbol) }
            val mtfSnapshot = mtfSnapshots[canonicalSymbol]
                ?: mtfSnapshots.entries.find { com.example.trading.validation.SymbolNormalizer.isSameSymbol(it.key, canonicalSymbol) }?.value

            val score = evaluateSymbolOpportunity(
                rawSymbol = canonicalSymbol,
                ticker = ticker,
                mtfSnapshot = mtfSnapshot,
                isFeedConnected = isFeedConnected,
                isFeedStale = isFeedStale,
                readinessGate = readinessGate,
                now = now,
                opportunityThreshold = opportunityThreshold,
                thresholdSettingsVersion = thresholdSettingsVersion,
                scanStartedAtEpochMs = scanStartedAtEpochMs
            )

            scoredList.add(score)
        }

        // Rank pairs using deterministic tie-breaking: Score -> Signal -> RiskReward -> Freshness -> Symbol
        val rankedScores = scoredList.sortedWith(
            compareByDescending<AlphaOpportunityScore> { it.score }
                .thenByDescending { it.signalScore }
                .thenByDescending { it.riskRewardScore }
                .thenByDescending { it.freshnessScore }
                .thenBy { it.symbol }
        )

        val eligiblePairs = rankedScores.filter { it.eligibility == OpportunityEligibility.ELIGIBLE }
        val topOpportunity = eligiblePairs.firstOrNull() ?: rankedScores.firstOrNull()

        // Telegram Alert for Top Opportunity if eligible and meets threshold
        if (alertOnEligible && topOpportunity != null && topOpportunity.eligibility == OpportunityEligibility.ELIGIBLE && topOpportunity.score >= opportunityThreshold) {
            TelegramAlphaIdentityReporter.sendAlphaOpportunityAlert(
                sessionId = sessionId,
                symbol = topOpportunity.symbol,
                score = topOpportunity.score,
                direction = topOpportunity.direction.name,
                strategyId = topOpportunity.strategyId,
                marketRegime = topOpportunity.marketRegime.name,
                evidenceId = topOpportunity.evidenceId
            )
        }

        val analysisValidCount = rankedScores.count { it.calculationStatus == ScoreCalculationStatus.SCORE_CALCULATED }
        val aboveThresholdCount = rankedScores.count { it.score >= opportunityThreshold }
        val riskApprovedCount = rankedScores.count { it.executionDecision?.riskApproved == true }
        val portfolioApprovedCount = rankedScores.count { it.executionDecision?.portfolioApproved == true }
        val execEligibleCount = rankedScores.count { it.executionDecision?.approvedForExecution == true }
        val openedTradesCount = rankedScores.count { it.executionDecision?.executionStatus == ExecutionStatus.ORDER_OPENED }

        return AlphaOpportunityScanResult(
            totalPairsScanned = canonicalUniverse.size,
            eligiblePairsCount = eligiblePairs.size,
            analysisValidCount = analysisValidCount,
            aboveScoreThresholdCount = aboveThresholdCount,
            riskApprovedCount = riskApprovedCount,
            portfolioApprovedCount = portfolioApprovedCount,
            executionEligibleCount = execEligibleCount,
            openedPaperTradesCount = openedTradesCount,
            topOpportunity = topOpportunity,
            scores = rankedScores,
            scannedAt = now
        )
    }

    private fun evaluateSymbolOpportunity(
        rawSymbol: String,
        ticker: CryptoTicker?,
        mtfSnapshot: MultiTimeframeSnapshot?,
        isFeedConnected: Boolean,
        isFeedStale: Boolean,
        readinessGate: MarketReadinessGate,
        now: Instant,
        opportunityThreshold: Double,
        thresholdSettingsVersion: Long,
        scanStartedAtEpochMs: Long
    ): AlphaOpportunityScore {
        val cleanSymbol = rawSymbol.replace("/", "")
        val evidenceId = "EVI_OPP_${cleanSymbol}_${System.currentTimeMillis()}"
        val rejectionReasons = mutableListOf<String>()
        val penalties = mutableListOf<ScorePenalty>()

        // Step 1: Market Readiness Gate Verification
        if (!readinessGate.isFullyReady && !readinessGate.isPartialReady) {
            val isBlocked = readinessGate.blockingReason?.contains("451") == true || readinessGate.blockingReason?.contains("REGION") == true || readinessGate.blockingReason?.contains("BLOCKED") == true
            val eligibility = if (isBlocked) OpportunityEligibility.PROVIDER_REGION_BLOCKED else OpportunityEligibility.INELIGIBLE_DATA_NOT_READY
            val calcStatus = if (isBlocked) ScoreCalculationStatus.PROVIDER_REGION_BLOCKED else ScoreCalculationStatus.DATA_NOT_READY

            rejectionReasons.add(if (isBlocked) "PROVIDER_REGION_BLOCKED" else "GENUINE_KLINE_DATA_UNAVAILABLE")
            readinessGate.blockingReason?.let { rejectionReasons.add(it) }
            return createIneligibleScore(
                symbol = rawSymbol,
                eligibility = eligibility,
                calculationStatus = calcStatus,
                rejectionReasons = rejectionReasons,
                evidenceId = evidenceId,
                now = now,
                opportunityThreshold = opportunityThreshold,
                thresholdSettingsVersion = thresholdSettingsVersion,
                scanStartedAtEpochMs = scanStartedAtEpochMs
            )
        }

        // Step 2: Market Data Verification
        if (ticker == null) {
            rejectionReasons.add("MISSING_TICKER_DATA")
            return createIneligibleScore(
                symbol = rawSymbol,
                eligibility = OpportunityEligibility.INELIGIBLE_DATA_NOT_READY,
                rejectionReasons = rejectionReasons,
                evidenceId = evidenceId,
                now = now,
                opportunityThreshold = opportunityThreshold,
                thresholdSettingsVersion = thresholdSettingsVersion,
                scanStartedAtEpochMs = scanStartedAtEpochMs
            )
        }

        if (!isFeedConnected) {
            rejectionReasons.add("MARKET_FEED_DISCONNECTED")
            return createIneligibleScore(
                symbol = rawSymbol,
                eligibility = OpportunityEligibility.INELIGIBLE_DATA_NOT_READY,
                rejectionReasons = rejectionReasons,
                evidenceId = evidenceId,
                now = now,
                opportunityThreshold = opportunityThreshold,
                thresholdSettingsVersion = thresholdSettingsVersion,
                scanStartedAtEpochMs = scanStartedAtEpochMs
            )
        }

        if (isFeedStale) {
            rejectionReasons.add("STALE_MARKET_FEED")
            return createIneligibleScore(
                symbol = rawSymbol,
                eligibility = OpportunityEligibility.INELIGIBLE_STALE_DATA,
                rejectionReasons = rejectionReasons,
                evidenceId = evidenceId,
                now = now,
                opportunityThreshold = opportunityThreshold,
                thresholdSettingsVersion = thresholdSettingsVersion,
                scanStartedAtEpochMs = scanStartedAtEpochMs
            )
        }

        // Step 3: Multi-Timeframe Snapshot & Source Authenticity Verification
        if (mtfSnapshot == null) {
            rejectionReasons.add("GENUINE_KLINE_DATA_UNAVAILABLE")
            return createIneligibleScore(
                symbol = rawSymbol,
                eligibility = OpportunityEligibility.INELIGIBLE_DATA_NOT_READY,
                rejectionReasons = rejectionReasons,
                evidenceId = evidenceId,
                now = now,
                opportunityThreshold = opportunityThreshold,
                thresholdSettingsVersion = thresholdSettingsVersion,
                scanStartedAtEpochMs = scanStartedAtEpochMs
            )
        }

        val m5Candles = mtfSnapshot.m5?.candles ?: emptyList()
        val m15Candles = mtfSnapshot.m15?.candles ?: emptyList()
        val h1Candles = mtfSnapshot.h1?.candles ?: emptyList()

        if (m5Candles.isEmpty() || m15Candles.isEmpty() || h1Candles.isEmpty()) {
            rejectionReasons.add("GENUINE_KLINE_DATA_UNAVAILABLE")
            return createIneligibleScore(
                symbol = rawSymbol,
                eligibility = OpportunityEligibility.INELIGIBLE_DATA_NOT_READY,
                rejectionReasons = rejectionReasons,
                evidenceId = evidenceId,
                now = now,
                opportunityThreshold = opportunityThreshold,
                thresholdSettingsVersion = thresholdSettingsVersion,
                scanStartedAtEpochMs = scanStartedAtEpochMs
            )
        }

        if (!SourceAuthenticityGuard.validateCandles(m5Candles) ||
            !SourceAuthenticityGuard.validateCandles(m15Candles) ||
            !SourceAuthenticityGuard.validateCandles(h1Candles)
        ) {
            rejectionReasons.add("UNAUTHENTIC_SYNTHETIC_DATA_DETECTED")
            return createIneligibleScore(
                symbol = rawSymbol,
                eligibility = OpportunityEligibility.INELIGIBLE_DATA_NOT_READY,
                calculationStatus = ScoreCalculationStatus.DATA_INVALID,
                rejectionReasons = rejectionReasons,
                evidenceId = evidenceId,
                now = now,
                opportunityThreshold = opportunityThreshold,
                thresholdSettingsVersion = thresholdSettingsVersion,
                scanStartedAtEpochMs = scanStartedAtEpochMs
            )
        }

        // Data quality verification
        val qualityResult = dataQualityValidator.validateMultiTimeframe(mtfSnapshot)
        if (!qualityResult.isValid) {
            qualityResult.issues.forEach { rejectionReasons.add("DQ_${it.code}") }
            val isStaleOnly = qualityResult.issues.all { it.code.contains("STALE_DATA") }
            val eligibility = if (isStaleOnly) OpportunityEligibility.INELIGIBLE_STALE_DATA else OpportunityEligibility.INELIGIBLE_DATA_NOT_READY
            return createIneligibleScore(
                symbol = rawSymbol,
                eligibility = eligibility,
                calculationStatus = ScoreCalculationStatus.DATA_INVALID,
                rejectionReasons = rejectionReasons,
                evidenceId = evidenceId,
                now = now,
                opportunityThreshold = opportunityThreshold,
                thresholdSettingsVersion = thresholdSettingsVersion,
                scanStartedAtEpochMs = scanStartedAtEpochMs
            )
        }

        // Step 4: Multi-Timeframe Technical & Directional Evaluation
        val price = ticker.price
        val rsi = ticker.rsi
        val change24h = ticker.change24h
        val sma50 = ticker.sma50
        val sma200 = ticker.sma200

        val isBullishTrend = price > sma50 && sma50 > sma200
        val isBearishTrend = price < sma50 && sma50 < sma200

        // Determine Direction First for Directionally-Consistent Component Evaluation
        val candidateDirection = when {
            isBullishTrend && rsi >= 45.0 -> OpportunityDirection.LONG
            isBearishTrend && rsi <= 55.0 -> OpportunityDirection.SHORT
            change24h >= 0 -> OpportunityDirection.LONG
            else -> OpportunityDirection.SHORT
        }

        // Sub-score 1: Trend Alignment Score (0.0 to 15.0) - Evaluated Directionally
        var trendScore = 0.0
        val maSpreadPct = if (price > 0.0) abs(sma50 - sma200) / price * 100.0 else 0.0
        if (candidateDirection == OpportunityDirection.LONG) {
            if (isBullishTrend) {
                trendScore = if (maSpreadPct in 0.5..5.0) 15.0 else 12.0
            } else if (isBearishTrend) {
                trendScore = 2.0
                penalties.add(ScorePenalty("COUNTER_TREND_ALIGNMENT", 3.0))
            } else {
                trendScore = 8.0 // Ranging
            }
        } else { // SHORT
            if (isBearishTrend) {
                trendScore = if (maSpreadPct in 0.5..5.0) 15.0 else 12.0
            } else if (isBullishTrend) {
                trendScore = 2.0
                penalties.add(ScorePenalty("COUNTER_TREND_ALIGNMENT", 3.0))
            } else {
                trendScore = 8.0 // Ranging
            }
        }

        // Sub-score 2: Momentum Score (0.0 to 15.0) - Evaluated Directionally
        var momentumScore = 0.0
        if (candidateDirection == OpportunityDirection.LONG) {
            if (rsi in 50.0..75.0 && change24h > 0) {
                momentumScore = 15.0
            } else if (rsi in 40.0..50.0) {
                momentumScore = 10.0
            } else {
                momentumScore = 5.0
                if (rsi > 80.0) penalties.add(ScorePenalty("EXTREME_OVERBOUGHT_MOMENTUM", 3.0))
            }
        } else { // SHORT
            if (rsi in 25.0..50.0 && change24h < 0) {
                momentumScore = 15.0
            } else if (rsi in 50.0..60.0) {
                momentumScore = 10.0
            } else {
                momentumScore = 5.0
                if (rsi < 20.0) penalties.add(ScorePenalty("EXTREME_OVERSOLD_MOMENTUM", 3.0))
            }
        }

        // Sub-score 3: Market Regime & Structure Score (0.0 to 15.0) - Evaluated Directionally
        var regime: MarketRegime = marketRegimeDetector.detectRegime(mtfSnapshot)
        if (regime == MarketRegime.UNKNOWN || regime == MarketRegime.UNSTABLE) {
            regime = when {
                isBullishTrend && change24h > 2.0 -> MarketRegime.STRONG_BULL_TREND
                isBullishTrend -> MarketRegime.WEAK_BULL_TREND
                isBearishTrend && change24h < -2.0 -> MarketRegime.STRONG_BEAR_TREND
                isBearishTrend -> MarketRegime.WEAK_BEAR_TREND
                abs(change24h) > 4.0 -> MarketRegime.BREAKOUT
                else -> MarketRegime.RANGE
            }
        }

        val structureScore = if (candidateDirection == OpportunityDirection.LONG) {
            when (regime) {
                MarketRegime.STRONG_BULL_TREND -> 15.0
                MarketRegime.WEAK_BULL_TREND -> 12.0
                MarketRegime.BREAKOUT -> 13.0
                MarketRegime.RANGE -> 9.0
                MarketRegime.WEAK_BEAR_TREND -> 5.0
                MarketRegime.STRONG_BEAR_TREND -> 2.0
                else -> 7.0
            }
        } else { // SHORT
            when (regime) {
                MarketRegime.STRONG_BEAR_TREND -> 15.0
                MarketRegime.WEAK_BEAR_TREND -> 12.0
                MarketRegime.BREAKOUT -> 13.0
                MarketRegime.RANGE -> 9.0
                MarketRegime.WEAK_BULL_TREND -> 5.0
                MarketRegime.STRONG_BULL_TREND -> 2.0
                else -> 7.0
            }
        }

        // Sub-score 4: Volume & Liquidity Score (0.0 to 10.0)
        val volumeScore = when {
            ticker.volume >= 5_000_000.0 -> 10.0
            ticker.volume >= 1_000_000.0 -> 8.0
            ticker.volume >= 250_000.0 -> 6.0
            else -> 3.0
        }

        // Sub-score 5: Volatility & Expansion Score (0.0 to 8.0)
        val volatilityScore = when {
            abs(change24h) in 0.8..8.0 -> 8.0
            abs(change24h) in 0.3..12.0 -> 5.0
            else -> 3.0
        }

        // Sub-score 6: Signal Score (0.0 to 15.0) derived from ticker.aiScore
        val signalScore = ((ticker.aiScore.toDouble() - 30.0) / 60.0 * 15.0).coerceIn(4.0, 15.0)

        // Sub-score 7: Risk / Reward Score (0.0 to 12.0) - Deterministic derivation from trade setup
        val entryPrice = price
        val (stopLossPrice, takeProfitPrice) = if (candidateDirection == OpportunityDirection.LONG) {
            Pair(entryPrice * 0.98, entryPrice * 1.05)
        } else {
            Pair(entryPrice * 1.02, entryPrice * 0.95)
        }
        val risk = abs(entryPrice - stopLossPrice)
        val reward = abs(takeProfitPrice - entryPrice)
        val riskRewardRatio = if (risk > 0.0) reward / risk else 0.0

        val riskRewardScore = when {
            riskRewardRatio >= 2.5 -> 12.0
            riskRewardRatio >= 2.0 -> 10.0
            riskRewardRatio >= 1.5 -> 7.0
            riskRewardRatio >= 1.0 -> 4.0
            else -> 1.0
        }

        // Sub-score 8: Freshness Score (0.0 to 5.0) - Timeframe-aware calculation
        val latestCandleTimestamp = m5Candles.lastOrNull()?.closeTimestamp
            ?: m5Candles.lastOrNull()?.timestamp
            ?: 0L
        val ageMs = if (latestCandleTimestamp > 0) abs(now.toEpochMilli() - latestCandleTimestamp) else Long.MAX_VALUE
        val ageMinutes = ageMs / 60_000.0

        val freshnessScore = when {
            ageMinutes <= 5.0 -> 5.0
            ageMinutes <= 15.0 -> 4.0
            ageMinutes <= 60.0 -> 2.0
            else -> 0.0
        }

        // Sub-score 9: Data Quality Score (0.0 to 5.0) - Measurable checks
        val dataQualityScore = when {
            qualityResult.isValid && m5Candles.size >= 200 -> 5.0
            qualityResult.isValid && m5Candles.size >= 100 -> 4.0
            qualityResult.isValid && m5Candles.size >= 20 -> 2.0
            else -> 0.0
        }

        // Sub-score 10: Baseline Evidence Score (0.0 to 5.0) - Uncalibrated pairs receive baseline without 0 penalty
        val portfolioScore = 5.0

        // Calculate Raw Subtotal and Final Bounded Score
        val rawSubtotal = trendScore + momentumScore + structureScore + volumeScore +
                volatilityScore + signalScore + riskRewardScore + freshnessScore +
                dataQualityScore + portfolioScore

        val totalPenalties = penalties.sumOf { it.pointsDeducted }
        val finalScore = (rawSubtotal - totalPenalties).coerceIn(0.0, 100.0)

        val breakdown = ScoreBreakdown(
            trendScore = trendScore, trendMax = 15.0,
            momentumScore = momentumScore, momentumMax = 15.0,
            structureScore = structureScore, structureMax = 15.0,
            volumeScore = volumeScore, volumeMax = 10.0,
            volatilityScore = volatilityScore, volatilityMax = 8.0,
            signalScore = signalScore, signalMax = 15.0,
            riskRewardScore = riskRewardScore, riskRewardMax = 12.0,
            freshnessScore = freshnessScore, freshnessMax = 5.0,
            dataQualityScore = dataQualityScore, dataQualityMax = 5.0,
            portfolioScore = portfolioScore, portfolioMax = 5.0,
            totalPenalties = totalPenalties,
            rawSubtotal = rawSubtotal,
            maxPossiblePoints = 100.0,
            finalScore = finalScore
        )

        // Final Direction Assignment
        val direction = when {
            finalScore < 30.0 -> OpportunityDirection.NO_TRADE
            candidateDirection == OpportunityDirection.LONG && finalScore >= 35.0 -> OpportunityDirection.LONG
            candidateDirection == OpportunityDirection.SHORT && finalScore >= 35.0 -> OpportunityDirection.SHORT
            else -> OpportunityDirection.NEUTRAL
        }

        // Select Strategy ID
        val strategyId = when (direction) {
            OpportunityDirection.LONG -> if (regime == MarketRegime.STRONG_BULL_TREND || regime == MarketRegime.WEAK_BULL_TREND) "TREND_PULLBACK" else "MOMENTUM_CONTINUATION"
            OpportunityDirection.SHORT -> if (regime == MarketRegime.STRONG_BEAR_TREND || regime == MarketRegime.WEAK_BEAR_TREND) "BASELINE_TREND_FOLLOW" else "RANGE_REVERSAL"
            else -> null
        }

        // Determine Eligibility
        val scoreGateResult = if (finalScore >= opportunityThreshold) "PASSED" else "FAILED"
        safeLogI("AlphaThresholdPipeline", "STAGE 3 [SCANNER ITEM] Symbol=$rawSymbol Score=${String.format(java.util.Locale.US, "%.1f", finalScore)} Threshold=${String.format(java.util.Locale.US, "%.1f", opportunityThreshold)} ScoreGate=$scoreGateResult")

        val eligibility = when {
            finalScore < opportunityThreshold -> {
                rejectionReasons.add("SCORE_BELOW_THRESHOLD (${String.format(java.util.Locale.US, "%.1f", finalScore)} < $opportunityThreshold)")
                OpportunityEligibility.INELIGIBLE_BELOW_THRESHOLD
            }
            direction == OpportunityDirection.NO_TRADE || direction == OpportunityDirection.NEUTRAL -> {
                rejectionReasons.add("DIRECTION_NEUTRAL_OR_NO_TRADE")
                OpportunityEligibility.INELIGIBLE_CONFLICT
            }
            else -> OpportunityEligibility.ELIGIBLE
        }

        val calcStatus = if (finalScore == 0.0) ScoreCalculationStatus.VALID_SCORE_ZERO else ScoreCalculationStatus.SCORE_CALCULATED

        val baseScore = AlphaOpportunityScore(
            symbol = rawSymbol,
            score = finalScore,
            direction = direction,
            eligibility = eligibility,
            marketRegime = regime,
            calculationStatus = calcStatus,
            strategyId = strategyId,
            signalScore = signalScore,
            trendScore = trendScore,
            momentumScore = momentumScore,
            structureScore = structureScore,
            volumeScore = volumeScore,
            volatilityScore = volatilityScore,
            riskRewardScore = riskRewardScore,
            freshnessScore = freshnessScore,
            dataQualityScore = dataQualityScore,
            portfolioScore = portfolioScore,
            penalties = penalties,
            rejectionReasons = rejectionReasons,
            evaluatedAt = now,
            evidenceId = evidenceId,
            componentBreakdown = breakdown,
            scoringModelVersion = "v2.0_100pt_exact",
            scoreCalculatedAt = now,
            eligibilityThresholdUsed = opportunityThreshold,
            thresholdSettingsVersion = thresholdSettingsVersion,
            scanStartedAtEpochMs = scanStartedAtEpochMs,
            lastUpdatedEpochMs = now.toEpochMilli(),
            dataAgeMs = ageMs,
            activeProvider = "OKX_SWAP_PUBLIC",
            providerSymbol = rawSymbol,
            dataOrigin = "REST_BOOTSTRAP"
        )

        val confidence = AlphaConfidenceCalculator.calculateConfidence(baseScore, ticker, mtfSnapshot, readinessGate)
        val tradePlan = AlphaTradePlanCalculator.calculateTradePlan(rawSymbol, direction, price, null)
        val marketPressure = MarketPressureCalculator.calculateMarketPressure("OKX_SWAP_PUBLIC", rawSymbol, ticker, mtfSnapshot)
        val liquidityEvidence = LiquidityEvidenceCalculator.calculateLiquidityEvidence("OKX_SWAP_PUBLIC", rawSymbol, ticker)
        val historicalPerformance = HistoricalPerformanceCalculator.calculateHistoricalEvidence(strategyId, regime.name, rawSymbol, emptyList())
        val reasonSummary = AlphaReasonSummaryBuilder.buildReasonSummary(baseScore, null)

        return baseScore.copy(
            confidence = confidence,
            tradePlan = tradePlan,
            marketPressure = marketPressure,
            liquidityEvidence = liquidityEvidence,
            historicalPerformance = historicalPerformance,
            reasonSummary = reasonSummary
        )
    }

    private fun createIneligibleScore(
        symbol: String,
        eligibility: OpportunityEligibility,
        calculationStatus: ScoreCalculationStatus = ScoreCalculationStatus.DATA_NOT_READY,
        rejectionReasons: List<String>,
        evidenceId: String,
        now: Instant,
        opportunityThreshold: Double = defaultOpportunityThreshold,
        thresholdSettingsVersion: Long = 1L,
        scanStartedAtEpochMs: Long = System.currentTimeMillis()
    ): AlphaOpportunityScore {
        val breakdown = ScoreBreakdown(
            maxPossiblePoints = 100.0,
            finalScore = 0.0
        )
        val baseScore = AlphaOpportunityScore(
            symbol = symbol,
            score = 0.0,
            direction = OpportunityDirection.NO_TRADE,
            eligibility = eligibility,
            marketRegime = MarketRegime.UNKNOWN,
            calculationStatus = calculationStatus,
            strategyId = null,
            rejectionReasons = rejectionReasons,
            evaluatedAt = now,
            evidenceId = evidenceId,
            componentBreakdown = breakdown,
            scoringModelVersion = "v2.0_100pt_exact",
            scoreCalculatedAt = now,
            eligibilityThresholdUsed = opportunityThreshold,
            thresholdSettingsVersion = thresholdSettingsVersion,
            scanStartedAtEpochMs = scanStartedAtEpochMs,
            lastUpdatedEpochMs = now.toEpochMilli(),
            dataAgeMs = 0L,
            activeProvider = "OKX_SWAP_PUBLIC",
            providerSymbol = symbol,
            dataOrigin = "REST_BOOTSTRAP"
        )

        val confidence = AlphaConfidence(
            confidencePercent = null,
            method = ConfidenceMethod.UNAVAILABLE,
            sampleSize = 0,
            calibrationStatus = CalibrationStatus.INSUFFICIENT_EVIDENCE,
            unavailableReason = "UNAVAILABLE — data provider unavailable or incomplete"
        )
        val tradePlan = AlphaTradePlanCalculator.calculateTradePlan(symbol, OpportunityDirection.NO_TRADE, null, null)
        val marketPressure = MarketPressureCalculator.calculateMarketPressure("OKX_SWAP_PUBLIC", symbol, null, null)
        val liquidityEvidence = LiquidityEvidenceCalculator.calculateLiquidityEvidence("OKX_SWAP_PUBLIC", symbol, null)
        val historicalPerformance = HistoricalPerformanceCalculator.calculateHistoricalEvidence(null, "UNKNOWN", symbol, emptyList())
        val reasonSummary = AlphaReasonSummaryBuilder.buildReasonSummary(baseScore, null)

        return baseScore.copy(
            confidence = confidence,
            tradePlan = tradePlan,
            marketPressure = marketPressure,
            liquidityEvidence = liquidityEvidence,
            historicalPerformance = historicalPerformance,
            reasonSummary = reasonSummary
        )
    }

    private fun safeLogI(tag: String, msg: String) {
        runCatching { android.util.Log.i(tag, msg) }
    }
}
