package com.gamesoundpro.app.ui.player

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gamesoundpro.app.GameSoundProApp
import com.gamesoundpro.app.domain.MusicState
import com.gamesoundpro.app.utils.AudioFiles
import com.gamesoundpro.app.utils.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Drives the mini music player from the UI layer. */
class PlaybackViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as GameSoundProApp).container
    private val repository = container.soundRepository

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val musicState: StateFlow<MusicState> = container.audioEngine.musicState

    val musicVolume: StateFlow<Float> = container.settingsRepository.settings
        .map { it.mixer.music }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.6f)

    /** Play/pause; when nothing is queued yet, starts the Music-category playlist. */
    fun playPause() {
        if (container.audioEngine.musicState.value.track == null) {
            viewModelScope.launch {
                val tracks = repository.musicTracks.first()
                if (tracks.isEmpty()) {
                    _messages.tryEmit("Tag a sound with the Music category to build your playlist 🎵")
                } else {
                    container.audioEngine.playMusic(tracks, 0)
                }
            }
        } else {
            container.audioEngine.playPauseMusic()
        }
    }
    fun next() = container.audioEngine.nextTrack()
    fun previous() = container.audioEngine.previousTrack()
    fun seek(positionMs: Long) = container.audioEngine.seekMusicTo(positionMs)
    fun stop() = container.audioEngine.stopMusic()
    fun setLoop(enabled: Boolean) = container.audioEngine.setMusicLoop(enabled)
    fun setShuffle(enabled: Boolean) = container.audioEngine.setMusicShuffle(enabled)

    /** Starts the playlist: every sound tagged with the Music category. */
    fun playFromLibrary(onEmpty: () -> Unit = {}) {
        viewModelScope.launch {
            val tracks = repository.musicTracks.first()
            if (tracks.isEmpty()) onEmpty() else container.audioEngine.playMusic(tracks, 0)
        }
    }

    fun setMusicVolume(value: Float) {
        container.audioEngine.setMusicVolumeLive(value)
        viewModelScope.launch {
            container.settingsRepository.setMixerVolumes(
                container.settingsRepository.snapshot.mixer.copy(music = value)
            )
        }
    }
}

/** Compact persistent bar shown while music is loaded (above the bottom navigation). */
@Composable
fun MiniPlayerBar(
    state: MusicState,
    viewModel: PlaybackViewModel,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpen) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) { Text("🎵") }
        }
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpen),
        ) {
            Text(
                track.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${Format.duration(state.positionMs)} / ${Format.duration(state.durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressThin(state.positionMs, state.durationMs)
        }
        IconButton(onClick = viewModel::playPause) {
            Icon(
                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Play or pause",
            )
        }
        IconButton(onClick = viewModel::next) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Next track")
        }
        IconButton(onClick = viewModel::stop) {
            Icon(Icons.Rounded.Close, contentDescription = "Stop playback", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun LinearProgressThin(positionMs: Long, durationMs: Long) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

/** Expanded music player with artwork, seek, loop/shuffle and volume. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(
    state: MusicState,
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    val track = state.track
    val context = LocalContext.current

    if (track == null) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🎵", fontSize = 44.sp)
                Spacer(Modifier.height(10.dp))
                Text("No music queued", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Give any sound the Music category and it joins your playlist — import full tracks " +
                        "and control them here with loop, shuffle and seek.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = viewModel::playPause) { Text("Play music library") }
            }
        }
        return
    }

    val trackMeta by produceState<AudioFiles.TrackMeta?>(initialValue = null, key1 = track.filePath) {
        value = withContext(Dispatchers.IO) { AudioFiles.extractTrackMeta(track.filePath) }
    }
    val artwork = trackMeta?.artwork

    var dragging by remember { mutableFloatStateOf(-1f) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (artwork != null) {
                    Image(
                        bitmap = artwork!!.asImageBitmap(),
                        contentDescription = "Album artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(180.dp),
                    )
                } else {
                    Text("🎵", modifier = Modifier.size(64.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(track.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val artist = trackMeta?.artist
            if (!artist.isNullOrBlank()) {
                Text(
                    artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${Format.duration(state.positionMs)} / ${Format.duration(state.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Slider(
                value = if (dragging >= 0f) dragging else state.positionMs.toFloat(),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    viewModel.seek(dragging.toLong())
                    dragging = -1f
                },
                valueRange = 0f..(state.durationMs.takeIf { it > 0 }?.toFloat() ?: 1f),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.setShuffle(!state.shuffleEnabled) }) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = viewModel::previous) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous track")
                }
                IconButton(
                    onClick = viewModel::playPause,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play or pause",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = viewModel::next) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next track")
                }
                IconButton(onClick = { viewModel.setLoop(!state.loopEnabled) }) {
                    Icon(
                        if (state.loopEnabled) Icons.Rounded.Repeat else Icons.Rounded.Repeat,
                        contentDescription = "Loop",
                        tint = if (state.loopEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            val musicVolume by viewModel.musicVolume.collectAsStateWithLifecycle()
            Text("Music volume", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = musicVolume,
                onValueChange = viewModel::setMusicVolume,
                valueRange = 0f..1f,
            )
        }
    }
}
