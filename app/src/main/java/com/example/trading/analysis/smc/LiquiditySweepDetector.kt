package com.example.trading.analysis.smc

import com.example.trading.analysis.Candle
import com.example.trading.analysis.Timeframe

class LiquiditySweepDetector {

    fun detectSweeps(
        candles: List<Candle>,
        pools: List<LiquidityPool>,
        timeframe: Timeframe = Timeframe.M15,
        minExcursionAtrFraction: Double = 0.05,
        maxDeepSweepAtrMultiple: Double = 3.0,
        atr: Double = 0.0
    ): List<LiquiditySweep> {
        if (candles.isEmpty() || pools.isEmpty()) return emptyList()

        val sweeps = mutableListOf<LiquiditySweep>()
        val minExcursion = if (atr > 0.0) atr * minExcursionAtrFraction else 0.0

        for (pool in pools) {
            if (pool.isSwept || pool.isInvalidated) continue

            // Look for sweep candidate in candles after the pool's last touch timestamp
            val candidateCandles = candles.filter { it.timestamp >= pool.lastTouchTimestamp }

            for (i in candidateCandles.indices) {
                val candle = candidateCandles[i]

                if (pool.type == LiquidityType.SELL_SIDE) {
                    // Bullish setup: Price breaks below pool priceLevel
                    if (candle.low < pool.priceLevel - minExcursion) {
                        val excursion = pool.priceLevel - candle.low
                        val isDeepSweep = atr > 0.0 && excursion > (maxDeepSweepAtrMultiple * atr)

                        // Check if reclaimed in this candle or within next few candles
                        val reclaimed = candle.close >= pool.priceLevel || candle.close >= pool.zoneLow

                        if (reclaimed) {
                            val sweepType = when {
                                candle.close > pool.priceLevel && candle.open > pool.priceLevel -> SweepType.WICK_SWEEP
                                isDeepSweep -> SweepType.DEEP_SWEEP
                                candle.close >= pool.zoneLow -> SweepType.CLOSE_AND_RECLAIM
                                else -> SweepType.WICK_SWEEP
                            }

                            sweeps.add(
                                LiquiditySweep(
                                    id = "SWEEP_SSL_${pool.id}_${candle.timestamp}",
                                    pool = pool.copy(isSwept = true),
                                    sweepType = sweepType,
                                    sweepCandleTimestamp = candle.timestamp,
                                    reclaimCandleTimestamp = candle.timestamp,
                                    extremePrice = candle.low,
                                    isConfirmed = true,
                                    timeframe = timeframe
                                )
                            )
                            break // Found sweep for this pool
                        } else if (candle.close < pool.priceLevel - (atr * 2.0)) {
                            // Failed reclaim / clean break down
                            sweeps.add(
                                LiquiditySweep(
                                    id = "FAILED_SSL_${pool.id}_${candle.timestamp}",
                                    pool = pool,
                                    sweepType = SweepType.FAILED_SWEEP,
                                    sweepCandleTimestamp = candle.timestamp,
                                    reclaimCandleTimestamp = candle.timestamp,
                                    extremePrice = candle.low,
                                    isConfirmed = false,
                                    timeframe = timeframe
                                )
                            )
                        }
                    }
                } else if (pool.type == LiquidityType.BUY_SIDE) {
                    // Bearish setup: Price breaks above pool priceLevel
                    if (candle.high > pool.priceLevel + minExcursion) {
                        val excursion = candle.high - pool.priceLevel
                        val isDeepSweep = atr > 0.0 && excursion > (maxDeepSweepAtrMultiple * atr)

                        // Check if reclaimed (closed back below)
                        val reclaimed = candle.close <= pool.priceLevel || candle.close <= pool.zoneHigh

                        if (reclaimed) {
                            val sweepType = when {
                                candle.close < pool.priceLevel && candle.open < pool.priceLevel -> SweepType.WICK_SWEEP
                                isDeepSweep -> SweepType.DEEP_SWEEP
                                candle.close <= pool.zoneHigh -> SweepType.CLOSE_AND_RECLAIM
                                else -> SweepType.WICK_SWEEP
                            }

                            sweeps.add(
                                LiquiditySweep(
                                    id = "SWEEP_BSL_${pool.id}_${candle.timestamp}",
                                    pool = pool.copy(isSwept = true),
                                    sweepType = sweepType,
                                    sweepCandleTimestamp = candle.timestamp,
                                    reclaimCandleTimestamp = candle.timestamp,
                                    extremePrice = candle.high,
                                    isConfirmed = true,
                                    timeframe = timeframe
                                )
                            )
                            break // Found sweep for this pool
                        } else if (candle.close > pool.priceLevel + (atr * 2.0)) {
                            sweeps.add(
                                LiquiditySweep(
                                    id = "FAILED_BSL_${pool.id}_${candle.timestamp}",
                                    pool = pool,
                                    sweepType = SweepType.FAILED_SWEEP,
                                    sweepCandleTimestamp = candle.timestamp,
                                    reclaimCandleTimestamp = candle.timestamp,
                                    extremePrice = candle.high,
                                    isConfirmed = false,
                                    timeframe = timeframe
                                )
                            )
                        }
                    }
                }
            }
        }

        return sweeps
    }
}
