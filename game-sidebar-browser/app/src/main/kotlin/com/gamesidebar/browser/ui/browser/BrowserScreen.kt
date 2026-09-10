package com.gamesidebar.browser.ui.browser

import android.app.Application
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamesidebar.browser.R
import com.gamesidebar.browser.browser.BrowserController
import com.gamesidebar.browser.browser.WebViewFactory
import com.gamesidebar.browser.data.ServiceLocator
import com.gamesidebar.browser.ui.components.ErrorState
import com.gamesidebar.browser.ui.components.IconAction
import com.gamesidebar.browser.ui.theme.GameSidebarColors
import com.gamesidebar.core.browser.TabSnapshot
import com.gamesidebar.core.browser.Tabs
import com.gamesidebar.core.download.DownloadRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The in-app browser.
 *
 * Same [BrowserController] as the floating panel, so both surfaces behave identically - the only
 * difference is the container. This screen exists for users who never grant SYSTEM_ALERT_WINDOW:
 * the app is still a full browser without the overlay.
 */
class BrowserViewModel(application: Application) : AndroidViewModel(application), BrowserController.Listener {

    data class UiState(
        val tabs: Tabs = Tabs(),
        val url: String = "",
        val title: String = "",
        val progress: Int = 0,
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        val isLoading: Boolean = false,
        val isBookmarked: Boolean = false,
        val errorMessageKey: String? = null,
        val errorUrl: String = "",
        val pendingDownload: DownloadRequest? = null,
        val fullscreenView: View? = null,
        val webviewAvailable: Boolean = true,
    )

    private val locator = ServiceLocator.get(application)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val controller: BrowserController? = if (WebViewFactory.isWebViewAvailable(application)) {
        BrowserController(
            context = application,
            dataRepository = locator.browserDataRepository,
            settings = { currentSettings },
            listener = this,
        )
    } else {
        null
    }

    @Volatile
    private var currentSettings = com.gamesidebar.core.model.AppSettings.DEFAULT

    init {
        if (controller == null) {
            _state.update { it.copy(webviewAvailable = false) }
        }
        viewModelScope.launch {
            locator.settingsRepository.settings.collect { settings ->
                currentSettings = settings
                controller?.applySettings(settings)
            }
        }
    }

    fun start(url: String?) {
        val browser = controller ?: return
        if (_state.value.tabs.isEmpty) {
            browser.start(currentSettings.searchEngine.homeUrl)
            url?.let { browser.submit(it) }
        }
    }

    fun submit(input: String) = controller?.submit(input)
    fun back() = controller?.back()
    fun forward() = controller?.forward()
    fun reload() = controller?.reload()
    fun stopLoading() = controller?.stop()
    fun home() = controller?.home()
    fun selectTab(id: String) = controller?.selectTab(id)
    fun closeTab(id: String) = controller?.closeTab(id)
    fun newTab() = controller?.newTab()
    fun toggleBookmark() = controller?.toggleBookmark()
    fun toggleDesktopMode() = controller?.toggleDesktopMode()
    fun openExternally() = controller?.openExternally()

    fun acceptDownload() {
        val request = _state.value.pendingDownload ?: return
        viewModelScope.launch(Dispatchers.Main) {
            downloads?.enqueue(request)
            _state.update { it.copy(pendingDownload = null) }
        }
    }

    fun rejectDownload() = _state.update { it.copy(pendingDownload = null) }

    fun clearError() = _state.update { it.copy(errorMessageKey = null) }

    private var downloads: com.gamesidebar.browser.browser.DownloadCoordinator? = null

    /** Called once when the WebView container exists. */
    fun attach(container: android.view.ViewGroup) {
        val browser = controller ?: return
        browser.attachContainer(container)
        downloads = browser.downloadListener()
    }

    fun detach() {
        controller?.detachContainer()
    }

    override fun onCleared() {
        controller?.destroy()
        super.onCleared()
    }

    // ------------------------------------------------------------ BrowserController.Listener

    override fun onTabsChanged(tabs: Tabs) = _state.update { it.copy(tabs = tabs) }

    override fun onProgress(progress: Int) = _state.update {
        it.copy(progress = progress, isLoading = progress in 1..99)
    }

    override fun onUrlChanged(url: String, title: String) = _state.update { it.copy(url = url, title = title) }

    override fun onNavigationChanged(canGoBack: Boolean, canGoForward: Boolean, isLoading: Boolean) =
        _state.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward, isLoading = isLoading) }

    override fun onBookmarkChanged(isBookmarked: Boolean) = _state.update { it.copy(isBookmarked = isBookmarked) }

    override fun onLoadError(messageKey: String, url: String) = _state.update {
        it.copy(errorMessageKey = messageKey, errorUrl = url)
    }

    override fun onLoadErrorCleared() = _state.update { it.copy(errorMessageKey = null) }

    override fun onExternalAuthRequired(url: String) = _state.update {
        it.copy(errorMessageKey = "error_login_blocked", errorUrl = url)
    }

    override fun onEnterFullscreen(view: View) = _state.update { it.copy(fullscreenView = view) }

    override fun onExitFullscreen() = _state.update { it.copy(fullscreenView = null) }

    override fun onDownloadRequested(request: DownloadRequest) = _state.update {
        it.copy(pendingDownload = request)
    }

    /** Exposed for the tab strip so the UI can show a letter badge without a favicon cache. */
    fun badgeFor(tab: TabSnapshot): String =
        tab.faviconKey ?: com.gamesidebar.core.browser.UrlResolver.hostOf(tab.url)?.take(1)?.uppercase() ?: "•"
}

