package com.gamesoundpro.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gamesoundpro.app.GameSoundProApp
import com.gamesoundpro.app.MainActivity
import com.gamesoundpro.app.R
import com.gamesoundpro.app.audio.AudioEngine
import com.gamesoundpro.app.domain.MusicState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.gamesoundpro.app.utils.DebugLog

/**
 * Foreground media-playback service: keeps music playing per Android background-audio rules
 * and shows a dismissible notification with previous / play-pause / next / stop actions.
 * It holds no player of its own — the AudioEngine singleton (same process) is the player.
 */
class PlaybackNotificationService : Service() {

    private val engine: AudioEngine by lazy { (application as GameSoundProApp).container.audioEngine }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLog.d("Service", "PLAYBACK_SERVICE running startId=$startId action=${intent?.action ?: "start"}")
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> engine.playPauseMusic()
            ACTION_NEXT -> engine.nextTrack()
            ACTION_PREVIOUS -> engine.previousTrack()
            ACTION_STOP -> engine.stopMusic()
        }

        val state = engine.musicState.value
        if (state.track == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground(state)

        scope.launch {
            engine.musicState.collect { musicState ->
                if (musicState.track == null) {
                    stopSelf()
                } else {
                    startAsForeground(musicState)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        DebugLog.d("Service", "PLAYBACK_SERVICE stopped")
        scope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground(state: MusicState) {
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: MusicState): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, GameSoundProApp.CHANNEL_PLAYBACK)
            .setSmallIcon(R.drawable.ic_stat_soundboard)
            .setContentTitle(state.track?.name ?: getString(R.string.notif_playback_title))
            .setContentText(
                if (state.isPlaying) getString(R.string.notif_playback_playing)
                else getString(R.string.notif_playback_paused)
            )
            .setContentIntent(openApp)
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_notif_previous, "Previous", pending(ACTION_PREVIOUS, 4))
            .addAction(
                if (state.isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
                "Play / Pause",
                pending(ACTION_PLAY_PAUSE, 1),
            )
            .addAction(R.drawable.ic_notif_next, "Next", pending(ACTION_NEXT, 2))
            .addAction(R.drawable.ic_notif_stop, "Stop", pending(ACTION_STOP, 3))

        return builder.build()
    }

    private fun pending(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, PlaybackNotificationService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val ACTION_PLAY_PAUSE = "com.gamesoundpro.app.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.gamesoundpro.app.action.NEXT"
        const val ACTION_PREVIOUS = "com.gamesoundpro.app.action.PREVIOUS"
        const val ACTION_STOP = "com.gamesoundpro.app.action.STOP"
        private const val NOTIFICATION_ID = 42
    }
}
