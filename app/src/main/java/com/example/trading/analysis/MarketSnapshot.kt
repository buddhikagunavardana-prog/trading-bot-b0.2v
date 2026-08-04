package com.example.trading.analysis

data class MarketSnapshot(
    val symbol: String,
    val timeframe: Timeframe,
    val candles: List<Candle>,
    val latestCandle: Candle,
    val indicators: IndicatorSnapshot,
    val timestamp: Long = latestCandle.timestamp
)

data class MultiTimeframeSnapshot(
    val symbol: String,
    val m5: MarketSnapshot?,
    val m15: MarketSnapshot?,
    val h1: MarketSnapshot? = null,
    val timestamp: Long = System.currentTimeMillis()
)
