package com.example.trading.paper

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PaperSessionState {
    STOPPED,
    STARTING,
    WARMING_UP,
    RUNNING,
    PAUSED,
    MARKET_DATA_STALE,
    RECONNECTING,
    RISK_LOCKED,
    KILL_SWITCHED,
    RECOVERING,
    STOPPING,
    ERROR
}

/**
 * Authoritative Paper Trading Session Controller for Phase 13.1.
 * Manages active session lifecycle states, warm-up transitions, controls, and recovery
 * with strict UTC time precision and canonical Session ID derivation.
 */
class PaperTradingSessionController(
    private val clock: Clock = Clock.systemUTC()
) {

    private val _sessionState = MutableStateFlow(PaperSessionState.STOPPED)
    val sessionState: StateFlow<PaperSessionState> = _sessionState.asStateFlow()

    private var _startInstant: Instant = clock.instant()
    val startInstant: Instant get() = _startInstant

    private val _sessionId = MutableStateFlow(TradingTimeCodec.generateCanonicalSessionId(_startInstant))
    val sessionId: StateFlow<String> = _sessionId.asStateFlow()

    val sessionStartEpochMillis: Long get() = _startInstant.toEpochMilli()

    private var isKillSwitchActive = false
    private var isRiskLockActive = false

    fun startSession(forcedInstant: Instant? = null) {
        if (_sessionState.value == PaperSessionState.RUNNING || _sessionState.value == PaperSessionState.WARMING_UP) {
            return // Idempotent start
        }
        _startInstant = forcedInstant ?: clock.instant()
        val newSessionId = TradingTimeCodec.generateCanonicalSessionId(_startInstant)

        require(TradingTimeCodec.validateSessionIdConsistency(newSessionId, _startInstant.toEpochMilli())) {
            "Generated Session ID $newSessionId is inconsistent with start epoch ${_startInstant.toEpochMilli()}"
        }

        _sessionId.value = newSessionId
        _sessionState.value = PaperSessionState.STARTING
        _sessionState.value = PaperSessionState.WARMING_UP
    }

    fun onWarmupComplete() {
        if (_sessionState.value == PaperSessionState.WARMING_UP) {
            _sessionState.value = PaperSessionState.RUNNING
        }
    }

    fun pauseSession() {
        if (_sessionState.value == PaperSessionState.RUNNING) {
            _sessionState.value = PaperSessionState.PAUSED
        }
    }

    fun resumeSession() {
        if (_sessionState.value == PaperSessionState.PAUSED && !isKillSwitchActive && !isRiskLockActive) {
            _sessionState.value = PaperSessionState.RUNNING
        }
    }

    fun stopSession() {
        _sessionState.value = PaperSessionState.STOPPING
        _sessionState.value = PaperSessionState.STOPPED
    }

    fun activateKillSwitch() {
        isKillSwitchActive = true
        _sessionState.value = PaperSessionState.KILL_SWITCHED
    }

    fun deactivateKillSwitch() {
        isKillSwitchActive = false
        if (_sessionState.value == PaperSessionState.KILL_SWITCHED) {
            _sessionState.value = PaperSessionState.RUNNING
        }
    }

    fun activateRiskLock() {
        isRiskLockActive = true
        _sessionState.value = PaperSessionState.RISK_LOCKED
    }

    fun updateMarketDataHealth(isHealthy: Boolean) {
        if (_sessionState.value == PaperSessionState.RUNNING && !isHealthy) {
            _sessionState.value = PaperSessionState.MARKET_DATA_STALE
        } else if (_sessionState.value == PaperSessionState.MARKET_DATA_STALE && isHealthy) {
            _sessionState.value = PaperSessionState.RUNNING
        }
    }

    fun triggerRecovery() {
        val prevState = _sessionState.value
        _sessionState.value = PaperSessionState.RECOVERING
        _sessionState.value = if (prevState == PaperSessionState.KILL_SWITCHED) PaperSessionState.KILL_SWITCHED else PaperSessionState.RUNNING
    }

    fun getEligibilityContext(): SessionEligibilityContext {
        return SessionEligibilityContext(
            sessionId = _sessionId.value,
            sessionStartEpoch = sessionStartEpochMillis,
            tradingMode = "PAPER"
        )
    }
}
