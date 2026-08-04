package com.example.trading.paper

enum class EventOrigin {
    LIVE_STREAM,
    REST_BOOTSTRAP,
    WARMUP,
    RECONNECT_BACKFILL,
    DATABASE_RECOVERY,
    SYNTHETIC_TEST,
    HISTORICAL_REPLAY
}
