package com.example.trading.backtest

import com.example.trading.analysis.Timeframe

class DataIntegrityValidator {

    fun validateCandleSeries(
        candles: List<HistoricalCandle>,
        gapPolicy: DataGapPolicy = DataGapPolicy.INSERT_SYNTHETIC_FLAT_CANDLE
    ): Pair<List<HistoricalCandle>, List<DataQualityIssue>> {
        if (candles.isEmpty()) {
            return Pair(emptyList(), listOf(
                DataQualityIssue(DataSeverity.FATAL, "UNKNOWN", Timeframe.M5, 0L, "Dataset is completely empty", false, "REJECT", false)
            ))
        }

        val issues = mutableListOf<DataQualityIssue>()
        val validated = mutableListOf<HistoricalCandle>()

        var previous: HistoricalCandle? = null

        for (candle in candles) {
            // Check impossible OHLC
            if (candle.high < candle.low || candle.high < candle.open || candle.high < candle.close || candle.low > candle.open || candle.low > candle.close) {
                issues.add(DataQualityIssue(DataSeverity.ERROR, candle.symbol, candle.timeframe, candle.openTime, "Impossible OHLC values: H=${candle.high}, L=${candle.low}, O=${candle.open}, C=${candle.close}", true, "DISCARD", true))
                continue
            }

            if (candle.open <= 0.0 || candle.close <= 0.0) {
                issues.add(DataQualityIssue(DataSeverity.ERROR, candle.symbol, candle.timeframe, candle.openTime, "Non-positive price detected: O=${candle.open}, C=${candle.close}", true, "DISCARD", true))
                continue
            }

            // Check duplicate or out-of-order timestamp
            if (previous != null) {
                if (candle.openTime == previous.openTime) {
                    issues.add(DataQualityIssue(DataSeverity.WARNING, candle.symbol, candle.timeframe, candle.openTime, "Duplicate timestamp detected", true, "SKIP_DUPLICATE", true))
                    continue
                } else if (candle.openTime < previous.openTime) {
                    issues.add(DataQualityIssue(DataSeverity.ERROR, candle.symbol, candle.timeframe, candle.openTime, "Out-of-order candle timestamp", true, "REORDER", true))
                }
            }

            previous = candle
            validated.add(candle)
        }

        return Pair(validated.sortedBy { it.openTime }, issues)
    }
}
