package com.gamesoundpro.app.ui.sheets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamesoundpro.app.di.AppContainer
import com.gamesoundpro.app.domain.Category
import com.gamesoundpro.app.repository.SoundMeta
import com.gamesoundpro.app.utils.AudioFiles
import kotlinx.coroutines.launch
import java.io.File

/**
 * "+ ADD SOUND" sheet: import one file with full customization, record voice, quick-import
 * several files from the device, or create a pack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSoundSheet(
    container: AppContainer,
    onDismiss: () -> Unit,
    onOpenRecord: () -> Unit,
    onCreatePack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = container.soundRepository

    // Staged file (already copied into app storage) awaiting metadata + save.
    var staged by remember { mutableStateOf<Pair<File, Long>?>(null) }
    var suggestedName by remember { mutableStateOf("") }

    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                repository.stageImport(uri).fold(
                    onSuccess = { (file, duration) ->
                        staged = file to duration
                        suggestedName = AudioFiles.baseName(file.name)
                    },
                    onFailure = { onMessage(it.message ?: "Couldn't import that file") },
                )
            }
        }
    }

    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                var added = 0
                var failed = 0
                uris.forEach { uri: Uri ->
                    val name = AudioFiles.baseName(uri.lastPathSegment ?: "sound")
                    val result = repository.addSoundFromUri(
                        uri,
                        SoundMeta(name = name, category = Category.CUSTOM, icon = "🎵"),
                    )
                    if (result.isSuccess) added++ else failed++
                }
                onMessage(
                    when {
                        failed == 0 -> "Added $added sound${if (added == 1) "" else "s"} 🎵"
                        added == 0 -> "Couldn't import — unsupported format?"
                        else -> "Added $added, skipped $failed (unsupported format)"
                    }
                )
                if (added > 0) onDismiss()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Add New Sound", style = MaterialTheme.typography.titleLarge)
            Text(
                "MP3 · WAV · M4A · OGG · AAC · FLAC · OPUS — stays on your device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(top = 10.dp))

            val stagedFile = staged
            if (stagedFile == null) {
                OptionRow("📂", "Import Audio File", "Pick one file, name it, trim it") {
                    singlePicker.launch(arrayOf("audio/*"))
                }
                OptionRow("🎙️", "Record Voice", "Use your microphone") {
                    onDismiss()
                    onOpenRecord()
                }
                OptionRow("📱", "Import From Device", "Quick-add several files at once") {
                    multiPicker.launch(arrayOf("audio/*"))
                }
                OptionRow("📦", "Create Sound Pack", "Group sounds together") {
                    onDismiss()
                    onCreatePack()
                }
                Spacer(Modifier.padding(bottom = 24.dp))
            } else {
                MetadataForm(
                    filePath = stagedFile.first.absolutePath,
                    durationMs = stagedFile.second,
                    engine = container.audioEngine,
                    suggestedName = suggestedName,
                    onSave = { meta ->
                        scope.launch {
                            repository.addStagedFile(stagedFile.first, meta).fold(
                                onSuccess = {
                                    onMessage("Saved to My Sounds ✅")
                                    onDismiss()
                                },
                                onFailure = { onMessage(it.message ?: "Couldn't save the sound") },
                            )
                        }
                    },
                    onCancel = {
                        // Remove the staged copy so nothing is left behind.
                        stagedFile.first.delete()
                        staged = null
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun OptionRow(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 24.sp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
