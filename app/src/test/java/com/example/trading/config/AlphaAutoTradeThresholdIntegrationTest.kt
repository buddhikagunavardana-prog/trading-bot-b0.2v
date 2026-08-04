package com.example.trading.config

import com.example.model.CryptoTicker
import com.example.trading.analysis.AlphaOpportunityScanner
import com.example.trading.analysis.ExecutionDecisionEngine
import com.example.trading.analysis.MarketReadinessGate
import com.example.trading.history.ClosedTradeAccountingCalculator
import com.example.trading.history.PositionCloseReason
import com.example.trading.history.TradeDirection
import com.example.trading.risk.AccountRiskState
import com.example.trading.risk.RiskEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphaAutoTradeThresholdIntegrationTest {

    @Test
    fun testRepositorySettingsAndAuditLogging() = runBlocking {
        val repository = AlphaExecutionSettingsRepository.createInMemoryForTest()
        assertEquals(75.0, repository.settings.value.minimumAutoTradeScore, 0.001)
        assertEquals(1L, repository.settings.value.settingsVersion)

        val updatedResult = repository.updateSettings(
            newScore = 60.0,
            autoPaperTradingEnabled = true,
            source = SettingsUpdateSource.USER_UI
        )

        assertTrue(updatedResult.isSuccess)
        val updated = updatedResult.getOrThrow()
        assertEquals(60.0, updated.minimumAutoTradeScore, 0.001)
        assertEquals(2L, updated.settingsVersion)
        assertTrue(updated.highFrequencyTestMode)

        val audit = repository.latestAudit.value
        assertNotNull(audit)
        assertEquals(75.0, audit!!.previousScore, 0.001)
        assertEquals(60.0, audit.newScore, 0.001)
        assertEquals(2L, audit.settingsVersion)
    }

    @Test
    fun testScannerAppliesDynamicThreshold() {
        val scanner = AlphaOpportunityScanner()
        val tickers = listOf(
            CryptoTicker(
                symbol = "BTC/USDT",
                name = "Bitcoin",
                price = 67000.0,
                change24h = 1.5,
                high24h = 68000.0,
                low24h = 66000.0,
                volume = 50000000.0
            )
        )
        val readinessGate = MarketReadinessGate(bootstrapComplete = true)

        val scan75 = scanner.scanAllPairs(
            tickers = tickers,
            isFeedConnected = true,
            isFeedStale = false,
            readinessGate = readinessGate,
            opportunityThreshold = 75.0,
            thresholdSettingsVersion = 1L
        )

        val score75 = scan75.scores.first()
        assertEquals(75.0, score75.eligibilityThresholdUsed, 0.001)
        assertEquals(1L, score75.thresholdSettingsVersion)

        val scan55 = scanner.scanAllPairs(
            tickers = tickers,
            isFeedConnected = true,
            isFeedStale = false,
            readinessGate = readinessGate,
            opportunityThreshold = 55.0,
            thresholdSettingsVersion = 2L
        )

        val score55 = scan55.scores.first()
        assertEquals(55.0, score55.eligibilityThresholdUsed, 0.001)
        assertEquals(2L, score55.thresholdSettingsVersion)
    }

    @Test
    fun testExecutionDecisionEngineWithDynamicThresholdAndRiskEnforcement() {
        val riskEngine = RiskEngine()
        val executionEngine = ExecutionDecisionEngine(riskEngine = riskEngine)

        val scanner = AlphaOpportunityScanner()
        val tickers = listOf(
            CryptoTicker(
                symbol = "ETH/USDT",
                name = "Ethereum",
                price = 3500.0,
                change24h = 2.0,
                high24h = 3550.0,
                low24h = 3450.0,
                volume = 30000000.0
            )
        )
        val readinessGate = MarketReadinessGate(bootstrapComplete = true)

        val scanResult = scanner.scanAllPairs(
            tickers = tickers,
            isFeedConnected = true,
            isFeedStale = false,
            readinessGate = readinessGate,
            opportunityThreshold = 65.0,
            thresholdSettingsVersion = 3L
        )

        val rawScore = scanResult.scores.first()

        val decision = executionEngine.evaluateExecution(
            score = rawScore,
            ticker = tickers.first(),
            mtfSnapshot = null,
            configuredThreshold = 65.0,
            paperExecutionEnabled = true,
            activeTrades = emptyList(),
            portfolioDecision = null,
            accountRiskState = AccountRiskState(totalEquityUsdt = 10000.0, dailyRealizedPnlUsdt = 0.0, openPositionsCount = 0),
            currentTimeMs = System.currentTimeMillis(),
            settingsVersion = 3L,
            scanStartedAtEpochMs = System.currentTimeMillis()
        )

        assertEquals(65.0, decision.thresholdUsed, 0.001)
        assertEquals(3L, decision.thresholdSettingsVersion)
    }

    @Test
    fun testClosedTradeAccountingAuditFields() {
        val closedResult = ClosedTradeAccountingCalculator.buildClosedTradeResult(
            tradeId = "TR-1001",
            positionId = "POS-9901",
            sessionId = "SESS-1",
            symbol = "BTC/USDT",
            direction = TradeDirection.LONG,
            openedAtEpochMs = 1700000000000L,
            closedAtEpochMs = 1700003600000L,
            entryPrice = 65000.0,
            exitPrice = 67000.0,
            quantity = 0.1,
            leverage = 2,
            entryFeeUsdt = 1.0,
            exitFeeUsdt = 1.0,
            fundingCostUsdt = 0.0,
            slippageCostUsdt = 0.5,
            closeReason = PositionCloseReason.TAKE_PROFIT,
            stopLossPrice = 64000.0,
            takeProfitPrice = 67000.0,
            initialRiskUsdt = 100.0,
            alphaScoreAtEntry = 82.5,
            thresholdUsed = 65.0,
            settingsVersion = 4L
        )

        assertEquals(65.0, closedResult.thresholdUsed ?: 0.0, 0.001)
        assertEquals(4L, closedResult.settingsVersion ?: 0L)
    }
}
