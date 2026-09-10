package com.gamesidebar.browser.ui.notes

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamesidebar.browser.R
import com.gamesidebar.browser.data.ServiceLocator
import com.gamesidebar.browser.ui.components.EmptyState
import com.gamesidebar.browser.ui.components.GlassCard
import com.gamesidebar.browser.ui.components.IconAction
import com.gamesidebar.browser.ui.theme.GameSidebarColors
import com.gamesidebar.core.data.Note
import com.gamesidebar.core.data.NoteOps
import com.gamesidebar.core.util.Formatting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceLocator.get(application).browserDataRepository
    private val query = MutableStateFlow("")

    data class UiState(val notes: List<Note> = emptyList(), val query: String = "")

    val state: StateFlow<UiState> = combine(repository.notes, query) { list, term ->
        UiState(NoteOps.search(NoteOps.sorted(list), term), term)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun search(value: String) {
        query.value = value
    }

    fun create(title: String, body: String) {
        viewModelScope.launch { repository.createNote(title, body) }
    }

    fun update(id: Long, title: String, body: String) {
        viewModelScope.launch { repository.updateNote(id, title, body) }
    }

    fun togglePin(id: Long) {
        viewModelScope.launch { repository.toggleNotePin(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteNote(id) }
    }
}

@Composable
fun NotesScreen() {
    val context = LocalContext.current
    val viewModel: NotesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { NotesViewModel(context.applicationContext as Application) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<Note?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = GameSidebarColors.Background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { creating = true },
                containerColor = GameSidebarColors.AccentBlue,
                contentColor = GameSidebarColors.TextPrimary,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add),
                    contentDescription = stringResource(R.string.notes_new),
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::search,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.notes_search)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = null,
                        tint = GameSidebarColors.TextMuted,
                    )
                },
            )

            if (state.notes.isEmpty()) {
                EmptyState(
                    iconRes = R.drawable.ic_notes,
                    title = stringResource(R.string.notes_empty),
                    body = stringResource(R.string.notes_empty_body),
                    modifier = Modifier.fillMaxSize(),
                )
                return@Column
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(state.notes, key = { it.id }) { note ->
                    GlassCard(modifier = Modifier.padding(vertical = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                painter = painterResource(
                                    id = if (note.pinned) R.drawable.ic_bookmark_filled else R.drawable.ic_notes,
                                ),
                                contentDescription = null,
                                tint = if (note.pinned) GameSidebarColors.AccentBlue else GameSidebarColors.TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = note.title.ifBlank { note.preview },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = GameSidebarColors.TextPrimary,
                                    maxLines = 1,
                                )
                                Text(
                                    text = note.preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GameSidebarColors.TextMuted,
                                    maxLines = 2,
                                )
                                Text(
                                    text = Formatting.relativeTime(note.updatedAt, System.currentTimeMillis()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GameSidebarColors.TextMuted,
                                )
                            }
                            IconAction(
                                iconRes = R.drawable.ic_pin,
                                contentDescription = stringResource(R.string.notes_pin),
                                onClick = { viewModel.togglePin(note.id) },
                            )
                            IconAction(
                                iconRes = R.drawable.ic_edit,
                                contentDescription = stringResource(R.string.action_edit),
                                onClick = { editing = note },
                            )
                            IconAction(
                                iconRes = R.drawable.ic_delete,
                                contentDescription = stringResource(R.string.action_delete),
                                onClick = { viewModel.delete(note.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        NoteDialog(
            title = stringResource(R.string.notes_new),
            initialTitle = "",
            initialBody = "",
            onDismiss = { creating = false },
            onConfirm = { title, body ->
                viewModel.create(title, body)
                creating = false
            },
        )
    }

    editing?.let { note ->
        NoteDialog(
            title = stringResource(R.string.action_edit),
            initialTitle = note.title,
            initialBody = note.body,
            onDismiss = { editing = null },
            onConfirm = { title, body ->
                viewModel.update(note.id, title, body)
                editing = null
            },
        )
    }
}

@Composable
private fun NoteDialog(
    title: String,
    initialTitle: String,
    initialBody: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var titleText by remember { mutableStateOf(initialTitle) }
    var bodyText by remember { mutableStateOf(initialBody) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text(stringResource(R.string.notes_hint_title)) },
                    singleLine = true,
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = bodyText,
                    onValueChange = { bodyText = it },
                    label = { Text(stringResource(R.string.notes_hint_body)) },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(titleText, bodyText) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
