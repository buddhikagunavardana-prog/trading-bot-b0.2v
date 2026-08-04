package com.example.trading.analysis

data class IndicatorSnapshot(
    val sma20: Double = 0.0,
    val sma50: Double = 0.0,
    val sma200: Double = 0.0,
    val ema9: Double = 0.0,
    val ema21: Double = 0.0,
    val ema50: Double = 0.0,
    val ema200: Double = 0.0,
    val rsi: Double = 50.0,
    val adx: Double = 25.0,
    val atr: Double = 0.0,
    val atrPercent: Double = 0.0,
    val bbUpper: Double = 0.0,
    val bbMiddle: Double = 0.0,
    val bbLower: Double = 0.0,
    val volumeSma20: Double = 0.0,
    val supportPrice: Double = 0.0,
    val resistancePrice: Double = 0.0
)
