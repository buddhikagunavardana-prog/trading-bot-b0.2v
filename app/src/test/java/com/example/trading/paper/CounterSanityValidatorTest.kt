package com.example.trading.paper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterSanityValidatorTest {

    @Test
    fun testValidM5Count() {
        val result = CounterSanityValidator.validateM5Count(
            elapsedMinutes = 15,
            symbolCount = 10,
            actualCount = 30
        )
        assertTrue(result.isValid)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun testImpossibleM5Count() {
        val result = CounterSanityValidator.validateM5Count(
            elapsedMinutes = 15,
            symbolCount = 10,
            actualCount = 200
        )
        assertFalse(result.isValid)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun testValidH1CountPreBoundary() {
        val sessionStartMs = 1785251120000L // 15:05:20Z
        val currentMs = 1785252620000L // 15:30:20Z (25 mins runtime)

        val result = CounterSanityValidator.validateH1Count(
            sessionStartMs = sessionStartMs,
            currentMs = currentMs,
            symbolCount = 10,
            actualCount = 0
        )
        assertTrue(result.isValid)
    }

    @Test
    fun testImpossibleH1CountPreBoundary() {
        val sessionStartMs = 1785251120000L // 15:05:20Z
        val currentMs = 1785252620000L // 15:30:20Z (25 mins runtime)

        val result = CounterSanityValidator.validateH1Count(
            sessionStartMs = sessionStartMs,
            currentMs = currentMs,
            symbolCount = 10,
            actualCount = 2
        )
        assertFalse(result.isValid)
    }
}
