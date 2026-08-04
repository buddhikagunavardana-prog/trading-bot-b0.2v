package com.example.trading.history

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class TradeDirection {
    LONG,
    SHORT
}

enum class TradeResultType {
    PROFIT,
    LOSS,
    BREAKEVEN
}

enum class PositionCloseReason {
    TAKE_PROFIT,
    STOP_LOSS,
    TRAILING_STOP,
    STRATEGY_EXIT,
    MANUAL_CLOSE,
    RISK_KILL_SWITCH,
    SESSION_SHUTDOWN,
    LIQUIDATION_SIMULATION,
    DATA_SAFETY_CLOSE,
    UNKNOWN;

    companion object {
        fun fromString(value: String): PositionCloseReason {
            val upper = value.uppercase()
            return when {
                upper.contains("TP") || upper.contains("TAKE_PROFIT") -> TAKE_PROFIT
                upper.contains("SL") || upper.contains("STOP_LOSS") -> STOP_LOSS
                upper.contains("TRAILING") -> TRAILING_STOP
                upper.contains("STRATEGY") -> STRATEGY_EXIT
                upper.contains("MANUAL") -> MANUAL_CLOSE
                upper.contains("RISK") || upper.contains("KILL_SWITCH") -> RISK_KILL_SWITCH
                upper.contains("SHUTDOWN") -> SESSION_SHUTDOWN
                upper.contains("LIQUIDATION") -> LIQUIDATION_SIMULATION
                upper.contains("SAFETY") -> DATA_SAFETY_CLOSE
                else -> UNKNOWN
            }
        }
    }
}

data class ClosedTradeResult(
    val tradeId: String,
    val positionId: String,
    val sessionId: String,

    val symbol: String,
    val direction: TradeDirection,

    val openedAtEpochMs: Long,
    val closedAtEpochMs: Long,
    val holdingDurationMs: Long,

    val entryPrice: Double,
    val exitPrice: Double,
    val quantity: Double,
    val entryNotionalUsdt: Double,
    val allocatedCapitalUsdt: Double,

    val grossPnlUsdt: Double,
    val entryFeeUsdt: Double = 0.0,
    val exitFeeUsdt: Double = 0.0,
    val totalFeesUsdt: Double = 0.0,
    val fundingCostUsdt: Double = 0.0,
    val slippageCostUsdt: Double = 0.0,
    val netPnlUsdt: Double,

    val pnlPercentOnNotional: Double,
    val pnlPercentOnAllocatedCapital: Double,
    val rMultiple: Double? = null,

    val resultType: TradeResultType,
    val closeReason: PositionCloseReason,

    val stopLossPrice: Double? = null,
    val takeProfitPrice: Double? = null,
    val initialRiskUsdt: Double? = null,

    val alphaScoreAtEntry: Double? = null,
    val thresholdUsed: Double? = 75.0,
    val settingsVersion: Long? = 1L,
    val scoringModelVersion: String? = "v2.0_100pt_exact",
    val strategyId: String? = null,
    val marketRegimeAtEntry: String? = null,

    val providerId: String? = "BINANCE_FUTURES_SIM",
    val sourceOrigin: String? = "PAPER_ENGINE",

    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    fun formatOpenedAtUtc(): String = formatDateUtc(openedAtEpochMs)
    fun formatClosedAtUtc(): String = formatDateUtc(closedAtEpochMs)
    fun formatHoldingDuration(): String = formatDuration(holdingDurationMs)

    companion object {
        fun formatDateUtc(epochMs: Long): String {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date(epochMs))
        }

        fun formatDuration(durationMs: Long): String {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return when {
                hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
                minutes > 0 -> "${minutes}m ${seconds}s"
                else -> "${seconds}s"
            }
        }
    }
}
