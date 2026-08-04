package com.example.trading.backtest

class BacktestClock(initialTime: Long = 0L) {
    var currentTimeMs: Long = initialTime
        private set

    fun setTime(timestamp: Long) {
        require(timestamp >= currentTimeMs) { "BacktestClock cannot move backward in time. Current: $currentTimeMs, Target: $timestamp" }
        currentTimeMs = timestamp
    }

    fun advanceBy(durationMs: Long) {
        require(durationMs >= 0) { "Cannot advance clock by negative duration" }
        currentTimeMs += durationMs
    }
}
