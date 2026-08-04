package com.example.trading.portfolio

import com.example.trading.analysis.MarketRegime
import com.example.trading.strategy.SignalDirection
import com.example.trading.strategy.StrategySignal
import kotlin.math.max
import kotlin.math.min

class SignalNormaliser(
    private val performanceProvider: StrategyPerformanceProvider = DefaultStrategyPerformanceProvider()
) {
    suspend fun normaliseSignal(
        signal: StrategySignal,
        regime: MarketRegime,
        config: PortfolioConfig,
        currentTimeMs: Long = System.currentTimeMillis()
    ): NormalisedCandidate {
        val components = mutableListOf<NormalisationComponent>()

        // 1. Raw Strategy Score (0 to 35)
        val rawScoreFraction = (signal.finalScore.toDouble() / 100.0).coerceIn(0.0, 1.0)
        val rawScoreVal = rawScoreFraction * 35.0
        components.add(
            NormalisationComponent(
                name = "Raw Strategy Score",
                rawValue = signal.finalScore.toDouble(),
                normalisedValue = rawScoreVal,
                maxPossibleValue = 35.0,
                weight = 0.35,
                explanation = "Strategy internal score ${signal.finalScore}/100 scaled to 35",
                dataSource = "StrategySignal.finalScore",
                confidence = 1.0
            )
        )

        // 2. Regime Compatibility (0 to 15)
        val regimeScore = calculateRegimeCompatibilityScore(signal.strategyId, regime, config)
        components.add(
            NormalisationComponent(
                name = "Regime Compatibility",
                rawValue = regimeScore,
                normalisedValue = regimeScore,
                maxPossibleValue = 15.0,
                weight = 0.15,
                explanation = "Compatibility of ${signal.strategyId} with regime $regime",
                dataSource = "PortfolioConfig.allowedMarketRegimesPerStrategy",
                confidence = 0.9
            )
        )

        // 3. Risk/Reward Quality (0 to 15)
        val rr = signal.riskRewardRatio
        val rrScore = when {
            rr < 1.0 -> 0.0
            rr >= 3.0 -> 15.0
            else -> ((rr - 1.0) / 2.0) * 15.0
        }.coerceIn(0.0, 15.0)
        components.add(
            NormalisationComponent(
                name = "Risk/Reward Quality",
                rawValue = rr,
                normalisedValue = rrScore,
                maxPossibleValue = 15.0,
                weight = 0.15,
                explanation = "R:R ratio of %.2f".format(rr),
                dataSource = "StrategySignal.riskRewardRatio",
                confidence = 1.0
            )
        )

        // 4. Signal Freshness (0 to 10)
        val ageMs = max(0L, currentTimeMs - signal.signalTimestamp)
        val maxAgeMs = config.maxAcceptableSignalAgeMs
        val freshnessFraction = (1.0 - (ageMs.toDouble() / maxAgeMs.toDouble())).coerceIn(0.0, 1.0)
        val freshnessScore = freshnessFraction * 10.0
        components.add(
            NormalisationComponent(
                name = "Signal Freshness",
                rawValue = ageMs.toDouble(),
                normalisedValue = freshnessScore,
                maxPossibleValue = 10.0,
                weight = 0.10,
                explanation = "Signal age ${ageMs / 1000}s (max ${maxAgeMs / 1000}s)",
                dataSource = "Signal timestamp comparison",
                confidence = 1.0
            )
        )

        // 5. Data Quality (0 to 10)
        val dataQualityScore = if (signal.isDataFresh) 10.0 else 2.0
        components.add(
            NormalisationComponent(
                name = "Data Quality",
                rawValue = if (signal.isDataFresh) 1.0 else 0.0,
                normalisedValue = dataQualityScore,
                maxPossibleValue = 10.0,
                weight = 0.10,
                explanation = if (signal.isDataFresh) "Data fresh and valid" else "Data stale or low quality",
                dataSource = "StrategySignal.isDataFresh",
                confidence = 1.0
            )
        )

        // 6. Strategy Historical Reliability (0 to 10)
        val metrics = performanceProvider.getMetrics(signal.strategyId, signal.symbol, regime)
        val (reliabilityScore, isReliable) = if (metrics != null && metrics.isReliable) {
            val winRatePart = (metrics.winRate / 0.70).coerceIn(0.0, 1.0) * 5.0
            val pfPart = (metrics.profitFactor / 2.0).coerceIn(0.0, 1.0) * 5.0
            Pair((winRatePart + pfPart).coerceIn(0.0, 10.0), true)
        } else {
            Pair(5.0, false) // Neutral default for UNKNOWN
        }
        components.add(
            NormalisationComponent(
                name = "Strategy Historical Reliability",
                rawValue = reliabilityScore,
                normalisedValue = reliabilityScore,
                maxPossibleValue = 10.0,
                weight = 0.10,
                explanation = if (isReliable) "Verified strategy metrics applied" else "Unverified/Unknown history, using neutral default 5.0",
                dataSource = "StrategyPerformanceProvider",
                confidence = if (isReliable) 0.85 else 0.5
            )
        )

        // 7. Portfolio Compatibility (0 to 5)
        val portfolioCompatScore = 5.0
        components.add(
            NormalisationComponent(
                name = "Portfolio Compatibility",
                rawValue = 1.0,
                normalisedValue = portfolioCompatScore,
                maxPossibleValue = 5.0,
                weight = 0.05,
                explanation = "Initial candidate portfolio compatibility",
                dataSource = "PortfolioConfig checks",
                confidence = 1.0
            )
        )

        val totalNormalised = components.sumOf { it.normalisedValue }.coerceIn(0.0, 100.0)

        // Calculate strategy weighting
        val configuredWeight = config.perStrategyWeight[signal.strategyId] ?: 1.0
        val isEnabled = config.enabledStrategyIds.contains(signal.strategyId) && !config.disabledStrategyIds.contains(signal.strategyId)
        val effectiveWeight = if (isEnabled) {
            val regimeFactor = (regimeScore / 15.0).coerceIn(0.1, 1.0)
            configuredWeight * regimeFactor
        } else {
            0.0
        }

        // Generate signal fingerprint for duplicate detection
        val fingerprint = "${signal.symbol}_${signal.direction}_${signal.strategyId}_${signal.signalTimestamp / 60000}"

        return NormalisedCandidate(
            signal = signal,
            rawStrategyScore = signal.finalScore.toDouble(),
            normalisedScore = totalNormalised,
            components = components,
            effectiveWeight = effectiveWeight,
            signalFingerprint = fingerprint,
            isReliabilityVerified = isReliable
        )
    }

    private fun calculateRegimeCompatibilityScore(
        strategyId: String,
        regime: MarketRegime,
        config: PortfolioConfig
    ): Double {
        val allowedRegimes = config.allowedMarketRegimesPerStrategy[strategyId]
        if (allowedRegimes != null && !allowedRegimes.contains(regime)) {
            return 0.0
        }

        return when (regime) {
            MarketRegime.STRONG_BULL_TREND, MarketRegime.STRONG_BEAR_TREND -> {
                when (strategyId) {
                    "trend_pullback", "momentum_continuation", "breakout_retest", "baseline_trend_follow" -> 15.0
                    "smc_liquidity_sweep" -> 12.0
                    "range_reversal" -> 0.0
                    else -> 8.0
                }
            }
            MarketRegime.WEAK_BULL_TREND, MarketRegime.WEAK_BEAR_TREND -> {
                when (strategyId) {
                    "trend_pullback", "baseline_trend_follow" -> 14.0
                    "smc_liquidity_sweep" -> 10.0
                    "momentum_continuation", "breakout_retest" -> 8.0
                    "range_reversal" -> 2.0
                    else -> 7.0
                }
            }
            MarketRegime.RANGE, MarketRegime.LOW_VOLATILITY -> {
                when (strategyId) {
                    "range_reversal", "smc_liquidity_sweep" -> 15.0
                    "breakout_retest" -> 6.0
                    "momentum_continuation", "trend_pullback", "baseline_trend_follow" -> 0.0
                    else -> 5.0
                }
            }
            MarketRegime.BREAKOUT -> {
                when (strategyId) {
                    "breakout_retest", "momentum_continuation" -> 15.0
                    "smc_liquidity_sweep" -> 10.0
                    "range_reversal", "trend_pullback", "baseline_trend_follow" -> 3.0
                    else -> 5.0
                }
            }
            MarketRegime.HIGH_VOLATILITY -> {
                when (strategyId) {
                    "smc_liquidity_sweep", "range_reversal" -> 10.0
                    else -> 0.0
                }
            }
            MarketRegime.UNSTABLE, MarketRegime.UNKNOWN -> 0.0
        }
    }
}
