package com.example.trading.paper

data class CounterSanityResult(
    val timeframe: String,
    val expectedMin: Int,
    val expectedMax: Int,
    val actualCount: Int,
    val variance: Int,
    val confidence: Double,
    val isValid: Boolean,
    val warnings: List<String>
)

object CounterSanityValidator {

    fun validateM5Count(elapsedMinutes: Long, symbolCount: Int, actualCount: Int): CounterSanityResult {
        val maxPerSymbol = (elapsedMinutes / 5).toInt() + 1
        val expectedMax = maxPerSymbol * symbolCount
        val expectedMin = 0
        val warnings = mutableListOf<String>()

        var isValid = true
        if (actualCount > expectedMax) {
            isValid = false
            warnings.add("M5 candle count ($actualCount) exceeds physical maximum feasible ($expectedMax) for $elapsedMinutes mins runtime across $symbolCount symbols.")
        }

        val variance = actualCount - expectedMax
        val confidence = if (isValid) 1.0 else 0.0

        return CounterSanityResult(
            timeframe = "M5",
            expectedMin = expectedMin,
            expectedMax = expectedMax,
            actualCount = actualCount,
            variance = variance,
            confidence = confidence,
            isValid = isValid,
            warnings = warnings
        )
    }

    fun validateM15Count(elapsedMinutes: Long, symbolCount: Int, actualCount: Int): CounterSanityResult {
        val maxPerSymbol = (elapsedMinutes / 15).toInt() + 1
        val expectedMax = maxPerSymbol * symbolCount
        val expectedMin = 0
        val warnings = mutableListOf<String>()

        var isValid = true
        if (actualCount > expectedMax) {
            isValid = false
            warnings.add("M15 candle count ($actualCount) exceeds physical maximum feasible ($expectedMax) for $elapsedMinutes mins runtime across $symbolCount symbols.")
        }

        val variance = actualCount - expectedMax
        val confidence = if (isValid) 1.0 else 0.0

        return CounterSanityResult(
            timeframe = "M15",
            expectedMin = expectedMin,
            expectedMax = expectedMax,
            actualCount = actualCount,
            variance = variance,
            confidence = confidence,
            isValid = isValid,
            warnings = warnings
        )
    }

    fun validateH1Count(sessionStartMs: Long, currentMs: Long, symbolCount: Int, actualCount: Int): CounterSanityResult {
        val elapsedMinutes = (currentMs - sessionStartMs) / 60000L
        val maxPerSymbol = if (elapsedMinutes < 60) 0 else (elapsedMinutes / 60).toInt()
        val expectedMax = maxPerSymbol * symbolCount
        val expectedMin = 0
        val warnings = mutableListOf<String>()

        var isValid = true
        if (actualCount > expectedMax) {
            isValid = false
            warnings.add("H1 candle count ($actualCount) exceeds physical maximum feasible ($expectedMax) before first valid H1 boundary for $elapsedMinutes mins runtime across $symbolCount symbols.")
        }

        val variance = actualCount - expectedMax
        val confidence = if (isValid) 1.0 else 0.0

        return CounterSanityResult(
            timeframe = "H1",
            expectedMin = expectedMin,
            expectedMax = expectedMax,
            actualCount = actualCount,
            variance = variance,
            confidence = confidence,
            isValid = isValid,
            warnings = warnings
        )
    }
}
