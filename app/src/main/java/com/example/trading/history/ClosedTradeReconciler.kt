package com.example.trading.history

import com.example.data.ClosedTradeEntity
import kotlin.math.abs

sealed class AccountingReconciliationResult {
    data class Success(
        val totalNetPnlUsdt: Double,
        val expectedBalanceUsdt: Double,
        val actualBalanceUsdt: Double,
        val varianceUsdt: Double
    ) : AccountingReconciliationResult()

    data class Violation(
        val errorCode: String = "ACCOUNTING_INTEGRITY_VIOLATION",
        val totalNetPnlUsdt: Double,
        val expectedBalanceUsdt: Double,
        val actualBalanceUsdt: Double,
        val varianceUsdt: Double,
        val message: String
    ) : AccountingReconciliationResult()
}

class AccountingIntegrityException(
    val errorCode: String,
    val varianceUsdt: Double,
    override val message: String
) : Exception(message)

object ClosedTradeReconciler {
    const val DEFAULT_TOLERANCE_USDT = 0.001

    fun reconcileAccount(
        initialBalanceUsdt: Double,
        currentBalanceUsdt: Double,
        closedTrades: List<ClosedTradeEntity>,
        toleranceUsdt: Double = DEFAULT_TOLERANCE_USDT
    ): AccountingReconciliationResult {
        val totalNetPnl = closedTrades.sumOf { it.netPnlUsdt }
        val expectedBalance = initialBalanceUsdt + totalNetPnl
        val variance = abs(currentBalanceUsdt - expectedBalance)

        return if (variance <= toleranceUsdt) {
            AccountingReconciliationResult.Success(
                totalNetPnlUsdt = totalNetPnl,
                expectedBalanceUsdt = expectedBalance,
                actualBalanceUsdt = currentBalanceUsdt,
                varianceUsdt = variance
            )
        } else {
            AccountingReconciliationResult.Violation(
                totalNetPnlUsdt = totalNetPnl,
                expectedBalanceUsdt = expectedBalance,
                actualBalanceUsdt = currentBalanceUsdt,
                varianceUsdt = variance,
                message = "Accounting variance ($variance USDT) exceeds tolerance ($toleranceUsdt USDT). Expected $expectedBalance USDT, found $currentBalanceUsdt USDT."
            )
        }
    }

    fun verifyAndThrowOnViolation(
        initialBalanceUsdt: Double,
        currentBalanceUsdt: Double,
        closedTrades: List<ClosedTradeEntity>,
        toleranceUsdt: Double = DEFAULT_TOLERANCE_USDT
    ) {
        val result = reconcileAccount(initialBalanceUsdt, currentBalanceUsdt, closedTrades, toleranceUsdt)
        if (result is AccountingReconciliationResult.Violation) {
            throw AccountingIntegrityException(
                errorCode = result.errorCode,
                varianceUsdt = result.varianceUsdt,
                message = result.message
            )
        }
    }
}
