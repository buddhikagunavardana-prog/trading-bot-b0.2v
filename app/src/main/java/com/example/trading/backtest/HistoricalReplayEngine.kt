package com.example.trading.backtest

class HistoricalReplayEngine(
    private val clock: BacktestClock = BacktestClock(),
    private val validator: DataIntegrityValidator = DataIntegrityValidator()
) {

    fun prepareEvaluationTimestamps(
        m5Candles: List<HistoricalCandle>,
        warmupCandlesCount: Int = 50
    ): List<Long> {
        if (m5Candles.size <= warmupCandlesCount) return emptyList()

        // Evaluation starts after warm-up candles
        val validM5Candles = m5Candles.sortedBy { it.openTime }.drop(warmupCandlesCount)
        return validM5Candles.map { it.closeTime }
    }
}
