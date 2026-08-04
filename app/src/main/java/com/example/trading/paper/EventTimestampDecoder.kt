package com.example.trading.paper

import java.time.ZoneId

data class DecodedTimestamp(
    val epochMillis: Long,
    val isoUtc: String,
    val localFormatted: String,
    val humanReadable: String
)

object EventTimestampDecoder {

    fun decodeEpochMillis(epochMs: Long): DecodedTimestamp {
        val instant = TradingTimeCodec.instantFromEpochMillis(epochMs)
        val isoUtc = TradingTimeCodec.formatUtc(instant)
        val humanReadable = "${TradingTimeCodec.formatUtc(instant).replace("T", " ").replace("Z", "")} UTC"
        val localFormatted = TradingTimeCodec.formatLocal(instant, ZoneId.systemDefault())

        return DecodedTimestamp(
            epochMillis = epochMs,
            isoUtc = isoUtc,
            localFormatted = localFormatted,
            humanReadable = humanReadable
        )
    }

    fun decodeEpochSeconds(epochSec: Long): DecodedTimestamp {
        val epochMs = epochSec * 1000L
        return decodeEpochMillis(epochMs)
    }

    fun decodeIsoUtc(isoStr: String): DecodedTimestamp {
        val instant = TradingTimeCodec.parseIsoUtc(isoStr)
        return decodeEpochMillis(instant.toEpochMilli())
    }

    fun decodeEventIdTimestamp(eventId: String): DecodedTimestamp? {
        val parts = eventId.split("_")
        val numericPart = parts.lastOrNull { it.toLongOrNull() != null }?.toLongOrNull()
            ?: Regex("\\d{10,13}").find(eventId)?.value?.toLongOrNull()

        if (numericPart == null) return null

        // Handle both epoch seconds (10 digits) and epoch millis (13 digits) explicitly
        val epochMs = if (numericPart < 10_000_000_000L) {
            numericPart * 1000L
        } else {
            numericPart
        }

        return if (TradingTimeCodec.validateEpochUnit(epochMs)) {
            decodeEpochMillis(epochMs)
        } else {
            null
        }
    }
}
