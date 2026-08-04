package com.example.service

import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Utility object for real-time error tracking and performance telemetry via Sentry.
 */
object SentryLogger {

    fun captureException(throwable: Throwable, message: String? = null) {
        message?.let {
            Sentry.addBreadcrumb(it)
        }
        Sentry.captureException(throwable)
    }

    fun logMessage(message: String, level: SentryLevel = SentryLevel.INFO) {
        Sentry.captureMessage(message, level)
    }

    fun addBreadcrumb(category: String, message: String) {
        Sentry.addBreadcrumb(message, category)
    }
}
