package com.example.trading.strategy

import com.example.trading.analysis.smc.EntryZoneType
import com.example.trading.analysis.smc.OrderBlockZonePolicy
import com.example.trading.analysis.smc.SwingPolicy

enum class SmcStopLossPolicy {
    SWEEP_EXTREME,
    ORDER_BLOCK_BOUNDARY,
    STRUCTURE_BOUNDARY,
    ATR_BUFFERED_STRUCTURE,
    MOST_CONSERVATIVE,
    HYBRID
}

data class SmcLiquiditySweepConfig(
    val swingLeftBars: Int = 2,
    val swingRightBars: Int = 2,
    val swingPolicy: SwingPolicy = SwingPolicy.STRICT,
    val equalLevelToleranceAtrFraction: Double = 0.15,
    val minSweepExcursionAtrFraction: Double = 0.05,
    val maxDeepSweepAtrMultiple: Double = 3.0,
    val minDisplacementAtrMultiple: Double = 1.0,
    val orderBlockZonePolicy: OrderBlockZonePolicy = OrderBlockZonePolicy.HYBRID,
    val obMaxCandleDistance: Int = 10,
    val minFvgGapAtrFraction: Double = 0.1,
    val preferredEntryZoneType: EntryZoneType = EntryZoneType.HIGHEST_SCORE_ZONE,
    val stopLossPolicy: SmcStopLossPolicy = SmcStopLossPolicy.HYBRID,
    val atrSlBufferMultiple: Double = 0.5,
    val minRiskRewardRatio: Double = 1.5,
    val targetRiskRewardRatio: Double = 2.0,
    val maxSpreadPercent: Double = 0.3,
    val isCounterTrendAllowed: Boolean = false
)
