package com.example.trading.analysis

enum class Timeframe(val label: String, val minutes: Int) {
    M1("1m", 1),
    M5("5m", 5),
    M15("15m", 15),
    H1("1h", 60),
    H4("4h", 240),
    D1("1d", 1440)
}
