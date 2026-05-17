package com.zbxapp.util

import android.util.Log
import com.zbxapp.BuildConfig
import timber.log.Timber

/**
 * Installs a Timber tree appropriate for the current build type.
 *
 * - Debug builds: full [Timber.DebugTree] with source-file/line info.
 * - Release builds: a tree that drops VERBOSE/DEBUG/INFO and forwards only
 *   WARN and ERROR to [Log], so noisy logging does not survive into release
 *   APKs but real problems remain observable via logcat.
 */
fun setupTimber() {
    if (Timber.forest().isNotEmpty()) return
    if (BuildConfig.DEBUG) {
        Timber.plant(Timber.DebugTree())
    } else {
        Timber.plant(ReleaseTree())
    }
}

private class ReleaseTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority >= Log.WARN
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (!isLoggable(tag, priority)) return
        val safeTag = tag ?: DEFAULT_TAG
        if (t != null) {
            Log.println(priority, safeTag, "$message\n${Log.getStackTraceString(t)}")
        } else {
            Log.println(priority, safeTag, message)
        }
    }

    companion object {
        private const val DEFAULT_TAG = "ZbxApp"
    }
}
