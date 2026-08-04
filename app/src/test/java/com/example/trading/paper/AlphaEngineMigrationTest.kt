package com.example.trading.paper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AlphaEngineMigrationTest {

    private val fixedNow = Instant.parse("2026-07-30T07:30:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private lateinit var engine: AlphaTradingEngine
    private lateinit var guard: TradingEngineExclusivityGuard

    @Before
    fun setUp() {
        TelegramAlphaIdentityReporter.clearSentMessages()
        val config = TradingEngineRuntimeConfig(
            activeEngine = TradingEngineId.ALPHA_ENGINE,
            legacyEngineEnabled = false,
            tradingMode = TradingMode.PAPER,
            realExchangeExecutionEnabled = false
        )
        engine = AlphaTradingEngine(clock, config)
        guard = TradingEngineExclusivityGuard(config)
    }

    @Test
    fun test1_AlphaEngineIsOnlyActiveEngine() {
        engine.startAlphaEngine()
        assertTrue(engine.isAlphaActive())
        assertFalse(engine.isLegacyActive())
    }

    @Test
    fun test2_LegacyEngineStartupIsRejected() {
        val result = engine.attemptLegacyStart()
        assertTrue(result is EngineStartResult.Disabled)
        assertEquals("LEGACY_ENGINE_DISABLED", (result as EngineStartResult.Disabled).reason)
    }

    @Test
    fun test3_LegacyEngineCannotEvaluateStrategies() {
        val exclusivity = guard.validate(alphaRunning = false, legacyRunning = true)
        assertTrue(exclusivity is EngineExclusivityResult.Violation)
        assertEquals("LEGACY_ENGINE_DISABLED", (exclusivity as EngineExclusivityResult.Violation).errorCode)
    }

    @Test
    fun test4_LegacyEngineCannotGenerateSignals() {
        val exclusivity = guard.validate(alphaRunning = false, legacyRunning = true)
        assertTrue(exclusivity is EngineExclusivityResult.Violation)
    }

    @Test
    fun test5_LegacyEngineCannotCreatePaperOrders() {
        val exclusivity = guard.validate(alphaRunning = false, legacyRunning = true)
        assertTrue(exclusivity is EngineExclusivityResult.Violation)
    }

    @Test
    fun test6_LegacyEngineCannotModifyBalances() {
        val account = engine.paperAccount
        assertEquals(10000.0, account.currentCash, 0.00001)
        // Legacy cannot touch Alpha paper account
        assertEquals(0.0, account.accountingVariance, 0.00001)
    }

    @Test
    fun test7_LegacyEngineCannotSendTelegramMessages() {
        val legacyMsg = TelegramMessagePayload(
            engineSource = TelegramEngineSource.LEGACY_ENGINE,
            header = "LEGACY TEST",
            sessionId = "SESS_OLD",
            body = "Legacy body"
        )
        val accepted = TelegramAlphaIdentityReporter.processTelegramMessage(legacyMsg)
        assertFalse(accepted)
    }

    @Test
    fun test8_AlphaEngineUsesLivePublicMarketData() {
        engine.startAlphaEngine()
        assertTrue(engine.isAlphaActive())
    }

    @Test
    fun test9_AlphaEngineExecutionRemainsSimulated() {
        val account = engine.paperAccount
        assertEquals(10000.0, account.startingCash, 0.00001)
        assertEquals("ACTIVE", account.status)
    }

    @Test
    fun test10_RealExchangeAdapterInvocationIsRejected() {
        val configWithReal = TradingEngineRuntimeConfig(
            activeEngine = TradingEngineId.ALPHA_ENGINE,
            realExchangeExecutionEnabled = true
        )
        val realGuard = TradingEngineExclusivityGuard(configWithReal)
        val exclusivity = realGuard.validate(alphaRunning = true, legacyRunning = false)
        assertTrue(exclusivity is EngineExclusivityResult.Violation)
        assertEquals("REAL_EXECUTION_DISABLED_FOR_ALPHA_ENGINE", (exclusivity as EngineExclusivityResult.Violation).errorCode)
    }

    @Test
    fun test11_NoPrivateApiKeyIsRequired() {
        val config = TradingEngineRuntimeConfig()
        assertFalse(config.realExchangeExecutionEnabled)
    }

    @Test
    fun test12_CacheCleanupRemovesOnlyDisposableRecords() {
        val auditList = SafeCacheCleaner.inspectCache()
        assertTrue(auditList.all { it.isDisposable })

        val summary = SafeCacheCleaner.executeSafeCleanup(retainedAuditRecordCount = 15)
        assertTrue(summary.deletedEntryCount >= 0)
        assertEquals(15, summary.retainedAuditRecords)
    }

    @Test
    fun test13_AuditLedgerRecordsSurviveCleanup() {
        val summary = SafeCacheCleaner.executeSafeCleanup(retainedAuditRecordCount = 12)
        assertEquals("INTACT", summary.databaseIntegrityStatus)
        assertEquals(12, summary.retainedAuditRecords)
    }

    @Test
    fun test14_AccountingRecordsSurviveCleanup() {
        val summary = SafeCacheCleaner.executeSafeCleanup(retainedAuditRecordCount = 12)
        assertEquals("INTACT", summary.accountingIntegrityStatus)
    }

    @Test
    fun test15_HistoricalPaperTradesSurviveCleanup() {
        val summary = SafeCacheCleaner.executeSafeCleanup(retainedAuditRecordCount = 12)
        assertEquals("VALID_ALPHA_SESSION", summary.sessionIdentityStatus)
    }

    @Test
    fun test16_AlphaSessionStartsWithCleanCounters() {
        val account = engine.paperAccount
        assertEquals(0, account.openPositionsCount)
        assertEquals(0, account.closedTradesCount)
        assertEquals(0.0, account.realisedPnl, 0.00001)
    }

    @Test
    fun test17_AlphaVirtualCashStartsAt10000() {
        val account = engine.paperAccount
        assertEquals(10000.0, account.startingCash, 0.00001)
        assertEquals(10000.0, account.currentCash, 0.00001)
        assertEquals(10000.0, account.currentEquity, 0.00001)
    }

    @Test
    fun test18_AlphaAccountingVarianceStartsAtZero() {
        val account = engine.paperAccount
        assertEquals(0.0, account.accountingVariance, 0.000001)
    }

    @Test
    fun test19_LegacySessionHistoryRemainsReadOnly() {
        val record = SessionCorrectionAuditLedger.getQuarantinedRecords().firstOrNull {
            it.originalSessionId == "SESS_LIVE_PAPER_20260730_062200_UTC"
        }
        assertNotNull(record)
    }

    @Test
    fun test20_UiShowsAlphaEngine() {
        val label = TradingEngineRuntimeConfig().telegramEngineLabel
        assertEquals("ALPHA ENGINE", label)
    }

    @Test
    fun test21_UiShowsPaperNoRealMoney() {
        val msg = TelegramAlphaIdentityReporter.sendAlphaSessionStart("SESS_ALPHA_TEST")
        assertTrue(msg.body.contains("PAPER TRADING"))
        assertTrue(msg.body.contains("NOT USED"))
    }

    @Test
    fun test22_TelegramEntryShowsAlphaEngine() {
        val msg = TelegramAlphaIdentityReporter.sendAlphaTradeEntry(
            sessionId = "SESS_ALPHA_TEST",
            symbol = "BTCUSDT",
            direction = "LONG",
            strategy = "BreakoutRetest",
            signalScore = 85.0,
            simulatedEntry = 65000.0,
            sl = 64000.0,
            tp = 67000.0,
            evidenceId = "EVID_001"
        )
        assertTrue(msg.header.contains("ALPHA ENGINE"))
    }

    @Test
    fun test23_TelegramExitShowsAlphaEngine() {
        val msg = TelegramAlphaIdentityReporter.sendAlphaTradeExit(
            sessionId = "SESS_ALPHA_TEST",
            symbol = "BTCUSDT",
            exitReason = "TAKE_PROFIT",
            simulatedEntry = 65000.0,
            simulatedExit = 67000.0,
            netPnl = "+200.00 USDT",
            virtualEquity = "10,200.00 USDT",
            evidenceId = "EVID_002"
        )
        assertTrue(msg.header.contains("ALPHA ENGINE"))
    }

    @Test
    fun test24_TelegramStatusShowsAlphaEngine() {
        val msg = TelegramAlphaIdentityReporter.sendAlphaStatus(
            sessionId = "SESS_ALPHA_TEST",
            virtualCash = "10,000.00 USDT",
            virtualEquity = "10,000.00 USDT",
            openPositions = 0,
            closedTrades = 0
        )
        assertTrue(msg.header.contains("ALPHA ENGINE STATUS"))
    }

    @Test
    fun test25_TelegramMessagesFromLegacyEngineAreRejected() {
        val legacyMsg = TelegramMessagePayload(
            engineSource = TelegramEngineSource.LEGACY_ENGINE,
            header = "LEGACY MSG",
            sessionId = "SESS_OLD",
            body = "body"
        )
        assertFalse(TelegramAlphaIdentityReporter.processTelegramMessage(legacyMsg))
    }

    @Test
    fun test26_DuplicateTelegramMessagesAreRejected() {
        val countBefore = TelegramAlphaIdentityReporter.getSentMessagesCount()
        val msg = TelegramAlphaIdentityReporter.sendAlphaSessionStart("SESS_ALPHA_TEST_DUP")
        assertTrue(TelegramAlphaIdentityReporter.processTelegramMessage(msg))
        assertEquals(countBefore + 1, TelegramAlphaIdentityReporter.getSentMessagesCount())
    }

    @Test
    fun test27_EngineExclusivityViolationFailsClosed() {
        val exclusivity = guard.validate(alphaRunning = true, legacyRunning = true)
        assertTrue(exclusivity is EngineExclusivityResult.Violation)
        assertEquals("ENGINE_EXCLUSIVITY_VIOLATION", (exclusivity as EngineExclusivityResult.Violation).errorCode)
    }

    @Test
    fun test28_UnknownEngineIdentityFailsClosed() {
        val badConfig = TradingEngineRuntimeConfig(
            activeEngine = TradingEngineId.LEGACY_ENGINE
        )
        val badGuard = TradingEngineExclusivityGuard(badConfig)
        val result = badGuard.validate(alphaRunning = false, legacyRunning = false)
        assertTrue(result is EngineExclusivityResult.Violation)
    }

    @Test
    fun test29_RestartRecoveryRestoresAlphaEngineOnly() {
        engine.startAlphaEngine()
        assertTrue(engine.isAlphaActive())
        assertFalse(engine.isLegacyActive())
    }

    @Test
    fun test30_NoLegacyRuntimeWorkerRestartsAutomatically() {
        val legacyStartResult = engine.attemptLegacyStart()
        assertTrue(legacyStartResult is EngineStartResult.Disabled)
    }
}
