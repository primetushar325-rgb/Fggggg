@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.provider

import android.content.Context

object Settings {
    const val ACTION_MANAGE_OVERLAY_PERMISSION = "android.settings.action.MANAGE_OVERLAY_PERMISSION"
    const val ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS"
    const val ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS =
        "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"
    const val ACTION_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS"
    const val ACTION_SETTINGS = "android.settings.SETTINGS"

    @JvmStatic
    fun canDrawOverlays(context: Context): Boolean = true
}
