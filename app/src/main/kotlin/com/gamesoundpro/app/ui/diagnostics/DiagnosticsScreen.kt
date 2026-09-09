package com.gamesoundpro.app.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamesoundpro.app.audio.AudioState
import com.gamesoundpro.app.audio.FocusState
import com.gamesoundpro.app.ui.components.SectionHeader
import com.gamesoundpro.app.ui.theme.GlassSurface

/**
 * Audio Diagnostics — an engineering screen that shows the exact health of the audio and
 * overlay subsystems. Built to debug devices where in-game playback behaves differently:
 * every row answers one concrete question ("is the engine alive?", "is media volume 0?").
 */
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val active by viewModel.activeSounds.collectAsStateWithLifecycle()
    val music by viewModel.musicState.collectAsStateWithLifecycle()
    val overlayActive by viewModel.overlayActive.collectAsStateWithLifecycle()
    val mediaVolume by viewModel.mediaVolume.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Go back")
            }
            Text("Audio Diagnostics", style = MaterialTheme.typography.headlineMedium)
        }

        SectionHeader("TEST SOUND")
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Plays a built-in test tone through the full playback pipeline. Use it " +
                        "right after entering a game to confirm audio still works.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { viewModel.testSound() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("🔔 TEST SOUND") }

                testResult?.let { result ->
                    when {
                        result.success -> {
                            Text("✓ Audio Engine Active", color = MaterialTheme.colorScheme.primary)
                            if (result.hint != null) {
                                Text("⚠ ${result.hint}", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        else -> {
                            Text(
                                "⚠ Audio Playback Unavailable",
                                color = MaterialTheme.colorScheme.error,
                            )
                            if (result.failureReason != null) {
                                Text(
                                    result.failureReason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (mediaVolume == 0) {
                    Text(
                        "Media volume is 0 — sounds will be silent on the speaker. Press a " +
                            "volume key and raise \"Media\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        SectionHeader("ENGINE")
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                DiagRow("Audio Engine", if (snapshot.state == AudioState.ERROR) "ERROR" else "READY")
                DiagRow(
                    "Player",
                    if (snapshot.effectPlayersAlive > 0) "READY (${snapshot.effectPlayersAlive} active)"
                    else "READY (pooled, idle)",
                )
                DiagRow(
                    "Audio Focus",
                    when (snapshot.focus) {
                        FocusState.HELD -> "GAIN (held)"
                        FocusState.LOST -> "LOSS"
                        FocusState.NONE -> "NONE (independent)"
                    },
                )
                DiagRow("Master Volume", "${(settings.mixer.master * 100).toInt()}%")
                DiagRow("Media Stream", "$mediaVolume%")
                DiagRow(
                    "Current Sound",
                    snapshot.currentSound?.name ?: music.track?.name ?: "—",
                )
                DiagRow(
                    "Playback",
                    when {
                        active.isNotEmpty() -> "PLAYING"
                        music.isPlaying -> "PLAYING (music)"
                        music.track != null -> "PAUSED"
                        else -> "STOPPED"
                    },
                )
                DiagRow("Audio Route", viewModel.audioRoute())
                snapshot.lastError?.let { DiagRow("Last Error", it) }
            }
        }

        SectionHeader("OVERLAY")
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                DiagRow("Overlay Service", if (overlayActive) "RUNNING" else "STOPPED")
                DiagRow("Gaming Mode", if (settings.gamingMode) "ON" else "OFF")
            }
        }

        SectionHeader("NOTES")
        GlassSurface(Modifier.fillMaxWidth()) {
            Text(
                "GameSound Pro plays through the device's normal audio output using public " +
                    "Android APIs. It never routes audio into another app's voice chat — " +
                    "Android does not allow that, and the app never tries. If teammates " +
                    "cannot hear a sound, play it out loud; in-game transmission is the " +
                    "game's own audio pipeline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}
