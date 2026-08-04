package com.example.trading.portfolio

data class SymbolGroup(
    val groupName: String,
    val symbols: Set<String>
)

class CorrelationManager(
    private val symbolGroups: List<SymbolGroup> = listOf(
        SymbolGroup("BTC_MAJORS", setOf("BTCUSDT", "ETHUSDT", "SOLUSDT")),
        SymbolGroup("ETH_ECOSYSTEM", setOf("ETHUSDT", "OPUSDT", "ARBUSDT", "MATICUSDT")),
        SymbolGroup("HIGH_BETA_ALTS", setOf("DOGEUSDT", "SHIBUSDT", "PEPEUSDT", "AVAXUSDT"))
    )
) {
    fun evaluateCorrelationRisk(
        candidateSymbol: String,
        openPositions: List<PortfolioPosition>,
        config: PortfolioConfig
    ): CorrelationReport {
        val warnings = mutableListOf<CorrelationWarning>()
        val activeCorrelatedGroups = mutableListOf<String>()
        var excessiveCorrelation = false

        // Find which groups candidate belongs to
        val matchingGroups = symbolGroups.filter { it.symbols.contains(candidateSymbol.uppercase()) }

        for (group in matchingGroups) {
            val correlatedOpenPositions = openPositions.filter {
                group.symbols.contains(it.symbol.uppercase())
            }

            if (correlatedOpenPositions.isNotEmpty()) {
                activeCorrelatedGroups.add(group.groupName)
                val totalCorrelatedPositions = correlatedOpenPositions.size
                val ratio = (totalCorrelatedPositions + 1).toDouble() / config.maxTotalOpenPositions.toDouble()

                if (ratio > config.maxCorrelatedExposureRatio) {
                    excessiveCorrelation = true
                    warnings.add(
                        CorrelationWarning(
                            symbol1 = candidateSymbol,
                            symbol2 = correlatedOpenPositions.first().symbol,
                            groupName = group.groupName,
                            correlationValue = 0.85,
                            explanation = "Candidate $candidateSymbol is in correlated group '${group.groupName}' with existing position ${correlatedOpenPositions.first().symbol}. Group exposure ratio %.2f exceeds max allowed %.2f".format(ratio, config.maxCorrelatedExposureRatio)
                        )
                    )
                }
            }
        }

        return CorrelationReport(
            hasExcessiveCorrelation = excessiveCorrelation,
            warnings = warnings,
            activeCorrelatedGroups = activeCorrelatedGroups
        )
    }
}
