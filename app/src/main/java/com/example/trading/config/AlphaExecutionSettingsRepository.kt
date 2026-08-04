package com.example.trading.config

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "alpha_execution_settings")

class AlphaExecutionSettingsRepository private constructor(
    private val dataStore: DataStore<Preferences>?,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val _settings = MutableStateFlow(AlphaExecutionSettings())
    val settings: StateFlow<AlphaExecutionSettings> = _settings.asStateFlow()

    private val _latestAudit = MutableStateFlow<ThresholdChangeAudit?>(null)
    val latestAudit: StateFlow<ThresholdChangeAudit?> = _latestAudit.asStateFlow()

    private val _diagnosticEvents = MutableStateFlow<List<String>>(emptyList())
    val diagnosticEvents: StateFlow<List<String>> = _diagnosticEvents.asStateFlow()

    init {
        if (dataStore != null) {
            coroutineScope.launch {
                dataStore.data
                    .catch { exception ->
                        logDiagnostic("DataStore read error/corruption: ${exception.message}. Falling back to default settings.")
                        emit(emptyPreferences())
                    }
                    .collect { prefs ->
                        try {
                            val loadedScore = try { prefs[KEY_MINIMUM_SCORE] ?: AlphaExecutionSettings.DEFAULT_SCORE } catch (e: Exception) { AlphaExecutionSettings.DEFAULT_SCORE }
                            val loadedEnabled = try { prefs[KEY_AUTO_PAPER_ENABLED] ?: true } catch (e: Exception) { true }
                            val loadedUpdatedAt = try { prefs[KEY_UPDATED_AT] ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }
                            val loadedSourceStr = try { prefs[KEY_UPDATE_SOURCE] ?: SettingsUpdateSource.DEFAULT_CONFIG.name } catch (e: Exception) { SettingsUpdateSource.DEFAULT_CONFIG.name }
                            val loadedSchemaVer = try { prefs[KEY_SCHEMA_VERSION] ?: 1 } catch (e: Exception) { 1 }
                            val loadedSettingsVer = try { prefs[KEY_SETTINGS_VERSION] ?: 1L } catch (e: Exception) { 1L }

                            val loadedSource = runCatching { SettingsUpdateSource.valueOf(loadedSourceStr) }.getOrDefault(SettingsUpdateSource.DEFAULT_CONFIG)

                            var finalScore = loadedScore
                            if (finalScore.isNaN() || finalScore.isInfinite() || finalScore < AlphaExecutionSettings.ABSOLUTE_MINIMUM_SCORE || finalScore > AlphaExecutionSettings.MAXIMUM_SCORE) {
                                logDiagnostic("Corrupted or out-of-bounds score loaded: $finalScore. Falling back to default ${AlphaExecutionSettings.DEFAULT_SCORE}.")
                                finalScore = AlphaExecutionSettings.DEFAULT_SCORE
                            }

                            val loadedSettings = AlphaExecutionSettings(
                                minimumAutoTradeScore = finalScore,
                                autoPaperTradingEnabled = loadedEnabled,
                                updatedAtEpochMs = loadedUpdatedAt,
                                updatedBy = loadedSource,
                                schemaVersion = loadedSchemaVer,
                                settingsVersion = loadedSettingsVer
                            )

                            val prevScore = try { prefs[KEY_PREVIOUS_SCORE] ?: AlphaExecutionSettings.DEFAULT_SCORE } catch (e: Exception) { AlphaExecutionSettings.DEFAULT_SCORE }
                            val versionPresent = try { prefs[KEY_SETTINGS_VERSION] != null } catch (e: Exception) { false }
                            if (versionPresent) {
                                _latestAudit.value = ThresholdChangeAudit(
                                    previousScore = prevScore,
                                    newScore = finalScore,
                                    changedAtEpochMs = loadedUpdatedAt,
                                    source = loadedSource,
                                    autoPaperTradingEnabled = loadedEnabled,
                                    settingsVersion = loadedSettingsVer
                                )
                            }

                            logDiagnostic("STAGE 1 [SETTINGS_REPOSITORY] Active Threshold = $finalScore, Version = $loadedSettingsVer, Source = ${loadedSource.name}")

                            _settings.value = loadedSettings
                        } catch (e: Throwable) {
                            logDiagnostic("Failed to parse settings preferences: ${e.message}. Using safe defaults.")
                        }
                    }
            }
        }
    }

    suspend fun updateSettings(
        newScore: Double,
        autoPaperTradingEnabled: Boolean,
        source: SettingsUpdateSource
    ): Result<AlphaExecutionSettings> {
        if (newScore.isNaN() || newScore.isInfinite()) {
            val err = "Invalid score threshold: $newScore (NaN or Infinite)"
            logDiagnostic("REJECTED_UPDATE: $err")
            return Result.failure(IllegalArgumentException(err))
        }

        if (newScore < AlphaExecutionSettings.ABSOLUTE_MINIMUM_SCORE || newScore > AlphaExecutionSettings.MAXIMUM_SCORE) {
            val err = "Score threshold $newScore out of allowed bounds [${AlphaExecutionSettings.ABSOLUTE_MINIMUM_SCORE}, ${AlphaExecutionSettings.MAXIMUM_SCORE}]"
            logDiagnostic("REJECTED_UPDATE: $err")
            return Result.failure(IllegalArgumentException(err))
        }

        val current = _settings.value
        val nextVersion = current.settingsVersion + 1
        val now = System.currentTimeMillis()

        val updated = AlphaExecutionSettings(
            minimumAutoTradeScore = newScore,
            autoPaperTradingEnabled = autoPaperTradingEnabled,
            updatedAtEpochMs = now,
            updatedBy = source,
            schemaVersion = 1,
            settingsVersion = nextVersion
        )

        val audit = ThresholdChangeAudit(
            previousScore = current.minimumAutoTradeScore,
            newScore = newScore,
            changedAtEpochMs = now,
            source = source,
            autoPaperTradingEnabled = autoPaperTradingEnabled,
            settingsVersion = nextVersion
        )

        if (dataStore != null) {
            try {
                dataStore.edit { prefs ->
                    prefs[KEY_PREVIOUS_SCORE] = current.minimumAutoTradeScore
                    prefs[KEY_MINIMUM_SCORE] = newScore
                    prefs[KEY_AUTO_PAPER_ENABLED] = autoPaperTradingEnabled
                    prefs[KEY_UPDATED_AT] = now
                    prefs[KEY_UPDATE_SOURCE] = source.name
                    prefs[KEY_SCHEMA_VERSION] = 1
                    prefs[KEY_SETTINGS_VERSION] = nextVersion
                }
            } catch (e: Exception) {
                logDiagnostic("DataStore write failed: ${e.message}")
                return Result.failure(e)
            }
        }

        _settings.value = updated
        _latestAudit.value = audit
        logDiagnostic("SETTINGS_SAVED: threshold=${updated.minimumAutoTradeScore}, autoPaper=${updated.autoPaperTradingEnabled}, version=${updated.settingsVersion}")
        return Result.success(updated)
    }

    suspend fun resetToDefault(): Result<AlphaExecutionSettings> {
        return updateSettings(
            newScore = AlphaExecutionSettings.DEFAULT_SCORE,
            autoPaperTradingEnabled = true,
            source = SettingsUpdateSource.USER_UI
        )
    }

    suspend fun clearAllDataStoreKeys(): Result<Boolean> {
        if (dataStore != null) {
            try {
                dataStore.edit { prefs ->
                    prefs.clear()
                }
            } catch (e: Exception) {
                logDiagnostic("DataStore clear failed: ${e.message}")
                return Result.failure(e)
            }
        }
        _settings.value = AlphaExecutionSettings()
        _latestAudit.value = null
        logDiagnostic("DATASTORE_CLEARED: All DataStore keys wiped and reset to default state.")
        return Result.success(true)
    }

    fun updateSettingsInMemoryDirectlyForTest(
        newScore: Double,
        autoPaperTradingEnabled: Boolean = true,
        source: SettingsUpdateSource = SettingsUpdateSource.TEST
    ) {
        val current = _settings.value
        val nextVersion = current.settingsVersion + 1
        val now = System.currentTimeMillis()

        if (newScore.isNaN() || newScore.isInfinite() || newScore < AlphaExecutionSettings.ABSOLUTE_MINIMUM_SCORE || newScore > AlphaExecutionSettings.MAXIMUM_SCORE) {
            throw IllegalArgumentException("Invalid test score: $newScore")
        }

        val updated = AlphaExecutionSettings(
            minimumAutoTradeScore = newScore,
            autoPaperTradingEnabled = autoPaperTradingEnabled,
            updatedAtEpochMs = now,
            updatedBy = source,
            schemaVersion = 1,
            settingsVersion = nextVersion
        )
        _settings.value = updated
        _latestAudit.value = ThresholdChangeAudit(
            previousScore = current.minimumAutoTradeScore,
            newScore = newScore,
            changedAtEpochMs = now,
            source = source,
            autoPaperTradingEnabled = autoPaperTradingEnabled,
            settingsVersion = nextVersion
        )
    }

    private fun logDiagnostic(msg: String) {
        try {
            Log.i("AlphaSettingsRepo", msg)
        } catch (_: Throwable) {
            // Android Log unmocked in local unit tests
        }
        _diagnosticEvents.value = (_diagnosticEvents.value + msg).takeLast(50)
    }

    companion object {
        val KEY_MINIMUM_SCORE = doublePreferencesKey("alpha_minimum_auto_trade_score")
        val KEY_PREVIOUS_SCORE = doublePreferencesKey("alpha_previous_auto_trade_score")
        val KEY_AUTO_PAPER_ENABLED = booleanPreferencesKey("alpha_auto_paper_trading_enabled")
        val KEY_UPDATED_AT = longPreferencesKey("alpha_threshold_updated_at")
        val KEY_UPDATE_SOURCE = stringPreferencesKey("alpha_settings_update_source")
        val KEY_SCHEMA_VERSION = intPreferencesKey("alpha_settings_schema_version")
        val KEY_SETTINGS_VERSION = longPreferencesKey("alpha_settings_version")

        @Volatile
        private var INSTANCE: AlphaExecutionSettingsRepository? = null

        fun getInstance(context: Context): AlphaExecutionSettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AlphaExecutionSettingsRepository(context.applicationContext.dataStore).also { INSTANCE = it }
            }
        }

        fun createInMemoryForTest(): AlphaExecutionSettingsRepository {
            return AlphaExecutionSettingsRepository(null)
        }
    }
}
