package com.gamesoundpro.app.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Central permission helper. GameSound Pro requests the minimum set, lazily:
 *  - RECORD_AUDIO        → only when the user opens the recorder
 *  - POST_NOTIFICATIONS  → only for Gaming Mode / music notifications (Android 13+)
 *  - SYSTEM_ALERT_WINDOW → only when the user enables the floating Gaming Mode bubble,
 *                          granted through the system Settings screen (never faked)
 * Nothing else: no storage permission (SAF is used), no contacts/SMS/location/camera, and
 * explicitly no accessibility service and no root.
 */
object Permissions {

    const val RECORD_AUDIO = Manifest.permission.RECORD_AUDIO
    const val POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS

    fun hasRecordAudio(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Settings screen where the user grants "Display over other apps". */
    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
