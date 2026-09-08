package com.gamesoundpro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamesoundpro.app.di.AppContainer
import com.gamesoundpro.app.domain.AppSettings
import com.gamesoundpro.app.overlay.GamingModeManager
import com.gamesoundpro.app.ui.navigation.AppRoot
import com.gamesoundpro.app.ui.theme.GameSoundProTheme

class MainActivity : ComponentActivity() {

    val container: AppContainer by lazy { (application as GameSoundProApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
            GameSoundProTheme(settings) {
                AppRoot(container)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // "Auto Stop" preference: release all audio when the app goes to the background.
        // Skipped while the floating overlay is active — that is exactly when the user wants
        // sounds to keep playing above the game.
        val snapshot = container.settingsRepository.snapshot
        if (snapshot.autoStop && !GamingModeManager.overlayActive.value) {
            container.audioEngine.stopAll()
        }
    }
}
