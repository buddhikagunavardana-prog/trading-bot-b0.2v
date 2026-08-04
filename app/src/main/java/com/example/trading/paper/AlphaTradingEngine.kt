package com.example.trading.paper

import java.time.Clock
import java.time.Instant

data class AlphaPaperAccount(
    val startingCash: Double = 10000.0,
    val currentCash: Double = 10000.0,
    val currentEquity: Double = 10000.0,
    val realisedPnl: Double = 0.0,
    val unrealisedPnl: Double = 0.0,
    val totalFees: Double = 0.0,
    val totalSlippage: Double = 0.0,
    val openPositionsCount: Int = 0,
    val closedTradesCount: Int = 0,
    val accountingVariance: Double = 0.0,
    val status: String = "ACTIVE"
)

sealed class EngineStartResult {
    data class Success(val engineId: TradingEngineId) : EngineStartResult()
    data class Disabled(val reason: String) : EngineStartResult()
}

class AlphaTradingEngine(
    private val clock: Clock = Clock.systemUTC(),
    private val runtimeConfig: TradingEngineRuntimeConfig = TradingEngineRuntimeConfig()
) {
    private val exclusivityGuard = TradingEngineExclusivityGuard(runtimeConfig)
    private var isAlphaRunning = false
    private var isLegacyRunning = false

    private val sessionStartInstant: Instant = clock.instant()
    val alphaSessionId: String = TradingTimeCodec.generateCanonicalSessionId(sessionStartInstant, prefix = "SESS_ALPHA_PAPER")
    val paperAccount = AlphaPaperAccount()

    fun startAlphaEngine(): Boolean {
        val exclusivity = exclusivityGuard.validate(alphaRunning = true, legacyRunning = false)
        if (exclusivity is EngineExclusivityResult.Violation) {
            error("Cannot start Alpha Engine: ${exclusivity.message}")
        }
        isAlphaRunning = true
        isLegacyRunning = false
        TelegramAlphaIdentityReporter.sendAlphaSessionStart(alphaSessionId)
        return true
    }

    fun attemptLegacyStart(): EngineStartResult {
        return EngineStartResult.Disabled(reason = "LEGACY_ENGINE_DISABLED")
    }

    fun isAlphaActive(): Boolean = isAlphaRunning
    fun isLegacyActive(): Boolean = isLegacyRunning
}
