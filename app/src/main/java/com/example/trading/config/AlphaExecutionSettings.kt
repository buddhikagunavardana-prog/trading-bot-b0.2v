package com.example.trading.config

import java.time.Instant

enum class SettingsUpdateSource {
    DEFAULT_CONFIG,
    USER_UI,
    MIGRATION,
    TEST
}

data class AlphaExecutionSettings(
    val minimumAutoTradeScore: Double = DEFAULT_SCORE,
    val autoPaperTradingEnabled: Boolean = true,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val updatedBy: SettingsUpdateSource = SettingsUpdateSource.DEFAULT_CONFIG,
    val schemaVersion: Int = 1,
    val settingsVersion: Long = 1L
) {
    val minAutoTradeScoreThreshold: Double get() = minimumAutoTradeScore
    val version: Long get() = settingsVersion
    val highFrequencyTestMode: Boolean get() = minimumAutoTradeScore < 70.0

    companion object {
        const val ABSOLUTE_MINIMUM_SCORE = 50.0
        const val DEFAULT_SCORE = 75.0
        const val MAXIMUM_SCORE = 95.0
        const val HIGH_RISK_THRESHOLD = 65.0
    }

    fun isValid(): Boolean {
        return !minimumAutoTradeScore.isNaN() &&
                !minimumAutoTradeScore.isInfinite() &&
                minimumAutoTradeScore in ABSOLUTE_MINIMUM_SCORE..MAXIMUM_SCORE
    }
}

data class ThresholdChangeAudit(
    val previousScore: Double,
    val newScore: Double,
    val changedAtEpochMs: Long,
    val source: SettingsUpdateSource,
    val autoPaperTradingEnabled: Boolean,
    val settingsVersion: Long
) {
    val previousThreshold: Double get() = previousScore
    val newThreshold: Double get() = newScore
    val timestampEpochMs: Long get() = changedAtEpochMs
    val version: Long get() = settingsVersion
    val reason: String get() = "Source: ${source.name}"
    val changedBy: String get() = source.name

    fun formatChangedAtUtc(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date(changedAtEpochMs))
    }
}
