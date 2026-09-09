package com.gamesoundpro.app.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamesoundpro.app.database.entity.SoundEntity
import com.gamesoundpro.app.di.AppContainer
import com.gamesoundpro.app.domain.Category
import com.gamesoundpro.app.repository.SoundMeta
import com.gamesoundpro.app.ui.components.IconPickerDialog
import com.gamesoundpro.app.ui.theme.GlassSurface
import com.gamesoundpro.app.utils.Format
import kotlinx.coroutines.launch

/**
 * Long-press management sheet: rename, change icon/category, volume, trim, duplicate,
 * move to pack and delete (with confirmation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSoundSheet(
    sound: SoundEntity,
    packs: List<com.gamesoundpro.app.database.entity.SoundPackEntity>,
    container: AppContainer,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repository = container.soundRepository

    var name by remember { mutableStateOf(sound.name) }
    var category by remember { mutableStateOf(Category.fromKey(sound.category)) }
    var icon by remember { mutableStateOf(sound.icon) }
    var volume by remember { mutableFloatStateOf(sound.volume) }
    var trimStart by remember { mutableLongStateOf(sound.trimStartMs) }
    var trimEnd by remember { mutableLongStateOf(if (sound.trimEndMs > 0) sound.trimEndMs else sound.durationMs) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Edit Sound", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Rename") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            CategoryField(category = category, onSelect = { category = it })

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        .clickable { showIconPicker = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(icon, fontSize = 20.sp)
                }
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = { showIconPicker = true }) { Text("Change icon") }
                Spacer(Modifier.weight(1f))
                Text("Pack: ${packs.firstOrNull { it.id == sound.packId }?.name ?: "None"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("Volume ${Format.percent((volume / 1.5f).coerceIn(0f, 1f))}", style = MaterialTheme.typography.labelMedium)
            Slider(value = volume, onValueChange = { volume = it }, valueRange = 0.1f..1.5f)

            if (sound.durationMs > 0) {
                Text("Trim", style = MaterialTheme.typography.labelLarge)
                Text(
                    "${Format.duration(trimStart)} → ${Format.duration(trimEnd)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = trimStart.toFloat(),
                    onValueChange = { trimStart = it.toLong().coerceIn(0L, (trimEnd - 100).coerceAtLeast(0L)) },
                    valueRange = 0f..sound.durationMs.toFloat(),
                )
                Slider(
                    value = trimEnd.toFloat(),
                    onValueChange = { trimEnd = it.toLong().coerceIn(trimStart + 100, sound.durationMs) },
                    valueRange = 0f..sound.durationMs.toFloat(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        repository.duplicateSound(sound).fold(
                            onSuccess = { onMessage("Duplicated ✨"); onDismiss() },
                            onFailure = { onMessage("Couldn't duplicate") },
                        )
                    }
                }, modifier = Modifier.weight(1f)) { Text("Duplicate") }
                OutlinedButton(onClick = { showMoveDialog = true }, modifier = Modifier.weight(1f)) { Text("Move to Pack") }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        scope.launch {
                            container.audioEngine.stopSound(sound.id)
                            repository.updateSound(
                                sound.copy(
                                    name = name.ifBlank { sound.name },
                                    category = category.key,
                                    icon = icon,
                                    volume = volume,
                                    trimStartMs = trimStart,
                                    trimEndMs = trimEnd,
                                )
                            ).let {
                                onMessage("Saved ✅")
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }

            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
            ) {
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text("Delete this sound", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showIconPicker) {
        IconPickerDialog(current = icon, onPick = { icon = it; showIconPicker = false }, onDismiss = { showIconPicker = false })
    }

    if (showMoveDialog) {
        MoveToPackDialog(
            currentPackId = sound.packId,
            packs = packs,
            onPick = { packId ->
                scope.launch {
                    repository.moveSoundToPack(sound.id, packId)
                    onMessage("Moved 📦")
                    showMoveDialog = false
                }
            },
            onDismiss = { showMoveDialog = false },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this sound?") },
            text = { Text("\"${sound.name}\" and its audio file will be removed from your device.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.audioEngine.stopSound(sound.id)
                        repository.deleteSound(sound).fold(
                            onSuccess = { onMessage("Deleted 🗑️"); onDismiss() },
                            onFailure = { onMessage("Couldn't delete the file") },
                        )
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MoveToPackDialog(
    currentPackId: String?,
    packs: List<com.gamesoundpro.app.database.entity.SoundPackEntity>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Pack") },
        text = {
            Column {
                TextButton(onClick = { onPick(null) }) {
                    Text(if (currentPackId == null) "✓ No pack" else "No pack")
                }
                packs.forEach { pack ->
                    TextButton(onClick = { onPick(pack.id) }) {
                        Text(if (pack.id == currentPackId) "✓ ${pack.icon} ${pack.name}" else "${pack.icon} ${pack.name}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