@Composable
fun BrowserScreen(initialUrl: String? = null) {
    val context = LocalContext.current
    val viewModel: BrowserViewModel = viewModel(
        factory = viewModelFactory {
            initializer { BrowserViewModel(context.applicationContext as Application) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addressText by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        viewModel.start(initialUrl)
        onDispose { viewModel.detach() }
    }

    if (!state.webviewAvailable) {
        ErrorState(
            title = stringResource(R.string.error_webview_missing),
            body = stringResource(R.string.error_webview_body),
            actionLabel = stringResource(R.string.action_retry),
            onAction = { },
            modifier = Modifier.fillMaxSize().padding(24.dp),
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabStrip(
                tabs = state.tabs,
                badgeFor = viewModel::badgeFor,
                onSelect = viewModel::selectTab,
                onClose = viewModel::closeTab,
                onNew = viewModel::newTab,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                IconAction(
                    iconRes = R.drawable.ic_back,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = viewModel::back,
                    tint = if (state.canGoBack) GameSidebarColors.TextPrimary else GameSidebarColors.TextMuted,
                )
                IconAction(
                    iconRes = R.drawable.ic_forward,
                    contentDescription = stringResource(R.string.action_forward),
                    onClick = viewModel::forward,
                    tint = if (state.canGoForward) GameSidebarColors.TextPrimary else GameSidebarColors.TextMuted,
                )
                OutlinedTextField(
                    value = addressText.ifBlank { com.gamesidebar.core.browser.UrlResolver.displayUrl(state.url) },
                    onValueChange = { addressText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    placeholder = { Text(stringResource(R.string.url_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            viewModel.submit(addressText.ifBlank { state.url })
                            addressText = ""
                        },
                    ),
                )
                IconAction(
                    iconRes = if (state.isLoading) R.drawable.ic_stop else R.drawable.ic_reload,
                    contentDescription = stringResource(
                        if (state.isLoading) R.string.action_stop else R.string.action_reload,
                    ),
                    onClick = { if (state.isLoading) viewModel.stopLoading() else viewModel.reload() },
                )
                IconAction(
                    iconRes = if (state.isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
                    contentDescription = stringResource(R.string.action_bookmark),
                    onClick = viewModel::toggleBookmark,
                    tint = if (state.isBookmarked) GameSidebarColors.AccentBlue else GameSidebarColors.TextSecondary,
                )
                IconAction(
                    iconRes = R.drawable.ic_home,
                    contentDescription = stringResource(R.string.action_home),
                    onClick = viewModel::home,
                )
            }

            if (state.isLoading) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = GameSidebarColors.AccentBlue,
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { viewContext ->
                        android.widget.FrameLayout(viewContext).also { viewModel.attach(it) }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                state.errorMessageKey?.let { key ->
                    ErrorState(
                        title = stringResource(titleFor(key)),
                        body = stringResource(bodyFor(key)),
                        actionLabel = stringResource(
                            if (key == "error_login_blocked") R.string.error_action_open_login else R.string.action_retry,
                        ),
                        onAction = {
                            if (key == "error_login_blocked") viewModel.openExternally() else viewModel.reload()
                            viewModel.clearError()
                        },
                        iconRes = if (key == "error_no_network") R.drawable.ic_wifi_off else R.drawable.ic_warning,
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                    )
                }
            }
        }

        state.fullscreenView?.let { view ->
            AndroidView(
                factory = { view },
                modifier = Modifier.fillMaxSize(),
            )
        }

        state.pendingDownload?.let { request ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = viewModel::rejectDownload,
                title = { Text(stringResource(R.string.download_title)) },
                text = { Text("${request.fileName}\n${request.sizeLabel}") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = viewModel::acceptDownload) {
                        Text(stringResource(R.string.action_download))
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = viewModel::rejectDownload) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun TabStrip(
    tabs: Tabs,
    badgeFor: (TabSnapshot) -> String,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNew: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(tabs.tabs.size) { index ->
                val tab = tabs.tabs[index]
                val active = tab.id == tabs.activeId
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) GameSidebarColors.SurfaceElevated else GameSidebarColors.Surface)
                        .clickable { onSelect(tab.id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = badgeFor(tab),
                        style = MaterialTheme.typography.labelSmall,
                        color = GameSidebarColors.AccentBlue,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = Tabs.shortenTitle(tab.displayTitle, 12),
                        style = MaterialTheme.typography.labelSmall,
                        color = GameSidebarColors.TextSecondary,
                    )
                    Spacer(Modifier.size(6.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = stringResource(R.string.panel_close_tab),
                        tint = GameSidebarColors.TextMuted,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { onClose(tab.id) },
                    )
                }
            }
        }
        IconAction(
            iconRes = R.drawable.ic_add,
            contentDescription = stringResource(R.string.panel_new_tab),
            onClick = onNew,
        )
    }
}

private fun titleFor(messageKey: String): Int = when (messageKey) {
    "error_no_network" -> R.string.error_no_network
    "error_blocked_scheme" -> R.string.error_blocked_scheme
    "error_blocked_file" -> R.string.error_blocked_file
    "error_blocked_script" -> R.string.error_blocked_script
    "error_empty_url" -> R.string.error_empty_url
    "error_login_blocked" -> R.string.error_login_blocked
    "error_webview_missing" -> R.string.error_webview_missing
    else -> R.string.error_page_failed
}

private fun bodyFor(messageKey: String): Int = when (messageKey) {
    "error_no_network" -> R.string.error_no_network_body
    "error_login_blocked" -> R.string.error_login_body
    "error_webview_missing" -> R.string.error_webview_body
    "error_ssl_not_yet_valid", "error_ssl_expired", "error_ssl_id_mismatch",
    "error_ssl_untrusted", "error_ssl_generic",
    -> R.string.error_ssl_body

    else -> R.string.error_no_network_body
}
