package com.gamesidebar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.IBinder;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.gamesidebar.browser.WebViewFactory;
import com.gamesidebar.overlay.OverlayManager;
import com.gamesidebar.util.PrefsManager;

/**
 * Foreground service for floating overlay over Free Fire.
 * Fixes: keyboard/back priority, lifecycle, do NOT destroy WebView on hide.
 */
public class FloatingService extends Service {

    private static final String CHANNEL_ID = "game_sidebar_channel";
    private static final int NOTIF_ID = 1001;

    private OverlayManager overlayManager;
    private PrefsManager prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new PrefsManager(this);
        overlayManager = new OverlayManager(this);
        createChannel();
        startForeground(NOTIF_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getStringExtra("action") : null;
        if ("collapse".equals(action)) {
            overlayManager.collapse();
        } else if ("expand".equals(action)) {
            overlayManager.expand();
        } else if ("hide".equals(action)) {
            overlayManager.hideAll();
            stopSelf();
        } else {
            // Default: show panel or handle depending on saved state
            if (prefs.isCollapsed()) {
                // Show handle only
                overlayManager.collapse(); // will show handle via logic? Actually need show handle directly
                // If panel not yet shown, show handle
                // Workaround: show panel then collapse to get handle
                overlayManager.showPanel();
                overlayManager.collapse();
            } else {
                overlayManager.showPanel();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (overlayManager != null) overlayManager.onConfigurationChanged(newConfig);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayManager != null) overlayManager.onDestroy();
        // Only destroy WebView on service destroy - not on collapse/hide/resize
        // But per spec avoid unnecessary destroy; we keep for session persistence unless service killed
        // WebViewFactory.destroyIfNeeded();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Game Sidebar", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Floating Game Sidebar");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, FloatingService.class);
        open.putExtra("action", "expand");
        PendingIntent piOpen = PendingIntent.getService(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent collapse = new Intent(this, FloatingService.class);
        collapse.putExtra("action", "collapse");
        PendingIntent piCollapse = PendingIntent.getService(this, 1, collapse, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(piOpen)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.collapse), piCollapse)
                .setOngoing(true)
                .build();
    }

    /**
     * FIX keyboard/back priority - called from panel if needed
     * This service's window does not directly receive key events, but panel's BrowserController handles.
     */
}
