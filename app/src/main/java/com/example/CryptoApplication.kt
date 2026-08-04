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

            if (dsn.isNotEmpty() && !dsn.contains("examplePublicKey")) {
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
            } else {
                Log.w("CryptoApplication", "Sentry DSN is empty or placeholder. Skipping Sentry initialization.")
            }
        } catch (e: Exception) {
            Log.e("CryptoApplication", "Failed to initialize Sentry: ${e.message}", e)
        }
    }
}
