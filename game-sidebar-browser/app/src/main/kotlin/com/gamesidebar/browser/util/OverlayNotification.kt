package com.gamesidebar.browser.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.gamesidebar.browser.MainActivity
import com.gamesidebar.browser.R
import com.gamesidebar.browser.overlay.OverlayService
import com.gamesidebar.core.service.OverlayCommand

/**
 * The persistent notification for the overlay foreground service.
 *
 * It is intentionally visible and honest: an overlay utility that hides its own notification would
 * be behaving like malware. Importance is LOW, so there is no sound, no heads-up and no badge - it
 * sits quietly in the shade with Open / Hide / Stop.
 */
object OverlayNotification {

    const val CHANNEL_ID = "gamesidebar_overlay"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, panelOpen: Boolean): Notification {
        val text = if (panelOpen) {
            context.getString(R.string.panel_title)
        } else {
            context.getString(R.string.notification_text)
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_panel)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingCommand(context, OverlayCommand.OPEN_APP, openAppIntent(context)))
            .addAction(
                R.drawable.ic_expand,
                context.getString(R.string.notification_action_open),
                pendingCommand(context, OverlayCommand.SHOW_PANEL),
            )
            .addAction(
                R.drawable.ic_minimize,
                context.getString(R.string.notification_action_hide),
                pendingCommand(context, OverlayCommand.HIDE_PANEL),
            )
            .addAction(
                R.drawable.ic_close,
                context.getString(R.string.notification_action_stop),
                pendingCommand(context, OverlayCommand.STOP_SERVICE),
            )
            .build()
    }

    /** Shown when Android System WebView is missing: the service stays honest about being useless. */
    fun buildUnavailable(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.error_webview_missing))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.error_webview_body)))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    900,
                    openAppIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .addAction(
                R.drawable.ic_close,
                context.getString(R.string.notification_action_stop),
                PendingIntent.getService(
                    context,
                    901,
                    Intent(context, OverlayService::class.java).apply {
                        action = OverlayCommand.STOP_SERVICE.action
                        setPackage(context.packageName)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    private fun openAppIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    private fun pendingCommand(
        context: Context,
        command: OverlayCommand,
        explicit: Intent? = null,
    ): PendingIntent {
        val intent = (explicit ?: Intent(context, OverlayService::class.java)).apply {
            action = command.action
            setPackage(context.packageName)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (explicit != null) {
            PendingIntent.getActivity(context, command.ordinal + 100, intent, flags)
        } else {
            PendingIntent.getService(context, command.ordinal, intent, flags)
        }
    }
}
