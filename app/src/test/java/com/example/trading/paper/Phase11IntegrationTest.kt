package com.example.trading.paper

import com.example.trading.analysis.MultiTimeframeCandleAggregator
import com.example.trading.validation.SymbolMetadataManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Phase11IntegrationTest {

    private lateinit var sessionController: PaperTradingSessionController
    private lateinit var symbolManager: SymbolMetadataManager
    private lateinit var candleAggregator: MultiTimeframeCandleAggregator
    private lateinit var soakMonitor: SoakTestMonitor
    private lateinit var reconciler: PaperAccountReconciler

    @Before
    fun setUp() {
        sessionController = PaperTradingSessionController()
        symbolManager = SymbolMetadataManager()
        candleAggregator = MultiTimeframeCandleAggregator()
        soakMonitor = SoakTestMonitor()
        reconciler = PaperAccountReconciler()
    }

    @Test
    fun testPhase11EndToEndPipelineIntegrity() {
        // 1. Session Start
        sessionController.startSession()
        assertEquals(PaperSessionState.WARMING_UP, sessionController.sessionState.value)

        // 2. Symbol Universe Verification
        val symbols = symbolManager.getActiveSymbols()
        assertEquals(10, symbols.size)

        // 3. Warmup & Session Running Transition
        sessionController.onWarmupComplete()
        assertEquals(PaperSessionState.RUNNING, sessionController.sessionState.value)

        // 4. Soak Monitor Metrics
        soakMonitor.updateMetrics(activePositions = 0, totalExecuted = 0)
        val report = soakMonitor.report.value
        assertTrue(report.isMemoryBounded)

        // 5. Account Reconciliation
        val reconcil = reconciler.reconcileAccount(10000.0, 10000.0, emptyList(), emptyList())
        assertTrue(reconcil.isBalanced)
    }
}
