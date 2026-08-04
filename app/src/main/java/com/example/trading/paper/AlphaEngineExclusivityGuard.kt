package com.example.trading.paper

class EngineExclusivityException(
    override val message: String,
    val errorCode: String = "ENGINE_EXCLUSIVITY_VIOLATION"
) : IllegalStateException(message)

class AlphaEngineExclusivityGuard(
    private val config: TradingEngineRuntimeConfig = TradingEngineRuntimeConfig()
) {

    /**
     * Validates that exclusively [TradingEngineId.ALPHA_ENGINE] is running.
     * Throws [EngineExclusivityException] with errorCode `ENGINE_EXCLUSIVITY_VIOLATION`
     * if legacy services or multiple engines are detected at startup.
     */
    @Throws(EngineExclusivityException::class)
    fun validate(
        alphaRunning: Boolean = true,
        legacyRunning: Boolean = false,
        activeServices: List<String> = emptyList()
    ) {
        if (alphaRunning && legacyRunning) {
            throw EngineExclusivityException(
                message = "ENGINE_EXCLUSIVITY_VIOLATION: Multiple trading engines active simultaneously (ALPHA_ENGINE and LEGACY_ENGINE)."
            )
        }

        if (legacyRunning || config.legacyEngineEnabled) {
            throw EngineExclusivityException(
                message = "ENGINE_EXCLUSIVITY_VIOLATION: Legacy engine or legacy services detected. Only ALPHA_ENGINE is permitted."
            )
        }

        if (config.activeEngine != TradingEngineId.ALPHA_ENGINE) {
            throw EngineExclusivityException(
                message = "ENGINE_EXCLUSIVITY_VIOLATION: Active engine identity must be ALPHA_ENGINE (found: ${config.activeEngine})."
            )
        }

        val legacyServices = activeServices.filter { serviceName ->
            serviceName.contains("Legacy", ignoreCase = true) ||
            serviceName.contains("OldEngine", ignoreCase = true)
        }

        if (legacyServices.isNotEmpty()) {
            throw EngineExclusivityException(
                message = "ENGINE_EXCLUSIVITY_VIOLATION: Legacy services active at startup: ${legacyServices.joinToString(", ")}."
            )
        }
    }
}
