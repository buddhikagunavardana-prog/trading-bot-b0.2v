package com.example

import android.app.Application
import android.util.Log
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions

class CryptoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        initSentry()
    }

    private fun initSentry() {
        try {
            val dsn = BuildConfig.SENTRY_DSN
            val env = BuildConfig.SENTRY_ENVIRONMENT.ifEmpty { "production" }
            val sampleRate = BuildConfig.SENTRY_TRACES_SAMPLE_RATE.toDoubleOrNull() ?: 1.0

            val isPlaceholder = dsn.isBlank() ||
                    dsn.contains("examplePublicKey") ||
                    dsn.contains("your_sentry_dsn_here") ||
                    dsn.contains("YOUR_SENTRY_DSN") ||
                    !dsn.startsWith("http")

            if (!isPlaceholder) {
                try {
                    SentryAndroid.init(this) { options: SentryAndroidOptions ->
                        options.dsn = dsn
                        options.environment = env
                        options.tracesSampleRate = sampleRate
                        options.profilesSampleRate = 1.0
                        options.isEnableAutoSessionTracking = true
                        options.isAttachStacktrace = true
                        options.isAttachThreads = true
                    }
                    Log.i("CryptoApplication", "Sentry initialized successfully for environment: $env")
                } catch (se: Throwable) {
                    Log.w("CryptoApplication", "Sentry initialization skipped due to invalid DSN: ${se.message}")
                }
            } else {
                Log.w("CryptoApplication", "Sentry DSN is empty or placeholder. Skipping Sentry initialization.")
            }
        } catch (e: Exception) {
            Log.e("CryptoApplication", "Failed to initialize Sentry: ${e.message}", e)
        }
    }
}
