package com.example.trading.paper

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs

object TradingTimeCodec {

    private val UTC_ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT
    private val SESSION_ID_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US).withZone(ZoneOffset.UTC)
    private val LOCAL_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.US)

    // Plausible epoch millis bounds (2017 to 2049)
    private const val MIN_PLAUSIBLE_EPOCH_MS = 1_500_000_000_000L
    private const val MAX_PLAUSIBLE_EPOCH_MS = 2_500_000_000_000L

    fun instantFromEpochMillis(epochMs: Long): Instant {
        require(validateEpochUnit(epochMs)) {
            "Epoch millis $epochMs is outside plausible range [$MIN_PLAUSIBLE_EPOCH_MS, $MAX_PLAUSIBLE_EPOCH_MS]"
        }
        return Instant.ofEpochMilli(epochMs)
    }

    fun epochMillisFromInstant(instant: Instant): Long {
        return instant.toEpochMilli()
    }

    fun formatUtc(instant: Instant): String {
        return UTC_ISO_FORMATTER.format(instant)
    }

    fun formatLocal(instant: Instant, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return LOCAL_FORMATTER.withZone(zoneId).format(instant)
    }

    fun parseIsoUtc(isoString: String): Instant {
        return Instant.parse(isoString)
    }

    fun validateEpochUnit(epochMs: Long): Boolean {
        return epochMs in MIN_PLAUSIBLE_EPOCH_MS..MAX_PLAUSIBLE_EPOCH_MS
    }

    fun generateCanonicalSessionId(startInstant: Instant, prefix: String = "SESS_LIVE_PAPER"): String {
        val dateStr = SESSION_ID_FORMATTER.format(startInstant)
        return "${prefix}_${dateStr}_UTC"
    }

    fun decodeSessionIdTimestamp(sessionId: String): Instant? {
        val regex = Regex("SESS_(?:LIVE|ALPHA)_PAPER_(\\d{8}_\\d{6})(?:_UTC)?")
        val match = regex.find(sessionId) ?: return null
        val datePart = match.groupValues[1]

        return try {
            val ldt = LocalDateTime.parse(datePart, DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US))
            ldt.toInstant(ZoneOffset.UTC)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun validateSessionIdConsistency(sessionId: String, sessionStartEpochMs: Long): Boolean {
        if (!validateEpochUnit(sessionStartEpochMs)) return false
        val decodedInstant = decodeSessionIdTimestamp(sessionId) ?: return false
        val startInstant = Instant.ofEpochMilli(sessionStartEpochMs)

        val diffMs = abs(startInstant.toEpochMilli() - decodedInstant.toEpochMilli())
        // Session ID has second precision, so diff must be under 1000ms
        return diffMs < 1000L
    }
}
