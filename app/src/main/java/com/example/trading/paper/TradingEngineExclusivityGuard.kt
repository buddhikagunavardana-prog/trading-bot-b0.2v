package com.example.trading.paper

sealed class EngineExclusivityResult {
    data class Valid(val activeEngine: TradingEngineId) : EngineExclusivityResult()
    data class Violation(val errorCode: String, val message: String) : EngineExclusivityResult()
}

class TradingEngineExclusivityGuard(
    private val config: TradingEngineRuntimeConfig = TradingEngineRuntimeConfig()
) {

    fun validate(
        alphaRunning: Boolean,
        legacyRunning: Boolean
    ): EngineExclusivityResult {
        if (alphaRunning && legacyRunning) {
            return EngineExclusivityResult.Violation(
                errorCode = "ENGINE_EXCLUSIVITY_VIOLATION",
                message = "Both ALPHA_ENGINE and LEGACY_ENGINE are currently active. Exactly one engine must run."
            )
        }

        if (config.activeEngine == TradingEngineId.LEGACY_ENGINE || config.legacyEngineEnabled) {
            return EngineExclusivityResult.Violation(
                errorCode = "LEGACY_ENGINE_DISABLED",
                message = "LEGACY_ENGINE is permanently disabled. Only ALPHA_ENGINE is permitted."
            )
        }

        if (legacyRunning) {
            return EngineExclusivityResult.Violation(
                errorCode = "LEGACY_ENGINE_DISABLED",
                message = "Attempted execution by LEGACY_ENGINE. Legacy engine is isolated and disabled."
            )
        }

        if (!alphaRunning && config.activeEngine != TradingEngineId.ALPHA_ENGINE) {
            return EngineExclusivityResult.Violation(
                errorCode = "UNKNOWN_ENGINE_IDENTITY",
                message = "Active engine identity is unknown or invalid."
            )
        }

        if (config.realExchangeExecutionEnabled) {
            return EngineExclusivityResult.Violation(
                errorCode = "REAL_EXECUTION_DISABLED_FOR_ALPHA_ENGINE",
                message = "Real exchange execution is prohibited. Alpha Engine runs in PAPER trading mode only."
            )
        }

        return EngineExclusivityResult.Valid(TradingEngineId.ALPHA_ENGINE)
    }

    fun assertLegacyDisabled(): Boolean {
        check(!config.legacyEngineEnabled) {
            "ENGINE_EXCLUSIVITY_VIOLATION: Legacy engine must be disabled"
        }
        check(config.activeEngine == TradingEngineId.ALPHA_ENGINE) {
            "ENGINE_EXCLUSIVITY_VIOLATION: Active engine must be ALPHA_ENGINE"
        }
        return true
    }
}
