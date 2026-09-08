package com.gamesoundpro.app.di

import android.content.Context
import com.gamesoundpro.app.audio.AudioEngine
import com.gamesoundpro.app.database.AppDatabase
import com.gamesoundpro.app.overlay.GamingModeManager
import com.gamesoundpro.app.repository.SoundRepository
import com.gamesoundpro.app.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Tiny hand-rolled dependency container. A full DI framework would only slow cold start on
 * the low-end devices this app targets; construction order here is explicit and cheap.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val soundRepository: SoundRepository by lazy {
        SoundRepository(
            context = appContext,
            soundDao = database.soundDao(),
            packDao = database.packDao(),
        )
    }

    val audioEngine: AudioEngine by lazy {
        AudioEngine(
            context = appContext,
            settings = settingsRepository,
            repository = soundRepository,
        )
    }

    val gamingModeManager: GamingModeManager by lazy {
        GamingModeManager(appContext, settingsRepository)
    }
}
