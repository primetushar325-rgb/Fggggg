package com.gamesidebar.browser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.gamesidebar.browser.data.ServiceLocator
import com.gamesidebar.browser.overlay.OverlayService
import com.gamesidebar.browser.ui.bookmarks.BookmarksScreen
import com.gamesidebar.browser.ui.browser.BrowserScreen
import com.gamesidebar.browser.ui.components.GlassCard
import com.gamesidebar.browser.ui.components.GlowActionButton
import com.gamesidebar.browser.ui.components.IconAction
import com.gamesidebar.browser.ui.history.HistoryScreen
import com.gamesidebar.browser.ui.home.HomeScreen
import com.gamesidebar.browser.ui.notes.NotesScreen
import com.gamesidebar.browser.ui.settings.SettingsScreen
import com.gamesidebar.browser.ui.theme.GameSidebarColors
import com.gamesidebar.browser.ui.theme.GameSidebarTheme
import com.gamesidebar.browser.util.OverlayPermission
import kotlinx.coroutines.launch

/**
 * The app's only navigation host.
 *
 * Routes are a small sealed class rather than the Navigation library: five destinations, no deep
 * links, no saved-state restoration requirements - a hand-rolled back stack is less code and less
 * startup work, which matters for an app whose whole point is staying light next to a game.
 */
class MainActivity : ComponentActivity() {

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // The result code from the overlay settings screen is unreliable across OEM builds;
            // the permission is re-checked directly instead.
            if (OverlayPermission.canDrawOverlays(this)) maybeAutoStart()
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            GameSidebarTheme {
                GameSidebarApp(
                    onRequestOverlayPermission = ::requestOverlayPermission,
                    onOpenUrl = { url -> BrowserActivity.start(this, url) },
                )
            }
        }
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        maybeAutoStart()
    }

    private fun requestOverlayPermission() {
        runCatching { overlayPermissionLauncher.launch(OverlayPermission.settingsIntent(this)) }
            .onFailure {
                // Some builds reject the package-scoped intent; fall back to the generic screen.
                runCatching { overlayPermissionLauncher.launch(OverlayPermission.genericSettingsIntent()) }
            }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** "Auto-start sidebar": show the handle as soon as the app opens and the permission exists. */
    private fun maybeAutoStart() {
        if (!OverlayPermission.canDrawOverlays(this)) return
        if (OverlayService.isRunning) return
        lifecycleScope.launch {
            val settings = ServiceLocator.get(this@MainActivity).settingsRepository.currentSettings()
            if (settings.autoStartSidebar) {
                OverlayService.start(this@MainActivity)
            }
        }
    }
}

private sealed interface Route {
    data object Home : Route
    data object Browser : Route
    data object Bookmarks : Route
    data object History : Route
    data object Notes : Route
    data object Settings : Route
}

@Composable
private fun GameSidebarApp(
    onRequestOverlayPermission: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val context = LocalContext.current
    var route by remember { mutableStateOf<Route>(Route.Home) }
    var permissionDismissed by remember { mutableStateOf(OverlayPermission.canDrawOverlays(context)) }

    BackHandler(enabled = route != Route.Home) { route = Route.Home }

    Column(modifier = Modifier.fillMaxSize()) {
        if (route != Route.Home) {
            ScreenHeader(
                title = when (route) {
                    Route.Browser -> stringResource(R.string.home_browser)
                    Route.Bookmarks -> stringResource(R.string.home_bookmarks)
                    Route.History -> stringResource(R.string.history_title)
                    Route.Notes -> stringResource(R.string.notes_title)
                    Route.Settings -> stringResource(R.string.settings_title)
                    Route.Home -> stringResource(R.string.app_name)
                },
                onBack = { route = Route.Home },
            )
        }

        when {
            !permissionDismissed -> PermissionScreen(
                onEnable = {
                    onRequestOverlayPermission()
                    permissionDismissed = true
                },
                onSkip = { permissionDismissed = true },
            )

            else -> when (route) {
                Route.Home -> HomeScreen(
                    onOpenBrowser = { route = Route.Browser },
                    onOpenBookmarks = { route = Route.Bookmarks },
                    onOpenHistory = { route = Route.History },
                    onOpenNotes = { route = Route.Notes },
                    onOpenSettings = { route = Route.Settings },
                    onRequestOverlayPermission = onRequestOverlayPermission,
                )

                Route.Browser -> BrowserScreen()
                Route.Bookmarks -> BookmarksScreen(onOpenUrl = onOpenUrl)
                Route.History -> HistoryScreen(onOpenUrl = onOpenUrl)
                Route.Notes -> NotesScreen()
                Route.Settings -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        IconAction(
            iconRes = R.drawable.ic_back,
            contentDescription = stringResource(R.string.action_back),
            onClick = onBack,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = GameSidebarColors.TextPrimary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * The overlay explanation screen shown on first launch.
 *
 * It says plainly what the permission does, and it offers a way past: without it the app is still a
 * browser, so the permission is never a wall.
 */
@Composable
private fun PermissionScreen(onEnable: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            painter = painterResource(id = R.drawable.ic_shield),
            contentDescription = null,
            tint = GameSidebarColors.AccentBlue,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.titleLarge,
            color = GameSidebarColors.TextPrimary,
        )
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Text(
                text = stringResource(R.string.permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = GameSidebarColors.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.permission_notification_body),
                style = MaterialTheme.typography.bodySmall,
                color = GameSidebarColors.TextMuted,
            )
        }
        Spacer(Modifier.height(20.dp))
        GlowActionButton(
            text = stringResource(R.string.permission_enable),
            iconRes = R.drawable.ic_panel,
            emphasized = true,
            onClick = onEnable,
        )
        Spacer(Modifier.height(12.dp))
        GlowActionButton(
            text = stringResource(R.string.permission_skip),
            iconRes = R.drawable.ic_browser,
            onClick = onSkip,
        )
    }
}
