package com.example.trading.paper

data class TradeProvenanceVerification(
    val tradeId: String,
    val hasEventSource: Boolean,
    val hasValidExchangeTimestamp: Boolean,
    val hasSessionId: Boolean,
    val hasEventOrigin: Boolean,
    val hasStrategyId: Boolean,
    val hasRiskDecision: Boolean,
    val hasPortfolioDecision: Boolean,
    val hasOrderAndPosition: Boolean,
    val hasTelegramNotification: Boolean,
    val isObservationEligible: Boolean,
    val isPerformanceIncluded: Boolean,
    val status: String
)

object TradeProvenanceValidator {

    fun validateTradeProvenance(
        tradeId: String,
        marketEventId: String?,
        exchangeTimestamp: Long,
        sessionStartMs: Long,
        sessionId: String?,
        eventOrigin: EventOrigin,
        strategyId: String?,
        riskDecisionId: String?,
        portfolioDecisionId: String?,
        orderId: String?,
        positionId: String?,
        telegramEventId: String?
    ): TradeProvenanceVerification {
        val hasEventSource = !marketEventId.isNull_or_blank()
        val isPostSession = exchangeTimestamp > sessionStartMs
        val hasSessionId = !sessionId.isNull_or_blank()
        val hasEventOrigin = eventOrigin == EventOrigin.LIVE_STREAM
        val hasStrategyId = !strategyId.isNull_or_blank()
        val hasRiskDecision = !riskDecisionId.isNull_or_blank()
        val hasPortfolioDecision = !portfolioDecisionId.isNull_or_blank()
        val hasOrderAndPosition = !orderId.isNull_or_blank() && !positionId.isNull_or_blank()
        val hasTelegramNotification = !telegramEventId.isNull_or_blank()

        val isEligible = isPostSession && hasEventOrigin && hasEventSource && hasRiskDecision && hasPortfolioDecision
        val isIncluded = isEligible

        val status = when {
            !isPostSession -> "EXCLUDED_PRE_SESSION_EVENT"
            !hasEventOrigin -> "EXCLUDED_NON_LIVE_ORIGIN"
            !isEligible -> "EXCLUDED_INCOMPLETE_PROVENANCE_CHAIN"
            else -> "PASSED_PROVENANCE_VERIFICATION"
        }

        return TradeProvenanceVerification(
            tradeId = tradeId,
            hasEventSource = hasEventSource,
            hasValidExchangeTimestamp = isPostSession,
            hasSessionId = hasSessionId,
            hasEventOrigin = hasEventOrigin,
            hasStrategyId = hasStrategyId,
            hasRiskDecision = hasRiskDecision,
            hasPortfolioDecision = hasPortfolioDecision,
            hasOrderAndPosition = hasOrderAndPosition,
            hasTelegramNotification = hasTelegramNotification,
            isObservationEligible = isEligible,
            isPerformanceIncluded = isIncluded,
            status = status
        )
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
}
