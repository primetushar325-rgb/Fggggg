package com.gamesoundpro.app.ui.sheets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamesoundpro.app.audio.RecorderState
import com.gamesoundpro.app.audio.VoiceRecorder
import com.gamesoundpro.app.di.AppContainer
import com.gamesoundpro.app.domain.Category
import com.gamesoundpro.app.permissions.Permissions
import com.gamesoundpro.app.ui.components.LocalHapticsEnabled
import com.gamesoundpro.app.utils.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Record flow: the microphone permission is requested here — and only here, when the user
 * actually opens the recorder. Supports start / pause / resume / stop, live level meter and
 * timer, then hands the finished recording to the shared metadata form (name, category,
 * icon, volume, trim, preview).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSheet(
    container: AppContainer,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(context) }
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current

    var hasMic by remember { mutableStateOf(Permissions.hasRecordAudio(context)) }
    var amplitude by remember { mutableIntStateOf(0) }
    var savedTake by remember { mutableStateOf(false) }
    val state by recorder.state.collectAsState()
    val elapsed by recorder.elapsedMs.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            // Clean up any take that was never saved (a saved file must survive disposal).
            if (!savedTake && (state == RecorderState.RECORDING || state == RecorderState.PAUSED)) {
                recorder.discard()
            }
        }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMic = granted
        if (!granted) onMessage("Microphone permission denied — recording is disabled")
    }

    val finishedTake = state == RecorderState.STOPPED && recorder.outputFile != null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Record Sound", style = MaterialTheme.typography.titleLarge)

            when {
                !hasMic -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "GameSound Pro uses the microphone only while you record a sound. " +
                            "Nothing is recorded in the background and recordings never leave your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { micLauncher.launch(Permissions.RECORD_AUDIO) }) {
                        Text("Allow microphone")
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }

                finishedTake -> {
                    // The metadata form below takes over.
                }

                state == RecorderState.RECORDING || state == RecorderState.PAUSED -> {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        Format.clock(elapsed),
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (state == RecorderState.RECORDING) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    LevelMeter(amplitude = amplitude, paused = state == RecorderState.PAUSED)
                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state == RecorderState.RECORDING) {
                            IconButton(
                                onClick = { recorder.pause() },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) { Icon(Icons.Rounded.Pause, contentDescription = "Pause recording") }
                        } else {
                            IconButton(
                                onClick = { recorder.resume() },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Resume recording") }
                        }
                        IconButton(
                            onClick = {
                                val file = recorder.stop()
                                if (file == null) {
                                    onMessage("Nothing recorded — try again")
                                    recorder.discard()
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                        ) {
                            Icon(
                                Icons.Rounded.Stop,
                                contentDescription = "Stop recording",
                                tint = MaterialTheme.colorScheme.onError,
                            )
                        }
                    }
                    TextButton(onClick = { recorder.discard() }) { Text("Discard") }
                }

                else -> {
                    // IDLE (or a discarded take)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Tap to start recording",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    RecordButton {
                        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!recorder.start()) {
                            onMessage("Couldn't start recording — is another app using the microphone?")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }

            if (finishedTake) {
                val takeFile = recorder.outputFile!!
                val takeDuration = recorder.elapsedMs.value
                Spacer(Modifier.height(8.dp))
                MetadataForm(
                    filePath = takeFile.absolutePath,
                    durationMs = takeDuration,
                    engine = container.audioEngine,
                    suggestedName = "Recording",
                    suggestedCategory = Category.VOICE,
                    onSave = { meta ->
                        scope.launch {
                            container.soundRepository.addRecording(takeFile, meta).fold(
                                onSuccess = {
                                    savedTake = true
                                    onMessage("Saved to My Sounds 🎙️")
                                    onDismiss()
                                },
                                onFailure = { onMessage(it.message ?: "Couldn't save the recording") },
                            )
                        }
                    },
                    onCancel = {
                        recorder.discard()
                        onDismiss()
                    },
                )
            }
        }
    }

    // Level meter + timer ticker while recording.
    LaunchedEffect(state) {
        while (state == RecorderState.RECORDING || state == RecorderState.PAUSED) {
            if (state == RecorderState.RECORDING) {
                amplitude = recorder.amplitude
                recorder.tick()
            }
            delay(80)
        }
        amplitude = 0
    }
}

/** Big red record button with a pulsing halo. */
@Composable
private fun RecordButton(onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "record")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "recordPulse",
    )
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(84.dp)
            .scale(pulse)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error),
    ) {
        Icon(
            Icons.Rounded.FiberManualRecord,
            contentDescription = "Record",
            tint = MaterialTheme.colorScheme.onError,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun LevelMeter(amplitude: Int, paused: Boolean) {
    val level = (amplitude / 32767f).coerceIn(0f, 1f)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(16) { index ->
            val factor = 0.35f + 0.65f * kotlin.math.abs(kotlin.math.sin(index * 1.1f))
            val barHeight = if (paused) 4.dp else 6.dp + 26.dp * level * factor
            Box(
                Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(
                        if (paused) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f + 0.6f * level)
                    )
            )
        }
    }
}
