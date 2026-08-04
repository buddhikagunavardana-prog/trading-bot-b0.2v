package com.example.trading.strategy

enum class SignalDirection {
    LONG,
    SHORT,
    NEUTRAL
}

enum class SignalDecision {
    REJECT,
    WATCHLIST,
    PAPER_TRADE,
    APPROVED
}

enum class NoTradeReason {
    INSUFFICIENT_DATA,
    STALE_DATA,
    INVALID_PRICE,
    INVALID_INDICATORS,
    UNSUPPORTED_REGIME,
    LOW_SIGNAL_SCORE,
    POOR_RISK_REWARD,
    SPREAD_TOO_HIGH,
    MAX_OPEN_POSITIONS,
    DAILY_LOSS_LIMIT,
    COOLDOWN_ACTIVE,
    CONFLICTING_TIMEFRAMES,
    RISK_ENGINE_REJECTED
}
