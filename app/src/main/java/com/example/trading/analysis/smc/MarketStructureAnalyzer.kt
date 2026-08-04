package com.example.trading.analysis.smc

import com.example.trading.analysis.Candle
import com.example.trading.analysis.Timeframe

class MarketStructureAnalyzer {

    data class StructureAnalysisResult(
        val recentEvents: List<StructureEvent>,
        val currentBias: String, // "BULLISH", "BEARISH", "NEUTRAL"
        val lastBos: StructureEvent?,
        val lastChoch: StructureEvent?,
        val lastMss: StructureEvent?
    )

    fun analyzeStructure(
        candles: List<Candle>,
        confirmedSwings: List<ConfirmedSwing>,
        timeframe: Timeframe,
        useClosePriceForBreak: Boolean = true,
        minBreakDistanceAtr: Double = 0.0,
        atr: Double = 0.0
    ): StructureAnalysisResult {
        if (candles.isEmpty() || confirmedSwings.isEmpty()) {
            return StructureAnalysisResult(emptyList(), "NEUTRAL", null, null, null)
        }

        val events = mutableListOf<StructureEvent>()
        var activeDirection = "NEUTRAL"

        val swingHighs = confirmedSwings.filter { it.type == SwingType.SWING_HIGH }.sortedBy { it.candleTimestamp }
        val swingLows = confirmedSwings.filter { it.type == SwingType.SWING_LOW }.sortedBy { it.candleTimestamp }

        var lastBrokenHigh: ConfirmedSwing? = null
        var lastBrokenLow: ConfirmedSwing? = null

        // Iterate candles sequentially to detect causal structural breaks
        for (i in candles.indices) {
            val candle = candles[i]

            // Find confirmed swing highs that occurred prior to or at this candle
            val validHighs = swingHighs.filter { it.confirmationTimestamp <= candle.timestamp }
            val validLows = swingLows.filter { it.confirmationTimestamp <= candle.timestamp }

            val latestHigh = validHighs.lastOrNull { it != lastBrokenHigh }
            val latestLow = validLows.lastOrNull { it != lastBrokenLow }

            val breakDistanceThreshold = minBreakDistanceAtr * atr

            // Bullish break check
            if (latestHigh != null) {
                val evalPrice = if (useClosePriceForBreak) candle.close else candle.high
                if (evalPrice > latestHigh.price + breakDistanceThreshold) {
                    val isChoch = activeDirection == "BEARISH"
                    val type = if (isChoch) MarketStructureType.BULLISH_CHOCH else MarketStructureType.BULLISH_BOS
                    val explanation = if (isChoch) {
                        "Bullish CHOCH: Candle close (${candle.close}) broke above confirmed swing high (${latestHigh.price}) reversing bearish trend"
                    } else {
                        "Bullish BOS: Candle close (${candle.close}) broke above confirmed swing high (${latestHigh.price})"
                    }

                    val event = StructureEvent(
                        type = type,
                        brokenSwing = latestHigh,
                        triggerCandleTimestamp = candle.timestamp,
                        confirmationTimestamp = candle.timestamp,
                        breakPrice = evalPrice,
                        timeframe = timeframe,
                        isDisplacementConfirmed = candle.bodySize > (atr * 0.8),
                        explanation = explanation
                    )
                    events.add(event)
                    activeDirection = "BULLISH"
                    lastBrokenHigh = latestHigh
                }
            }

            // Bearish break check
            if (latestLow != null) {
                val evalPrice = if (useClosePriceForBreak) candle.close else candle.low
                if (evalPrice < latestLow.price - breakDistanceThreshold) {
                    val isChoch = activeDirection == "BULLISH"
                    val type = if (isChoch) MarketStructureType.BEARISH_CHOCH else MarketStructureType.BEARISH_BOS
                    val explanation = if (isChoch) {
                        "Bearish CHOCH: Candle close (${candle.close}) broke below confirmed swing low (${latestLow.price}) reversing bullish trend"
                    } else {
                        "Bearish BOS: Candle close (${candle.close}) broke below confirmed swing low (${latestLow.price})"
                    }

                    val event = StructureEvent(
                        type = type,
                        brokenSwing = latestLow,
                        triggerCandleTimestamp = candle.timestamp,
                        confirmationTimestamp = candle.timestamp,
                        breakPrice = evalPrice,
                        timeframe = timeframe,
                        isDisplacementConfirmed = candle.bodySize > (atr * 0.8),
                        explanation = explanation
                    )
                    events.add(event)
                    activeDirection = "BEARISH"
                    lastBrokenLow = latestLow
                }
            }
        }

        val lastBos = events.lastOrNull { it.type == MarketStructureType.BULLISH_BOS || it.type == MarketStructureType.BEARISH_BOS }
        val lastChoch = events.lastOrNull { it.type == MarketStructureType.BULLISH_CHOCH || it.type == MarketStructureType.BEARISH_CHOCH }
        val lastMss = events.lastOrNull { it.type == MarketStructureType.BULLISH_MSS || it.type == MarketStructureType.BEARISH_MSS }

        return StructureAnalysisResult(
            recentEvents = events,
            currentBias = activeDirection,
            lastBos = lastBos,
            lastChoch = lastChoch,
            lastMss = lastMss
        )
    }
}
