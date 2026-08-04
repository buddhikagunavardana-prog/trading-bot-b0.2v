package com.example.service.provider

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}

class ProviderCircuitBreaker(
    val providerId: String,
    private val threshold: Int = 3,
    private val cooldownMs: Long = 60000L
) {
    private val consecutiveFailures = AtomicInteger(0)
    private val lastStateChangeTimestamp = AtomicLong(System.currentTimeMillis())
    private val _state = AtomicReference(CircuitBreakerState.CLOSED)

    val state: CircuitBreakerState
        get() {
            val currentState = _state.get()
            if (currentState == CircuitBreakerState.OPEN) {
                val elapsed = System.currentTimeMillis() - lastStateChangeTimestamp.get()
                if (elapsed >= cooldownMs) {
                    if (_state.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN)) {
                        lastStateChangeTimestamp.set(System.currentTimeMillis())
                        return CircuitBreakerState.HALF_OPEN
                    }
                }
            }
            return _state.get()
        }

    fun canExecute(): Boolean {
        return when (state) {
            CircuitBreakerState.CLOSED -> true
            CircuitBreakerState.HALF_OPEN -> true
            CircuitBreakerState.OPEN -> false
        }
    }

    fun recordSuccess() {
        consecutiveFailures.set(0)
        if (_state.get() != CircuitBreakerState.CLOSED) {
            _state.set(CircuitBreakerState.CLOSED)
            lastStateChangeTimestamp.set(System.currentTimeMillis())
        }
    }

    fun recordFailure(failureType: ProviderFailureType) {
        if (failureType == ProviderFailureType.PROVIDER_REGION_BLOCKED) {
            // Immediately open circuit on HTTP 451 legal restriction
            _state.set(CircuitBreakerState.OPEN)
            lastStateChangeTimestamp.set(System.currentTimeMillis())
            consecutiveFailures.set(threshold)
            return
        }

        val count = consecutiveFailures.incrementAndGet()
        if (count >= threshold) {
            _state.set(CircuitBreakerState.OPEN)
            lastStateChangeTimestamp.set(System.currentTimeMillis())
        }
    }

    fun forceOpen() {
        _state.set(CircuitBreakerState.OPEN)
        lastStateChangeTimestamp.set(System.currentTimeMillis())
    }

    fun reset() {
        consecutiveFailures.set(0)
        _state.set(CircuitBreakerState.CLOSED)
        lastStateChangeTimestamp.set(System.currentTimeMillis())
    }
}
