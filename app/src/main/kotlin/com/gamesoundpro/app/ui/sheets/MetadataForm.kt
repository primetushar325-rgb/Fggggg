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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamesoundpro.app.audio.AudioEngine
import com.gamesoundpro.app.database.entity.SoundPackEntity
import com.gamesoundpro.app.domain.Category
import com.gamesoundpro.app.repository.SoundMeta
import com.gamesoundpro.app.ui.components.ICON_CHOICES
import com.gamesoundpro.app.ui.components.IconPickerDialog
import com.gamesoundpro.app.utils.Format

/**
 * Shared metadata form used by the import flow and the recorder:
 * name, category, emoji icon, per-sound volume and trim (playback window), plus preview.
 */
@Composable
fun MetadataForm(
    filePath: String,
    durationMs: Long,
    engine: AudioEngine,
    suggestedName: String = "",
    suggestedCategory: Category = Category.DEFAULT,
    onSave: (SoundMeta) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(suggestedName) }
    var category by remember { mutableStateOf(suggestedCategory) }
    var icon by remember { mutableStateOf("🎵") }
    var showIconPicker by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(1f) }
    var trimStart by remember { mutableLongStateOf(0L) }
    var trimEnd by remember { mutableLongStateOf(if (durationMs > 0) durationMs else 0L) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Sound name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Category picker (dropdown field).
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
            TextButton(onClick = { showIconPicker = true }) { Text("Choose icon") }
            Spacer(Modifier.weight(1f))
            Text(
                "Volume ${Format.percent((volume / 1.5f).coerceIn(0f, 1f))}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Slider(
            value = volume,
            onValueChange = { volume = it },
            valueRange = 0.1f..1.5f,
        )

        if (durationMs > 0) {
            Text("Trim audio", style = MaterialTheme.typography.labelLarge)
            Text(
                "Start ${Format.duration(trimStart)}   •   End ${Format.duration(trimEnd)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = trimStart.toFloat(),
                onValueChange = { trimStart = it.toLong().coerceIn(0L, (trimEnd - 100).coerceAtLeast(0L)) },
                valueRange = 0f..durationMs.toFloat(),
            )
            Slider(
                value = trimEnd.toFloat(),
                onValueChange = { trimEnd = it.toLong().coerceIn((trimStart + 100), durationMs) },
                valueRange = 0f..durationMs.toFloat(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = {
                engine.playPreview(filePath, volume, trimStart, trimEnd)
            }) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play preview")
                Spacer(Modifier.width(4.dp))
                Text("Preview")
            }
            OutlinedButton(onClick = { engine.stopPreview() }) {
                Icon(Icons.Rounded.Stop, contentDescription = "Stop preview")
                Spacer(Modifier.width(4.dp))
                Text("Stop")
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = {
                    onSave(
                        SoundMeta(
                            name = name.ifBlank { "Sound" },
                            category = category,
                            icon = icon,
                            volume = volume,
                            trimStartMs = trimStart,
                            trimEndMs = trimEnd,
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text("Save Sound") }
        }
    }

    if (showIconPicker) {
        IconPickerDialog(
            current = icon,
            onPick = { picked ->
                icon = picked
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false },
        )
    }
}

@Composable
private fun CategoryField(category: Category, onSelect: (Category) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("${category.emoji} ${category.label}")
            Spacer(Modifier.weight(1f))
            Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Category.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option.emoji} ${option.label}") },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
