package com.zbxapp.util

import timber.log.Timber

/**
 * Installs a JVM-wide uncaught exception handler that funnels crashes through
 * Timber before delegating to the previously registered handler (typically the
 * Android default that terminates the process). This guarantees every fatal
 * exception is logged with the app's standard logging pipeline.
 *
 * Safe to call multiple times; subsequent calls are no-ops.
 */
object GlobalErrorHandler {

    @Volatile
    private var installed: Boolean = false

    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    Timber.e(throwable, "Uncaught exception on thread %s", thread.name)
                } catch (loggingFailure: Throwable) {
                    loggingFailure.addSuppressed(throwable)
                }
                previous?.uncaughtException(thread, throwable)
            }
            installed = true
        }
    }
}
