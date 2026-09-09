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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamesoundpro.app.audio.AudioState
import com.gamesoundpro.app.audio.FocusState
import com.gamesoundpro.app.audio.RecorderState
import com.gamesoundpro.app.permissions.Permissions
import com.gamesoundpro.app.ui.components.SectionHeader
import com.gamesoundpro.app.ui.theme.GlassSurface
import com.gamesoundpro.app.utils.Format

/**
 * Audio Routing Diagnostics — shows the device's real audio capabilities and runs the
 * three-step AUDIO TEST. It exists to answer one question honestly: what can this device
 * do? Cross-app microphone loopback is reported as NOT AVAILABLE when Android provides no
 * supported path — the app never fakes success.
 */
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val active by viewModel.activeSounds.collectAsStateWithLifecycle()
    val music by viewModel.musicState.collectAsStateWithLifecycle()
    val overlayActive by viewModel.overlayActive.collectAsStateWithLifecycle()
    val mediaVolume by viewModel.mediaVolume.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val heardAnswer by viewModel.heardAnswer.collectAsStateWithLifecycle()
    val micTest by viewModel.micTest.collectAsStateWithLifecycle()
    val recorderState by viewModel.recorderState.collectAsStateWithLifecycle()
    val recorderElapsed by viewModel.recorderElapsed.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // Re-read so the UI shows the honest DENIED state.
            Permissions.hasRecordAudio(context)
        }
    }

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
            Text("Audio Routing Diagnostics", style = MaterialTheme.typography.headlineMedium)
        }

        // ---------------- Status ----------------
        SectionHeader("STATUS")
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                DiagRow(
                    "Microphone Permission",
                    if (viewModel.hasMicPermission()) "GRANTED" else "DENIED",
                )
                DiagRow(
                    "Overlay Permission",
                    if (viewModel.hasOverlayPermission()) "GRANTED" else "DENIED",
                )
                DiagRow("Audio Engine", if (snapshot.state == AudioState.ERROR) "ERROR" else "READY")
                DiagRow(
                    "Audio Focus",
                    when (snapshot.focus) {
                        FocusState.HELD -> "GAIN (held)"
                        FocusState.LOST -> "LOSS"
                        FocusState.NONE -> "NONE (independent)"
                    },
                )
                DiagRow("Current Output", viewModel.audioRoute())
                DiagRow(
                    "Current Playback",
                    when {
                        active.isNotEmpty() -> "PLAYING"
                        music.isPlaying -> "PLAYING (music)"
                        music.track != null -> "PAUSED"
                        else -> "STOPPED"
                    },
                )
                DiagRow(
                    "Microphone",
                    when {
                        !viewModel.hasMicPermission() -> "UNAVAILABLE (permission)"
                        viewModel.microphoneHardwareAvailable() -> "AVAILABLE"
                        else -> "UNAVAILABLE (no hardware)"
                    },
                )
                DiagRow("Gaming Mode", if (settings.gamingMode) "ON" else "OFF")
                DiagRow("Master Volume", "${(settings.mixer.master * 100).toInt()}%")
                DiagRow("Media Stream", "$mediaVolume%")
            }
        }

        // ---------------- Audio modes ----------------
        SectionHeader("AUDIO MODES")
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "Only modes this device actually supports are shown. Output routing always " +
                        "follows Android — plugging a headset or connecting Bluetooth switches " +
                        "the output automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(4.dp))
                viewModel.audioModes().forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = selectedMode == mode.key,
                            onClick = { viewModel.selectMode(mode.key) },
                            label = { Text(mode.label) },
                            enabled = mode.available,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            mode.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ---------------- AUDIO TEST ----------------
        SectionHeader("AUDIO TEST")
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // Step 1 — test tone.
                Text("Step 1 · Test tone", style = MaterialTheme.typography.titleSmall)
                Button(onClick = { viewModel.testSound() }, modifier = Modifier.fillMaxWidth()) {
                    Text("🔔 TEST SOUND")
                }
                testResult?.let { result ->
                    if (result.success) {
                        Text("✓ App playback started", color = MaterialTheme.colorScheme.primary)
                        Text("Can you hear this sound?", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = heardAnswer == HeardAnswer.YES,
                                onClick = { viewModel.setHeardAnswer(HeardAnswer.YES) },
                                label = { Text("YES") },
                            )
                            FilterChip(
                                selected = heardAnswer == HeardAnswer.NO,
                                onClick = { viewModel.setHeardAnswer(HeardAnswer.NO) },
                                label = { Text("NO") },
                            )
                        }
                        if (heardAnswer == HeardAnswer.NO) {
                            Text(
                                "If the tone is audible on the speaker but teammates can't hear it " +
                                    "in-game, that is expected — see the cross-app note below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            "⚠ Playback failed: ${result.failureReason ?: "unknown"}",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (mediaVolume == 0) {
                    Text(
                        "Media volume is 0 — sounds will be silent. Press a volume key and " +
                            "raise \"Media\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // Step 2 — microphone sample (explicit user action only).
                Text("Step 2 · Microphone test (3 s)", style = MaterialTheme.typography.titleSmall)
                when (val state = micTest) {
                    is MicTestState.Recording -> {
                        val remaining = (DiagnosticsViewModel.MIC_TEST_MS - recorderElapsed)
                            .coerceIn(0L, DiagnosticsViewModel.MIC_TEST_MS)
                        Text(
                            "🔴 RECORDING — ${Format.clock(recorderElapsed)} " +
                                "(auto-stops in ${Format.clock(remaining)})",
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(
                            onClick = { viewModel.stopMicTest() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("⏹ STOP NOW") }
                    }

                    is MicTestState.Recorded -> {
                        Text(
                            "✓ Recorded ${Format.duration(state.durationMs)} — stays on this device",
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { viewModel.playRecording() }) { Text("▶ Play my recording") }
                            OutlinedButton(onClick = { viewModel.discardRecording() }) { Text("Discard") }
                        }
                    }

                    is MicTestState.Failed -> {
                        Text("⚠ ${state.reason}", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.startMicTest() }) { Text("Try again") }
                        if (!viewModel.hasMicPermission()) {
                            OutlinedButton(onClick = {
                                micPermissionLauncher.launch(Permissions.RECORD_AUDIO)
                            }) { Text("Allow microphone") }
                        }
                    }

                    MicTestState.Idle -> {
                        OutlinedButton(
                            onClick = { viewModel.startMicTest() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("🎙 Record a 3-second sample") }
                    }
                }
                if (recorderState == RecorderState.RECORDING && micTest !is MicTestState.Recording) {
                    Text("🔴 RECORDING", color = MaterialTheme.colorScheme.error)
                }

                // Step 3 — verdict.
                Text("Step 3 · Result", style = MaterialTheme.typography.titleSmall)
                Column {
                    DiagRow(
                        "DEVICE AUDIO",
                        when {
                            heardAnswer == HeardAnswer.YES -> "✓ Working"
                            heardAnswer == HeardAnswer.NO -> "✗ Not audible on this device"
                            else -> "— run step 1"
                        },
                    )
                    DiagRow(
                        "MICROPHONE",
                        when (val state = micTest) {
                            is MicTestState.Recorded -> "✓ Working"
                            is MicTestState.Failed -> "✗ ${state.reason}"
                            else -> "— run step 2"
                        },
                    )
                    DiagRow(
                        "APP PLAYBACK",
                        when {
                            testResult?.success == true -> "✓ Working"
                            testResult != null -> "✗ Failed"
                            else -> "— run step 1"
                        },
                    )
                    DiagRow(
                        "CROSS-APP MIC LOOPBACK",
                        "NOT AVAILABLE THROUGH STANDARD ANDROID API",
                    )
                }
            }
        }

        // ---------------- Honest limitation ----------------
        SectionHeader("GAME VOICE CHAT")
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "Your device/Android audio system does not provide a supported way for " +
                        "this app to send its playback directly into another app's microphone " +
                        "input.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.padding(4.dp))
                Text(
                    "That is why you hear BRUH, ALARM and EXPLOSION on your speaker while your " +
                        "teammates in Free Fire voice chat do not. GameSound Pro never modifies " +
                        "games, never injects code, and never touches another app's microphone " +
                        "stream — those would be game modifications, not soundboard features. " +
                        "What works reliably: play sounds out loud through your speaker or " +
                        "headset, or use your game's own audio-sharing features if it has them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            modifier = Modifier.width(150.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}
