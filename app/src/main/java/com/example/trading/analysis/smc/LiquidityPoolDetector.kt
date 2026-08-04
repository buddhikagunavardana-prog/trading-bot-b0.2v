package com.example.trading.analysis.smc

import com.example.trading.analysis.Candle

class LiquidityPoolDetector {

    fun detectPools(
        candles: List<Candle>,
        confirmedSwings: List<ConfirmedSwing>,
        atr: Double = 0.0,
        equalLevelToleranceAtrFraction: Double = 0.15
    ): List<LiquidityPool> {
        if (confirmedSwings.isEmpty()) return emptyList()

        val pools = mutableListOf<LiquidityPool>()
        val tolerance = if (atr > 0.0) atr * equalLevelToleranceAtrFraction else 0.0005 * (candles.lastOrNull()?.close ?: 1.0)

        // Group swing highs for Buy-Side Liquidity
        val swingHighs = confirmedSwings.filter { it.type == SwingType.SWING_HIGH }.sortedBy { it.price }
        val highClusters = mutableListOf<MutableList<ConfirmedSwing>>()

        for (sh in swingHighs) {
            val cluster = highClusters.firstOrNull { c -> c.any { Math.abs(it.price - sh.price) <= tolerance } }
            if (cluster != null) {
                cluster.add(sh)
            } else {
                highClusters.add(mutableListOf(sh))
            }
        }

        for (cluster in highClusters) {
            val avgPrice = cluster.map { it.price }.average()
            val maxPrice = cluster.maxOf { it.price }
            val minPrice = cluster.minOf { it.price }
            val firstSeen = cluster.minOf { it.candleTimestamp }
            val lastTouch = cluster.maxOf { it.candleTimestamp }
            val touches = cluster.size
            val isEqualHighs = touches >= 2

            val poolId = "BSL_${(avgPrice * 100).toInt()}_${cluster.first().index}"
            val score = if (isEqualHighs) 15.0 + touches * 5.0 else 10.0

            pools.add(
                LiquidityPool(
                    id = poolId,
                    type = LiquidityType.BUY_SIDE,
                    priceLevel = maxPrice,
                    zoneHigh = maxPrice + tolerance,
                    zoneLow = minPrice - tolerance,
                    contributingSwings = cluster,
                    firstSeenTimestamp = firstSeen,
                    lastTouchTimestamp = lastTouch,
                    strengthScore = score,
                    isSwept = false,
                    isInvalidated = false
                )
            )
        }

        // Group swing lows for Sell-Side Liquidity
        val swingLows = confirmedSwings.filter { it.type == SwingType.SWING_LOW }.sortedBy { it.price }
        val lowClusters = mutableListOf<MutableList<ConfirmedSwing>>()

        for (sl in swingLows) {
            val cluster = lowClusters.firstOrNull { c -> c.any { Math.abs(it.price - sl.price) <= tolerance } }
            if (cluster != null) {
                cluster.add(sl)
            } else {
                lowClusters.add(mutableListOf(sl))
            }
        }

        for (cluster in lowClusters) {
            val avgPrice = cluster.map { it.price }.average()
            val maxPrice = cluster.maxOf { it.price }
            val minPrice = cluster.minOf { it.price }
            val firstSeen = cluster.minOf { it.candleTimestamp }
            val lastTouch = cluster.maxOf { it.candleTimestamp }
            val touches = cluster.size
            val isEqualLows = touches >= 2

            val poolId = "SSL_${(avgPrice * 100).toInt()}_${cluster.first().index}"
            val score = if (isEqualLows) 15.0 + touches * 5.0 else 10.0

            pools.add(
                LiquidityPool(
                    id = poolId,
                    type = LiquidityType.SELL_SIDE,
                    priceLevel = minPrice,
                    zoneHigh = maxPrice + tolerance,
                    zoneLow = minPrice - tolerance,
                    contributingSwings = cluster,
                    firstSeenTimestamp = firstSeen,
                    lastTouchTimestamp = lastTouch,
                    strengthScore = score,
                    isSwept = false,
                    isInvalidated = false
                )
            )
        }

        return pools
    }
}
