package com.gamesidebar.browser.ui.bookmarks

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.gamesidebar.core.browser.UrlResolver
import com.gamesidebar.core.data.Bookmark
import com.gamesidebar.core.data.BookmarkOps
import com.gamesidebar.core.util.Formatting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookmarksViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceLocator.get(application).browserDataRepository

    private val query = MutableStateFlow("")

    data class UiState(val bookmarks: List<Bookmark> = emptyList(), val query: String = "")

    val state: StateFlow<UiState> = combine(repository.bookmarks, query) { list, term ->
        UiState(BookmarkOps.search(BookmarkOps.sortedNewestFirst(list), term), term)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun search(value: String) {
        query.value = value
    }

    fun delete(bookmark: Bookmark) {
        viewModelScope.launch { repository.deleteBookmark(bookmark.id) }
    }

    fun rename(bookmark: Bookmark, title: String) {
        viewModelScope.launch { repository.renameBookmark(bookmark.id, title) }
    }
}

@Composable
fun BookmarksScreen(onOpenUrl: (String) -> Unit) {
    val context = LocalContext.current
    val viewModel: BookmarksViewModel = viewModel(
        factory = viewModelFactory {
            initializer { BookmarksViewModel(context.applicationContext as Application) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.bookmarks.isEmpty()) {
        com.gamesidebar.browser.ui.components.EmptyState(
            iconRes = R.drawable.ic_bookmark,
            title = stringResource(R.string.bookmarks_empty),
            body = stringResource(R.string.bookmarks_empty_body),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(state.bookmarks, key = { it.id }) { bookmark ->
            BookmarkRow(
                bookmark = bookmark,
                onOpen = { onOpenUrl(bookmark.url) },
                onDelete = { viewModel.delete(bookmark) },
                onEdit = { viewModel.rename(bookmark, it) },
            )
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(bookmark.title) }

    com.gamesidebar.browser.ui.components.GlassCard(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                painter = painterResource(id = R.drawable.ic_browser),
                contentDescription = null,
                tint = com.gamesidebar.browser.ui.theme.GameSidebarColors.AccentBlue,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (editing) {
                    androidx.compose.material3.OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.shortcuts_name)) },
                    )
                } else {
                    Text(
                        text = bookmark.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = com.gamesidebar.browser.ui.theme.GameSidebarColors.TextPrimary,
                    )
                    Text(
                        text = UrlResolver.displayUrl(bookmark.url),
                        style = MaterialTheme.typography.bodySmall,
                        color = com.gamesidebar.browser.ui.theme.GameSidebarColors.TextMuted,
                        maxLines = 1,
                    )
                    Text(
                        text = Formatting.relativeTime(bookmark.updatedAt, System.currentTimeMillis()),
                        style = MaterialTheme.typography.labelSmall,
                        color = com.gamesidebar.browser.ui.theme.GameSidebarColors.TextMuted,
                    )
                }
            }
            if (editing) {
                com.gamesidebar.browser.ui.components.IconAction(
                    iconRes = R.drawable.ic_check,
                    contentDescription = stringResource(R.string.action_save),
                    onClick = {
                        onEdit(draft)
                        editing = false
                    },
                )
            } else {
                com.gamesidebar.browser.ui.components.IconAction(
                    iconRes = R.drawable.ic_edit,
                    contentDescription = stringResource(R.string.action_edit),
                    onClick = {
                        draft = bookmark.title
                        editing = true
                    },
                )
                com.gamesidebar.browser.ui.components.IconAction(
                    iconRes = R.drawable.ic_delete,
                    contentDescription = stringResource(R.string.action_delete),
                    onClick = onDelete,
                )
            }
            com.gamesidebar.browser.ui.components.IconAction(
                iconRes = R.drawable.ic_chevron_right,
                contentDescription = stringResource(R.string.action_search),
                onClick = onOpen,
            )
        }
    }
}
