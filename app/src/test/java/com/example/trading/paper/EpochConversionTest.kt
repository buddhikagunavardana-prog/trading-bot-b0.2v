package com.example.trading.paper

import org.junit.Assert.assertEquals
import org.junit.Test

class EpochConversionTest {

    @Test
    fun testExplicitEpoch1785251400000() {
        val epochMs = 1785251400000L
        val decoded = EventTimestampDecoder.decodeEpochMillis(epochMs)
        val expectedIso = java.time.Instant.ofEpochMilli(epochMs).toString()

        assertEquals(1785251400000L, decoded.epochMillis)
        assertEquals(expectedIso, decoded.isoUtc)
    }

    @Test
    fun testSessionStartEpoch1785251120000() {
        val epochMs = 1785251120000L
        val decoded = EventTimestampDecoder.decodeEpochMillis(epochMs)
        val expectedIso = java.time.Instant.ofEpochMilli(epochMs).toString()

        assertEquals(1785251120000L, decoded.epochMillis)
        assertEquals(expectedIso, decoded.isoUtc)
    }
}
