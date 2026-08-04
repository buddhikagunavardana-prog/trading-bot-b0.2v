package com.example.trading.backtest

import com.example.trading.analysis.Timeframe
import com.example.trading.backtest.execution.SameCandleAmbiguityPolicy
import com.example.trading.backtest.execution.FillPolicy

data class DatasetConfig(
    val symbols: List<String> = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT"),
    val startTimeMs: Long = 1704067200000L, // Jan 1 2024
    val endTimeMs: Long = 1711929600000L,   // Apr 1 2024
    val baseTimeframe: Timeframe = Timeframe.M5,
    val requiredTimeframes: List<Timeframe> = listOf(Timeframe.M5, Timeframe.M15, Timeframe.H1),
    val dataSource: String = "HISTORICAL_REPLAY",
    val gapPolicy: DataGapPolicy = DataGapPolicy.INSERT_SYNTHETIC_FLAT_CANDLE,
    val timezonePolicy: String = "UTC"
)

data class AccountConfig(
    val initialBalance: Double = 10000.0,
    val quoteCurrency: String = "USDT",
    val riskPerTradePercent: Double = 1.0,
    val allocationCapPercent: Double = 20.0,
    val maxOpenPositions: Int = 3,
    val maxPortfolioRiskPercent: Double = 5.0,
    val leverage: Double = 1.0,
    val marginMode: String = "ISOLATED",
    val compounding: Boolean = false,
    val allowFractionalQuantity: Boolean = true
)

data class ExecutionConfig(
    val fillPolicy: FillPolicy = FillPolicy.SIGNAL_CANDLE_CLOSE,
    val sameCandleAmbiguityPolicy: SameCandleAmbiguityPolicy = SameCandleAmbiguityPolicy.STOP_FIRST,
    val makerFeeBps: Double = 2.0,
    val takerFeeBps: Double = 5.0,
    val fixedSpreadBps: Double = 3.0,
    val fixedSlippageBps: Double = 2.0,
    val fundingRateIntervalHours: Int = 8,
    val orderLatencyMs: Long = 100L,
    val pricePrecision: Int = 2,
    val quantityPrecision: Int = 4,
    val minQuantity: Double = 0.0001,
    val minNotional: Double = 10.0
)

data class ValidationSplitConfig(
    val trainRatio: Double = 0.60,
    val validationRatio: Double = 0.20,
    val outOfSampleRatio: Double = 0.20,
    val enableWalkForward: Boolean = true,
    val minTradesRequired: Int = 20,
    val purgeGapCandles: Int = 12,
    val embargoCandles: Int = 12,
    val isDeterministicMode: Boolean = true,
    val randomSeed: Long = 42L
)

data class BacktestConfig(
    val id: String = "BT_CONFIG_DEFAULT",
    val datasetConfig: DatasetConfig = DatasetConfig(),
    val accountConfig: AccountConfig = AccountConfig(),
    val executionConfig: ExecutionConfig = ExecutionConfig(),
    val validationConfig: ValidationSplitConfig = ValidationSplitConfig(),
    val enabledStrategyIds: Set<String> = setOf(
        "baseline_trend_follow",
        "trend_pullback",
        "breakout_retest",
        "smc_liquidity_sweep",
        "range_reversal",
        "momentum_continuation"
    )
) {
    fun calculateConfigHash(): String {
        return "CFG_HASH_" + Math.abs((id + datasetConfig.toString() + accountConfig.toString() + executionConfig.toString()).hashCode()).toString(16)
    }
}
