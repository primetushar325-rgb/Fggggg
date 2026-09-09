package com.gamesoundpro.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.gamesoundpro.app.di.AppContainer
import com.gamesoundpro.app.utils.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GameSoundProApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        DebugLog.init(this)
        container = AppContainer(this)
        createNotificationChannels()
        seedStarterContent()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val gaming = NotificationChannel(
            CHANNEL_GAMING,
            getString(R.string.notif_channel_gaming),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notif_channel_gaming_desc) }
        val playback = NotificationChannel(
            CHANNEL_PLAYBACK,
            getString(R.string.notif_channel_playback),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notif_channel_playback_desc) }
        manager.createNotificationChannel(gaming)
        manager.createNotificationChannel(playback)
    }

    /** Copies the bundled starter WAVs on first launch so the soundboard works instantly. */
    private fun seedStarterContent() {
        container.applicationScope.launch(Dispatchers.IO) {
            val settings = container.settingsRepository
            if (!settings.snapshot.seedDone) {
                try {
                    container.soundRepository.seedStarterPack()
                } catch (_: Exception) {
                    // Seeding is best-effort; the app is fully usable without it.
                }
                settings.setSeedDone(true)
            }
        }
    }

    companion object {
        const val CHANNEL_GAMING = "gaming_mode"
        const val CHANNEL_PLAYBACK = "playback"
    }
}
