package com.example.trading.analysis.smc

import com.example.trading.analysis.Candle
import com.example.trading.analysis.Timeframe
import com.example.trading.strategy.SignalDirection

class OrderBlockDetector {

    fun detectOrderBlocks(
        candles: List<Candle>,
        structureEvents: List<StructureEvent>,
        timeframe: Timeframe,
        zonePolicy: OrderBlockZonePolicy = OrderBlockZonePolicy.HYBRID,
        maxCandleDistanceToBreak: Int = 10,
        atr: Double = 0.0
    ): List<OrderBlock> {
        if (candles.isEmpty() || structureEvents.isEmpty()) return emptyList()

        val orderBlocks = mutableListOf<OrderBlock>()

        for (event in structureEvents) {
            val breakIndex = candles.indexOfFirst { it.timestamp == event.triggerCandleTimestamp }
            if (breakIndex <= 0) continue

            val startIndex = (breakIndex - maxCandleDistanceToBreak).coerceAtLeast(0)
            val preBreakCandles = candles.subList(startIndex, breakIndex)

            if (event.type == MarketStructureType.BULLISH_BOS ||
                event.type == MarketStructureType.BULLISH_CHOCH ||
                event.type == MarketStructureType.BULLISH_MSS
            ) {
                // Find last bearish candle before the break
                val sourceCandle = preBreakCandles.lastOrNull { !it.isBullish } ?: preBreakCandles.lastOrNull()
                if (sourceCandle != null) {
                    val (top, bottom) = calculateZoneBoundaries(sourceCandle, SignalDirection.LONG, zonePolicy)
                    val ob = OrderBlock(
                        id = "OB_BULL_${sourceCandle.timestamp}",
                        direction = SignalDirection.LONG,
                        sourceCandle = sourceCandle,
                        timeframe = timeframe,
                        creationTimestamp = event.triggerCandleTimestamp,
                        topPrice = top,
                        bottomPrice = bottom,
                        zonePolicy = zonePolicy,
                        state = OrderBlockLifecycle.ACTIVE,
                        associatedStructureEvent = event
                    )
                    // Update mitigation state using post-break candles
                    val updatedOb = updateLifecycle(ob, candles.subList(breakIndex, candles.size), SignalDirection.LONG)
                    orderBlocks.add(updatedOb)
                }
            } else if (event.type == MarketStructureType.BEARISH_BOS ||
                event.type == MarketStructureType.BEARISH_CHOCH ||
                event.type == MarketStructureType.BEARISH_MSS
            ) {
                // Find last bullish candle before the break
                val sourceCandle = preBreakCandles.lastOrNull { it.isBullish } ?: preBreakCandles.lastOrNull()
                if (sourceCandle != null) {
                    val (top, bottom) = calculateZoneBoundaries(sourceCandle, SignalDirection.SHORT, zonePolicy)
                    val ob = OrderBlock(
                        id = "OB_BEAR_${sourceCandle.timestamp}",
                        direction = SignalDirection.SHORT,
                        sourceCandle = sourceCandle,
                        timeframe = timeframe,
                        creationTimestamp = event.triggerCandleTimestamp,
                        topPrice = top,
                        bottomPrice = bottom,
                        zonePolicy = zonePolicy,
                        state = OrderBlockLifecycle.ACTIVE,
                        associatedStructureEvent = event
                    )
                    val updatedOb = updateLifecycle(ob, candles.subList(breakIndex, candles.size), SignalDirection.SHORT)
                    orderBlocks.add(updatedOb)
                }
            }
        }

        return orderBlocks
    }

    private fun calculateZoneBoundaries(
        candle: Candle,
        direction: SignalDirection,
        policy: OrderBlockZonePolicy
    ): Pair<Double, Double> {
        return when (policy) {
            OrderBlockZonePolicy.FULL_CANDLE -> Pair(candle.high, candle.low)
            OrderBlockZonePolicy.BODY_ONLY -> {
                val top = Math.max(candle.open, candle.close)
                val bottom = Math.min(candle.open, candle.close)
                Pair(top, bottom)
            }
            OrderBlockZonePolicy.OPEN_TO_LOW_OR_HIGH -> {
                if (direction == SignalDirection.LONG) {
                    Pair(candle.open, candle.low)
                } else {
                    Pair(candle.high, candle.open)
                }
            }
            OrderBlockZonePolicy.HYBRID -> {
                val bodyTop = Math.max(candle.open, candle.close)
                if (direction == SignalDirection.LONG) {
                    Pair(bodyTop, candle.low)
                } else {
                    Pair(candle.high, Math.min(candle.open, candle.close))
                }
            }
        }
    }

    private fun updateLifecycle(
        ob: OrderBlock,
        postBreakCandles: List<Candle>,
        direction: SignalDirection
    ): OrderBlock {
        var state = OrderBlockLifecycle.ACTIVE
        var invalidationTs: Long? = null

        for (c in postBreakCandles) {
            if (c.timestamp <= ob.creationTimestamp) continue

            if (direction == SignalDirection.LONG) {
                // Close below bottom = Invalidation
                if (c.close < ob.bottomPrice) {
                    state = OrderBlockLifecycle.INVALIDATED
                    invalidationTs = c.timestamp
                    break
                }
                // Low enters zone = Mitigation
                if (c.low <= ob.topPrice && c.low >= ob.bottomPrice) {
                    state = OrderBlockLifecycle.PARTIALLY_MITIGATED
                }
            } else {
                // Close above top = Invalidation
                if (c.close > ob.topPrice) {
                    state = OrderBlockLifecycle.INVALIDATED
                    invalidationTs = c.timestamp
                    break
                }
                // High enters zone = Mitigation
                if (c.high >= ob.bottomPrice && c.high <= ob.topPrice) {
                    state = OrderBlockLifecycle.PARTIALLY_MITIGATED
                }
            }
        }

        return ob.copy(state = state, invalidationTimestamp = invalidationTs)
    }
}
