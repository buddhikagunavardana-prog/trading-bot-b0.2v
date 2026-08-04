package com.example.trading.portfolio

import com.example.trading.strategy.NoTradeReason

class PortfolioRiskManager(
    private val exposureManager: ExposureManager = ExposureManager(),
    private val correlationManager: CorrelationManager = CorrelationManager()
) {
    suspend fun validatePortfolioRisk(
        candidate: NormalisedCandidate,
        config: PortfolioConfig,
        currentTimeMs: Long = System.currentTimeMillis()
    ): PortfolioRiskReport {
        val rejectionReasons = mutableListOf<NoTradeReason>()
        val warnings = mutableListOf<String>()

        // 1. Kill Switch Check
        if (config.isGlobalKillSwitchActive) {
            rejectionReasons.add(NoTradeReason.RISK_ENGINE_REJECTED)
            warnings.add("Global Kill Switch is ACTIVE. Rejecting all portfolio trades.")
            return PortfolioRiskReport(
                isApproved = false,
                currentRiskPercent = 0.0,
                proposedRiskPercent = 0.0,
                riskAfterTradePercent = 0.0,
                exposureChanges = "No changes (Kill Switch active)",
                rejectionReasons = rejectionReasons,
                warnings = warnings,
                recommendedPositionSizeMultiplier = 0.0
            )
        }

        // 2. Exposure Checks
        val (exposureValid, exposureReasons) = exposureManager.validateCandidateExposure(candidate, config, currentTimeMs)
        if (!exposureValid) {
            rejectionReasons.addAll(exposureReasons)
        }

        // 3. Exposure Summary & Risk Calculations
        val summary = exposureManager.calculateExposureSummary(currentTimeMs)
        val equity = if (summary.totalAccountEquity > 0.0) summary.totalAccountEquity else 10_000.0

        val currentRiskPercent = (summary.totalRiskAmount / equity) * 100.0
        val proposedTradeRiskAmount = (equity * 0.01) // 1% risk per trade standard
        val proposedRiskPercent = 1.0
        val riskAfterTradePercent = currentRiskPercent + proposedRiskPercent

        if (riskAfterTradePercent > (config.maxRiskAllocationRatio * 100.0)) {
            rejectionReasons.add(NoTradeReason.RISK_ENGINE_REJECTED)
            warnings.add("Proposed total risk after trade (%.2f%%) exceeds max allowed portfolio risk (%.2f%%)".format(riskAfterTradePercent, config.maxRiskAllocationRatio * 100.0))
        }

        // Check drawdown
        if (summary.currentDrawdownPercent >= config.maxDrawdownPercent) {
            rejectionReasons.add(NoTradeReason.DAILY_LOSS_LIMIT)
            warnings.add("Current drawdown (%.2f%%) exceeds max drawdown limit (%.2f%%)".format(summary.currentDrawdownPercent, config.maxDrawdownPercent))
        }

        // 4. Correlation Risk Check
        val openPositions = ExposureManager().calculateExposureSummary(currentTimeMs) // state provider open positions
        val correlationReport = correlationManager.evaluateCorrelationRisk(candidate.signal.symbol, emptyList(), config)
        if (correlationReport.hasExcessiveCorrelation) {
            rejectionReasons.add(NoTradeReason.RISK_ENGINE_REJECTED)
            correlationReport.warnings.forEach { warnings.add(it.explanation) }
        }

        val isApproved = rejectionReasons.isEmpty()
        val sizeMultiplier = if (isApproved) {
            when {
                candidate.normalisedScore >= 85.0 -> 1.0
                candidate.normalisedScore >= 75.0 -> 0.85
                else -> 0.70
            }
        } else {
            0.0
        }

        return PortfolioRiskReport(
            isApproved = isApproved,
            currentRiskPercent = currentRiskPercent,
            proposedRiskPercent = proposedRiskPercent,
            riskAfterTradePercent = riskAfterTradePercent,
            exposureChanges = "Symbol: ${candidate.signal.symbol}, Direction: ${candidate.signal.direction}, Proposed Risk: 1.0%",
            rejectionReasons = rejectionReasons,
            warnings = warnings,
            recommendedPositionSizeMultiplier = sizeMultiplier
        )
    }
}
