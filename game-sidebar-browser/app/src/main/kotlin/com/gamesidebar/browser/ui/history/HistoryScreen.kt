package com.gamesidebar.browser.ui.history

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.gamesidebar.browser.ui.components.SectionTitle
import com.gamesidebar.browser.ui.theme.GameSidebarColors
import com.gamesidebar.core.browser.UrlResolver
import com.gamesidebar.core.data.HistoryEntry
import com.gamesidebar.core.data.HistoryOps
import com.gamesidebar.core.model.AppSettings
import com.gamesidebar.core.util.Formatting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val locator = ServiceLocator.get(application)
    private val repository = locator.browserDataRepository
    private val query = MutableStateFlow("")

    data class UiState(
        val entries: List<HistoryEntry> = emptyList(),
        val settings: AppSettings = AppSettings.DEFAULT,
        val query: String = "",
    )

    val state: StateFlow<UiState> = combine(
        repository.history,
        locator.settingsRepository.settings,
        query,
    ) { list, settings, term ->
        UiState(HistoryOps.search(HistoryOps.sortedNewestFirst(list), term), settings, term)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun search(value: String) {
        query.value = value
    }

    fun delete(entry: HistoryEntry) {
        viewModelScope.launch { repository.deleteHistory(entry.id) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearHistory() }
    }
}

@Composable
fun HistoryScreen(onOpenUrl: (String) -> Unit) {
    val context = LocalContext.current
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HistoryViewModel(context.applicationContext as Application) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::search,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.action_search)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = null,
                    tint = GameSidebarColors.TextMuted,
                )
            },
        )

        if (!state.settings.saveHistory || state.settings.incognito) {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_incognito),
                        contentDescription = null,
                        tint = GameSidebarColors.AccentCyan,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = stringResource(R.string.settings_incognito_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = GameSidebarColors.TextSecondary,
                    )
                }
            }
        }

        if (state.entries.isEmpty()) {
            EmptyState(
                iconRes = R.drawable.ic_history,
                title = stringResource(R.string.history_empty),
                body = stringResource(R.string.history_empty_body),
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Formatting.groupByDay(
                state.entries,
                System.currentTimeMillis(),
                { it.visitedAt },
            ).forEach { (label, entries) ->
                item(key = "header-$label") { SectionTitle(label) }
                items(entries, key = { it.id }) { entry ->
                    HistoryRow(
                        entry = entry,
                        onOpen = { onOpenUrl(entry.url) },
                        onDelete = { viewModel.delete(entry) },
                    )
                }
            }
            item {
                TextButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.action_clear_all), color = GameSidebarColors.AccentRed)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.confirm_clear_history_title)) },
            text = { Text(stringResource(R.string.confirm_clear_history_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    confirmClear = false
                }) { Text(stringResource(R.string.action_clear_all)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onOpen: () -> Unit, onDelete: () -> Unit) {
    GlassCard(modifier = Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                painter = painterResource(id = R.drawable.ic_history),
                contentDescription = null,
                tint = GameSidebarColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = GameSidebarColors.TextPrimary,
                    maxLines = 1,
                )
                Text(
                    text = UrlResolver.displayUrl(entry.url),
                    style = MaterialTheme.typography.bodySmall,
                    color = GameSidebarColors.TextMuted,
                    maxLines = 1,
                )
                Text(
                    text = Formatting.relativeTime(entry.visitedAt, System.currentTimeMillis()),
                    style = MaterialTheme.typography.labelSmall,
                    color = GameSidebarColors.TextMuted,
                )
            }
            IconAction(
                iconRes = R.drawable.ic_delete,
                contentDescription = stringResource(R.string.action_delete),
                onClick = onDelete,
            )
            IconAction(
                iconRes = R.drawable.ic_chevron_right,
                contentDescription = stringResource(R.string.action_search),
                onClick = onOpen,
            )
        }
    }
}
