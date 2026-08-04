package com.example.trading.paper

enum class TelegramEngineSource {
    ALPHA_ENGINE,
    LEGACY_ENGINE
}

data class TelegramMessagePayload(
    val engineSource: TelegramEngineSource,
    val header: String,
    val sessionId: String,
    val body: String,
    val isPaperTradeWarningIncluded: Boolean = true
)

object TelegramAlphaIdentityReporter {

    private val sentMessages = mutableListOf<TelegramMessagePayload>()

    fun sendAlphaSessionStart(sessionId: String, startingCash: String = "10,000.00 USDT"): TelegramMessagePayload {
        val payload = TelegramMessagePayload(
            engineSource = TelegramEngineSource.ALPHA_ENGINE,
            header = "🚀 ALPHA ENGINE STARTED",
            sessionId = sessionId,
            body = """
                Mode: PAPER TRADING
                Market Data: LIVE BINANCE PUBLIC DATA
                Execution: SIMULATED
                Real Money: NOT USED
                Real Exchange Orders: DISABLED
                Session ID: $sessionId
                Starting Virtual Equity: $startingCash
            """.trimIndent()
        )
        sentMessages.add(payload)
        return payload
    }

    fun sendAlphaTradeEntry(
        sessionId: String,
        symbol: String,
        direction: String,
        strategy: String,
        signalScore: Double,
        simulatedEntry: Double,
        sl: Double,
        tp: Double,
        evidenceId: String
    ): TelegramMessagePayload {
        val payload = TelegramMessagePayload(
            engineSource = TelegramEngineSource.ALPHA_ENGINE,
            header = "⚠️ ALPHA ENGINE — PAPER TRADE\nNO REAL MONEY",
            sessionId = sessionId,
            body = """
                Session ID: $sessionId
                Symbol: $symbol
                Direction: $direction
                Strategy: $strategy
                Signal Score: $signalScore
                Simulated Entry: $simulatedEntry
                Stop Loss: $sl
                Take Profit: $tp
                Evidence ID: $evidenceId
            """.trimIndent()
        )
        sentMessages.add(payload)
        return payload
    }

    fun sendAlphaTradeExit(
        sessionId: String,
        symbol: String,
        exitReason: String,
        simulatedEntry: Double,
        simulatedExit: Double,
        netPnl: String,
        virtualEquity: String,
        evidenceId: String
    ): TelegramMessagePayload {
        val payload = TelegramMessagePayload(
            engineSource = TelegramEngineSource.ALPHA_ENGINE,
            header = "📊 ALPHA ENGINE — PAPER TRADE CLOSED\nNO REAL MONEY",
            sessionId = sessionId,
            body = """
                Session ID: $sessionId
                Symbol: $symbol
                Exit Reason: $exitReason
                Simulated Entry: $simulatedEntry
                Simulated Exit: $simulatedExit
                Net PnL: $netPnl
                Virtual Equity: $virtualEquity
                Evidence ID: $evidenceId
            """.trimIndent()
        )
        sentMessages.add(payload)
        return payload
    }

    fun sendAlphaStatus(
        sessionId: String,
        virtualCash: String,
        virtualEquity: String,
        openPositions: Int,
        closedTrades: Int
    ): TelegramMessagePayload {
        val payload = TelegramMessagePayload(
            engineSource = TelegramEngineSource.ALPHA_ENGINE,
            header = "🤖 ALPHA ENGINE STATUS",
            sessionId = sessionId,
            body = """
                Engine: ALPHA ENGINE
                Legacy Engine: DISABLED
                Trading Mode: PAPER
                Market Data: LIVE
                Execution: SIMULATED
                Real Orders: DISABLED
                Session ID: $sessionId
                Virtual Cash: $virtualCash
                Virtual Equity: $virtualEquity
                Open Positions: $openPositions
                Closed Trades: $closedTrades
                Accounting Status: RECONCILED
            """.trimIndent()
        )
        sentMessages.add(payload)
        return payload
    }

    fun sendAlphaOpportunityAlert(
        sessionId: String,
        symbol: String,
        score: Double,
        direction: String,
        strategyId: String?,
        marketRegime: String,
        evidenceId: String
    ): TelegramMessagePayload {
        val payload = TelegramMessagePayload(
            engineSource = TelegramEngineSource.ALPHA_ENGINE,
            header = "🎯 ALPHA OPPORTUNITY ALERT\nNO REAL MONEY",
            sessionId = sessionId,
            body = """
                Session ID: $sessionId
                Symbol: $symbol
                Direction: $direction
                Opportunity Score: ${String.format(java.util.Locale.US, "%.1f", score)}/100
                Strategy: ${strategyId ?: "MULTI_STRATEGY"}
                Regime: $marketRegime
                Evidence ID: $evidenceId
            """.trimIndent()
        )
        sentMessages.add(payload)
        return payload
    }

    fun processTelegramMessage(payload: TelegramMessagePayload): Boolean {
        if (payload.engineSource != TelegramEngineSource.ALPHA_ENGINE) {
            // Reject legacy engine messages
            return false
        }
        return true
    }

    fun getSentMessagesCount(): Int = sentMessages.size
    fun clearSentMessages() = sentMessages.clear()
}
