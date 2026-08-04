package com.example.trading.risk

import com.example.trading.strategy.SignalDirection

object PositionSizer {

    private fun logI(tag: String, msg: String) {
        try {
            android.util.Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    private fun logW(tag: String, msg: String) {
        try {
            android.util.Log.w(tag, msg)
        } catch (_: Throwable) {
            println("WARN: [$tag] $msg")
        }
    }

    private fun logE(tag: String, msg: String) {
        try {
            android.util.Log.e(tag, msg)
        } catch (_: Throwable) {
            println("ERROR: [$tag] $msg")
        }
    }

    fun calculatePositionSize(
        accountEquityUsdt: Double,
        riskPercent: Double,
        entryPrice: Double,
        stopLossPrice: Double,
        direction: SignalDirection = SignalDirection.LONG,
        leverage: Double = 1.0,
        symbol: String = "UNKNOWN"
    ): Double {
        val paperEquity = if (accountEquityUsdt > 0.0) accountEquityUsdt else 10000.0
        val availableBalance = paperEquity
        val validRiskPercent = if (riskPercent > 0.0) riskPercent else 2.0
        val riskAmount = paperEquity * (validRiskPercent / 100.0)

        val directionMultiplier = if (direction == SignalDirection.LONG) 1.0 else -1.0

        // If SL is on wrong side or invalid, return 0.0 cleanly
        val invalidSide = when (direction) {
            SignalDirection.LONG -> stopLossPrice >= entryPrice
            SignalDirection.SHORT -> stopLossPrice <= entryPrice
            SignalDirection.NEUTRAL -> true
        }

        if (paperEquity <= 0.0 || entryPrice <= 0.0 || stopLossPrice <= 0.0 || invalidSide) {
            logW("PositionSizer", "Invalid inputs or wrong-side SL for $symbol ($direction): equity=$paperEquity, entry=$entryPrice, sl=$stopLossPrice")
            return 0.0
        }

        val stopDistance = if (direction == SignalDirection.LONG) {
            entryPrice - stopLossPrice
        } else {
            stopLossPrice - entryPrice
        }

        if (stopDistance <= 0.0) return 0.0

        val units = riskAmount / stopDistance
        val quantity = Math.round(units * 10000.0) / 10000.0
        val notionalValue = entryPrice * quantity

        // Comprehensive Variable Log
        logI(
            "PositionSizerAudit",
            "POSITION_SIZING_AUDIT | symbol=$symbol, direction=$direction, paperEquity=$paperEquity, availableBalance=$availableBalance, riskPercent=$validRiskPercent, riskAmount=$riskAmount, entryPrice=$entryPrice, stopLossPrice=$stopLossPrice, stopDistance=$stopDistance, quantity=$quantity, notionalValue=$notionalValue, leverage=$leverage, directionMultiplier=$directionMultiplier"
        )

        // Runtime Assertion for valid setup: quantity > 0, notional > 0, riskAmount >= 0
        if (quantity <= 0.0 || notionalValue <= 0.0 || riskAmount < 0.0) {
            val errorMsg = "POSITION_SIZE_INVARIANT_VIOLATION: invalid calculated position size for valid trade setup $symbol ($direction) -> quantity=$quantity, notional=$notionalValue, riskAmount=$riskAmount. Diagnostics: paperEquity=$paperEquity, entryPrice=$entryPrice, stopLossPrice=$stopLossPrice, stopDistance=$stopDistance, leverage=$leverage, directionMultiplier=$directionMultiplier"
            logE("PositionSizerAudit", errorMsg)
            throw IllegalStateException(errorMsg)
        }

        return quantity
    }

    fun calculateRiskAmount(
        entryPrice: Double,
        stopLossPrice: Double,
        positionUnits: Double
    ): Double {
        val priceRiskPerUnit = Math.abs(entryPrice - stopLossPrice)
        return Math.round(priceRiskPerUnit * positionUnits * 100.0) / 100.0
    }

    fun calculateRiskRewardRatio(
        entryPrice: Double,
        stopLossPrice: Double,
        takeProfitPrice: Double
    ): Double {
        val risk = Math.abs(entryPrice - stopLossPrice)
        val reward = Math.abs(takeProfitPrice - entryPrice)
        if (risk <= 0.0) return 0.0
        return Math.round((reward / risk) * 100.0) / 100.0
    }
}

