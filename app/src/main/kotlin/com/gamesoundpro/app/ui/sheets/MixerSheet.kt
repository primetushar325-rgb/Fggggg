package com.gamesoundpro.app.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamesoundpro.app.di.AppContainer
import com.gamesoundpro.app.domain.AppSettings
import com.gamesoundpro.app.domain.MixerVolumes
import com.gamesoundpro.app.utils.Format
import kotlinx.coroutines.launch

/**
 * Premium mini mixer: per-channel volumes (effects / music / voice), master volume,
 * mute, stop-all and the optional "duck music when an effect plays" behaviour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixerSheet(
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings.DEFAULT
    )
    val scope = rememberCoroutineScope()

    fun update(transform: (MixerVolumes) -> MixerVolumes) {
        scope.launch { container.settingsRepository.setMixerVolumes(transform(settings.mixer)) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Audio Mixer", style = MaterialTheme.typography.titleLarge)
            Text(
                "Example: Music 40% · Effects 80% · Master 70%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(4.dp))

            MixerSlider("💥", "Sound effects", settings.mixer.effects) { v ->
                update { it.copy(effects = v) }
            }
            MixerSlider("🎵", "Music", settings.mixer.music) { v ->
                container.audioEngine.setMusicVolumeLive(v)
                update { it.copy(music = v) }
            }
            MixerSlider("🎙️", "Voice recordings", settings.mixer.voice) { v ->
                update { it.copy(voice = v) }
            }
            MixerSlider("🎚️", "Master volume", settings.mixer.master) { v ->
                container.audioEngine.setMasterVolumeLive(v)
                update { it.copy(master = v) }
            }

            Spacer(Modifier.padding(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mute everything", style = MaterialTheme.typography.titleSmall)
                    Text("One tap silence", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.mixer.muted, onCheckedChange = {
                    scope.launch { container.settingsRepository.toggleMuted() }
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Duck music on effects", style = MaterialTheme.typography.titleSmall)
                    Text("Lower music while a sound effect plays", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.ducking, onCheckedChange = { on ->
                    scope.launch { container.settingsRepository.setDucking(on) }
                })
            }

            Spacer(Modifier.padding(4.dp))
            Button(
                onClick = { container.audioEngine.stopAll() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("⏹ Stop All")
            }
        }
    }
}

@Composable
private fun MixerSlider(
    emoji: String,
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(Format.percent(value), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onChange,
            valueRange = 0f..1f,
        )
    }
}
