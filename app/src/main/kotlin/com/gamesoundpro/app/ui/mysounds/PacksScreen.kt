package com.gamesoundpro.app.ui.mysounds

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamesoundpro.app.GameSoundProApp
import com.gamesoundpro.app.database.entity.SoundEntity
import com.gamesoundpro.app.database.entity.SoundPackEntity
import com.gamesoundpro.app.ui.components.EmptyState
import com.gamesoundpro.app.ui.components.SoundTile
import com.gamesoundpro.app.ui.sheets.PackCreateDialog
import com.gamesoundpro.app.ui.sheets.PackDeleteDialog
import com.gamesoundpro.app.ui.theme.GlassSurface
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Pack list
// ---------------------------------------------------------------------------

class PackListViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as GameSoundProApp).container
    private val repository = container.soundRepository

    data class PackWithCount(val pack: SoundPackEntity, val count: Int)

    val packs: StateFlow<List<PackWithCount>> = combine(repository.packs, repository.sounds) { packs, sounds ->
        packs.map { pack ->
            PackWithCount(pack, sounds.count { sound -> sound.packId == pack.id })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createPack(name: String, icon: String) {
        viewModelScope.launch { repository.createPack(name, icon) }
    }

    fun renamePack(pack: SoundPackEntity, name: String, icon: String) {
        viewModelScope.launch { repository.renamePack(pack, name, icon) }
    }

    fun deletePack(pack: SoundPackEntity) {
        viewModelScope.launch { repository.deletePack(pack) }
    }

    fun export(pack: SoundPackEntity, uri: android.net.Uri, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch { onResult(repository.exportPack(pack, uri)) }
    }
}

@Composable
fun PacksScreen(
    onOpenPack: (String) -> Unit,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: PackListViewModel = viewModel(),
) {
    val packs by viewModel.packs.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SoundPackEntity?>(null) }
    var deleting by remember { mutableStateOf<SoundPackEntity?>(null) }
    var exporting by remember { mutableStateOf<SoundPackEntity?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val pack = exporting
        if (uri != null && pack != null) {
            viewModel.export(pack, uri) { result ->
                onResultMessage(result, onMessage)
            }
        }
        exporting = null
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Go back")
            }
            Text("Sound Packs", style = MaterialTheme.typography.headlineMedium)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Create Pack")
            }
        }

        if (packs.isEmpty()) {
            EmptyState(
                emoji = "📦",
                title = "No packs yet",
                subtitle = "Group your sounds into packs — meme packs, troll packs, music packs — and export them to share.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(packs, key = { it.pack.id }) { entry ->
                    PackRow(
                        entry = entry,
                        onClick = { onOpenPack(entry.pack.id) },
                        onRename = { renaming = entry.pack },
                        onDelete = { deleting = entry.pack },
                        onExport = { exporting = entry.pack },
                    )
                }
            }
        }
    }

    if (creating) {
        PackCreateDialog(
            onCreate = { name, icon ->
                viewModel.createPack(name, icon)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
    renaming?.let { pack ->
        PackCreateDialog(
            initialName = pack.name,
            initialIcon = pack.icon,
            title = "Rename Pack",
            confirmLabel = "Save",
            onCreate = { name, icon ->
                viewModel.renamePack(pack, name, icon)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { pack ->
        PackDeleteDialog(
            packName = pack.name,
            onConfirm = {
                viewModel.deletePack(pack)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
    if (exporting != null) {
        exportLauncher.launch("${exporting?.name ?: "pack"}.gsoundpack.zip")
    }
}

private fun onResultMessage(result: Result<Int>, onMessage: (String) -> Unit) {
    onMessage(
        result.fold(
            onSuccess = { count -> "Exported $count sounds 📦" },
            onFailure = { it.message ?: "Couldn't export the pack" },
        )
    )
}

@Composable
private fun PackRow(
    entry: PackListViewModel.PackWithCount,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    GlassSurface(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick), cornerRadius = 16.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .padding(0.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(entry.pack.icon, fontSize = 20.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.pack.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${entry.count} sound${if (entry.count == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Export") },
                        leadingIcon = { Icon(Icons.Rounded.DriveFileMove, contentDescription = null) },
                        onClick = { menuOpen = false; onExport() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pack detail
// ---------------------------------------------------------------------------

class PackViewModel(app: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(app) {

    private val container = (app as GameSoundProApp).container
    private val repository = container.soundRepository
    private val packId: String = checkNotNull(savedStateHandle["packId"])

    val pack: StateFlow<SoundPackEntity?> = repository.observePackEntity(packId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val members: StateFlow<List<SoundEntity>> = repository.observePack(packId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeSounds = container.audioEngine.activeSounds

    fun rename(name: String, icon: String) {
        val current = pack.value ?: return
        viewModelScope.launch { repository.renamePack(current, name, icon) }
    }

    fun delete(onDone: () -> Unit) {
        val current = pack.value ?: return onDone()
        viewModelScope.launch {
            repository.deletePack(current)
            onDone()
        }
    }

    fun export(uri: android.net.Uri, onResult: (Result<Int>) -> Unit) {
        val current = pack.value ?: return
        viewModelScope.launch { onResult(repository.exportPack(current, uri)) }
    }

    fun reorder(sound: SoundEntity, delta: Int) {
        viewModelScope.launch { repository.reorderSound(sound, delta) }
    }

    fun play(sound: SoundEntity) = container.audioEngine.playSound(sound)
}

@Composable
fun PackDetailScreen(
    onBack: () -> Unit,
    onEditSound: (SoundEntity) -> Unit,
    onMessage: (String) -> Unit,
    viewModel: PackViewModel = viewModel(),
) {
    val pack by viewModel.pack.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val active by viewModel.activeSounds.collectAsStateWithLifecycle()
    val activeIds = active.map { it.id }.toSet()

    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.export(uri) { result ->
                onMessage(
                    result.fold(
                        onSuccess = { count -> "Exported $count sounds 📦" },
                        onFailure = { it.message ?: "Couldn't export the pack" },
                    )
                )
            }
        }
        exporting = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Go back")
            }
            Text(pack?.icon ?: "📦", fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(pack?.name ?: "Pack", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${members.size} sound${if (members.size == 1) "" else "s"} — long-press a sound to manage it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { exporting = true }) {
                Icon(Icons.Rounded.DriveFileMove, contentDescription = "Export pack", tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (members.isEmpty()) {
            EmptyState(
                emoji = "📂",
                title = "This pack is empty",
                subtitle = "Use Edit on any sound and choose \"Move to pack\" to fill it.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                gridItems(members, key = { it.id }) { sound ->
                    SoundTile(
                        sound = sound,
                        isPlaying = sound.id in activeIds,
                        reorderable = true,
                        onPlay = { viewModel.play(sound) },
                        onLongClick = { onEditSound(sound) },
                        onReorder = { delta -> viewModel.reorder(sound, delta) },
                    )
                }
            }
        }
    }

    if (renaming && pack != null) {
        PackCreateDialog(
            initialName = pack!!.name,
            initialIcon = pack!!.icon,
            title = "Rename Pack",
            confirmLabel = "Save",
            onCreate = { name, icon ->
                viewModel.rename(name, icon)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    if (deleting && pack != null) {
        PackDeleteDialog(
            packName = pack!!.name,
            onConfirm = {
                viewModel.delete { onBack() }
                deleting = false
            },
            onDismiss = { deleting = false },
        )
    }
    LaunchedEffect(exporting) {
        if (exporting) exportLauncher.launch("${pack?.name ?: "pack"}.gsoundpack.zip")
    }
}
