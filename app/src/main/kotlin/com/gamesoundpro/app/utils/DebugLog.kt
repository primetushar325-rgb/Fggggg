package com.gamesoundpro.app.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * Structured debug logging for the stability-critical subsystems. Enabled only in debug
 * builds (detected from the application debuggable flag) so release installs never pay the
 * logging cost or leak behavior. Tags used across the app:
 * [OverlayService] [OverlayState] [AudioEngine] [AudioFocus] [Playback] [Permission].
 */
object DebugLog {

    @Volatile
    var enabled: Boolean = false
        private set

    fun init(context: Context) {
        enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun d(tag: String, message: String) {
        if (enabled) Log.d(tag, message)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        if (enabled) Log.w(tag, message, error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        if (enabled) Log.e(tag, message, error)
    }
}
