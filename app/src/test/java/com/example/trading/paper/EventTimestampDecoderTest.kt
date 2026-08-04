package com.example.trading.paper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EventTimestampDecoderTest {

    @Test
    fun testDecodeEpochMillis() {
        val epochMs = 1785251400000L
        val decoded = EventTimestampDecoder.decodeEpochMillis(epochMs)
        val expectedIso = java.time.Instant.ofEpochMilli(epochMs).toString()

        assertEquals(1785251400000L, decoded.epochMillis)
        assertEquals(expectedIso, decoded.isoUtc)
    }

    @Test
    fun testDecodeEpochSeconds() {
        val epochSec = 1785251400L
        val decoded = EventTimestampDecoder.decodeEpochSeconds(epochSec)
        val expectedIso = java.time.Instant.ofEpochMilli(1785251400000L).toString()

        assertEquals(1785251400000L, decoded.epochMillis)
        assertEquals(expectedIso, decoded.isoUtc)
    }

    @Test
    fun testDecodeIsoUtc() {
        val isoStr = "2026-07-29T07:10:00Z"
        val decoded = EventTimestampDecoder.decodeIsoUtc(isoStr)

        assertEquals(java.time.Instant.parse(isoStr).toEpochMilli(), decoded.epochMillis)
        assertEquals(isoStr, decoded.isoUtc)
    }

    @Test
    fun testDecodeEventIdTimestamp() {
        val eventId = "EVT_BTC_1785251400000"
        val decoded = EventTimestampDecoder.decodeEventIdTimestamp(eventId)
        val expectedIso = java.time.Instant.ofEpochMilli(1785251400000L).toString()

        assertNotNull(decoded)
        assertEquals(1785251400000L, decoded!!.epochMillis)
        assertEquals(expectedIso, decoded.isoUtc)
    }
}
