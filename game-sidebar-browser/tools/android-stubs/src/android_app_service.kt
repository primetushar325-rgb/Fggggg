@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.IBinder

open class Service : Context() {
    open fun onCreate() {}
    open fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    open fun onDestroy() {}
    open fun onBind(intent: Intent?): IBinder? = null
    open fun onConfigurationChanged(newConfig: Configuration) {}
    open fun onTaskRemoved(rootIntent: Intent?) {}
    fun startForeground(id: Int, notification: Notification) {}
    fun startForeground(id: Int, notification: Notification, foregroundServiceType: Int) {}
    fun stopSelf() {}
    fun stopForeground(flags: Int) {}

    companion object {
        const val START_STICKY = 1
        const val START_NOT_STICKY = 2
        const val START_REDELIVER_INTENT = 3
        const val STOP_FOREGROUND_REMOVE = 1
    }
}

class ForegroundServiceStartNotAllowedException(message: String? = null) : Exception(message)
