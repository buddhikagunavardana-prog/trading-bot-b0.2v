package com.example.trading.paper

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PaperTradingSessionControllerTest {

    private lateinit var controller: PaperTradingSessionController

    @Before
    fun setUp() {
        controller = PaperTradingSessionController()
    }

    @Test
    fun testLifecycleTransitions() {
        assertEquals(PaperSessionState.STOPPED, controller.sessionState.value)

        controller.startSession()
        assertEquals(PaperSessionState.WARMING_UP, controller.sessionState.value)

        controller.onWarmupComplete()
        assertEquals(PaperSessionState.RUNNING, controller.sessionState.value)

        controller.pauseSession()
        assertEquals(PaperSessionState.PAUSED, controller.sessionState.value)

        controller.resumeSession()
        assertEquals(PaperSessionState.RUNNING, controller.sessionState.value)

        controller.stopSession()
        assertEquals(PaperSessionState.STOPPED, controller.sessionState.value)
    }

    @Test
    fun testKillSwitchEnforcement() {
        controller.startSession()
        controller.onWarmupComplete()
        assertEquals(PaperSessionState.RUNNING, controller.sessionState.value)

        controller.activateKillSwitch()
        assertEquals(PaperSessionState.KILL_SWITCHED, controller.sessionState.value)

        // Resume should be blocked when kill switch active
        controller.resumeSession()
        assertEquals(PaperSessionState.KILL_SWITCHED, controller.sessionState.value)

        controller.deactivateKillSwitch()
        assertEquals(PaperSessionState.RUNNING, controller.sessionState.value)
    }
}
