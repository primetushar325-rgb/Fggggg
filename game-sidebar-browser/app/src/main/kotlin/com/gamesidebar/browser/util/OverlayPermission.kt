package com.gamesidebar.browser.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest

/**
 * Runtime permission helpers.
 *
 * SYSTEM_ALERT_WINDOW is a special permission: it is granted through a system settings screen, not a
 * dialog, so the app explains itself first, sends the user there, and re-checks on resume. If it is
 * refused, the in-app browser keeps working - the overlay is an enhancement, never a hard gate.
 */
object OverlayPermission {

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun settingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    ).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    /** Some OEM builds reject the package URI; this is the fallback target. */
    fun genericSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

    fun needsNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    fun notificationPermission(): String = Manifest.permission.POST_NOTIFICATIONS

    /** Battery optimisation kills long-lived overlay services on some devices. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean = try {
        val manager = context.getSystemService(android.os.PowerManager::class.java)
        manager?.isIgnoringBatteryOptimizations(context.packageName) == true
    } catch (error: RuntimeException) {
        // A missing PowerManager should never stop the sidebar from starting.
        false
    }

    fun batteryOptimizationsIntent(context: Context): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
}
