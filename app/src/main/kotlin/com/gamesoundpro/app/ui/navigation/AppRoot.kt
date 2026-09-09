package com.gamesoundpro.app.ui.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gamesoundpro.app.database.entity.SoundEntity
import com.gamesoundpro.app.di.AppContainer
import com.gamesoundpro.app.domain.AppSettings
import com.gamesoundpro.app.overlay.GamingModeManager
import com.gamesoundpro.app.ui.components.LocalHapticsEnabled
import com.gamesoundpro.app.ui.favorites.FavoritesScreen
import com.gamesoundpro.app.ui.home.HomeScreen
import com.gamesoundpro.app.ui.mysounds.MySoundsScreen
import com.gamesoundpro.app.ui.mysounds.PackDetailScreen
import com.gamesoundpro.app.ui.mysounds.PacksScreen
import com.gamesoundpro.app.ui.player.MiniPlayerBar
import com.gamesoundpro.app.ui.player.PlaybackViewModel
import com.gamesoundpro.app.ui.player.PlayerSheet
import com.gamesoundpro.app.ui.settings.SettingsScreen
import com.gamesoundpro.app.ui.sheets.AddSoundSheet
import com.gamesoundpro.app.ui.sheets.EditSoundSheet
import com.gamesoundpro.app.ui.sheets.MixerSheet
import com.gamesoundpro.app.ui.sheets.PackCreateDialog
import com.gamesoundpro.app.ui.sheets.RecordSheet
import com.gamesoundpro.app.ui.soundboard.SoundboardScreen
import com.gamesoundpro.app.ui.theme.LocalGamingMode
import kotlinx.coroutines.launch

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val BOTTOM_DESTINATIONS = listOf(
    BottomDestination("home", "Home", Icons.Rounded.Home),
    BottomDestination("soundboard", "Soundboard", Icons.Rounded.GridView),
    BottomDestination("mysounds", "My Sounds", Icons.Rounded.LibraryMusic),
    BottomDestination("favorites", "Favorites", Icons.Rounded.Star),
    BottomDestination("settings", "Settings", Icons.Rounded.Settings),
)

/** Root UI: bottom navigation, screens, sheets, snackbars, mini player and the gaming banner. */
@Composable
fun AppRoot(container: AppContainer) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val packs by remember { container.soundRepository.packs }.collectAsState(initial = emptyList())
    val musicState by container.audioEngine.musicState.collectAsStateWithLifecycle()
    val overlayActive by GamingModeManager.overlayActive.collectAsStateWithLifecycle()

    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Engine playback errors + music messages surface as friendly snackbars.
    LaunchedEffectOnce {
        container.audioEngine.errors.collect { message -> snackbarHostState.showSnackbar(message) }
    }
    val playbackVm: PlaybackViewModel = viewModel()
    LaunchedEffectOnce {
        playbackVm.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    CompositionLocalProvider(LocalHapticsEnabled provides settings.haptics) {
        var showAddSheet by remember { mutableStateOf(false) }
        var showRecordSheet by remember { mutableStateOf(false) }
        var showMixerSheet by remember { mutableStateOf(false) }
        var showPlayerSheet by remember { mutableStateOf(false) }
        var showCreatePack by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf<SoundEntity?>(null) }
        val playbackViewModel: PlaybackViewModel = playbackVm

        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val gaming = LocalGamingMode.current

        // In Gaming Mode the home screen requires a double-back to exit (no accidental leaves).
        var lastBackAt by remember { mutableStateOf(0L) }
        BackHandler(enabled = gaming && currentRoute == "home") {
            val now = System.currentTimeMillis()
            if (now - lastBackAt < 2000) {
                (context as? Activity)?.finish()
            } else {
                lastBackAt = now
                showMessage("GAMING MODE — press back again to exit")
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column {
                    AnimatedVisibility(visible = musicState.track != null) {
                        MiniPlayerBar(
                            state = musicState,
                            viewModel = playbackViewModel,
                            onOpen = { showPlayerSheet = true },
                        )
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        BOTTOM_DESTINATIONS.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(destination.icon, contentDescription = destination.label) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (currentRoute in setOf("soundboard", "mysounds", "favorites")) {
                    FloatingActionButton(onClick = { showAddSheet = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add a new sound")
                    }
                }
            },
        ) { innerPadding ->
            Column(Modifier.padding(innerPadding).fillMaxSize()) {
                if (settings.gamingMode && overlayActive) {
                    GamingBanner(onExit = { container.gamingModeManager.setEnabled(false) })
                }
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { if (gaming) EnterTransition.None else fadeIn(tween(200)) },
                    exitTransition = { if (gaming) ExitTransition.None else fadeOut(tween(160)) },
                    popEnterTransition = { if (gaming) EnterTransition.None else fadeIn(tween(200)) },
                    popExitTransition = { if (gaming) ExitTransition.None else fadeOut(tween(160)) },
                ) {
                    composable("home") {
                        HomeScreen(
                            onNavigate = { route -> navController.navigate(route) },
                            onOpenAdd = { showAddSheet = true },
                            onOpenRecord = { showRecordSheet = true },
                            onOpenMixer = { showMixerSheet = true },
                            onOpenPlayer = { showPlayerSheet = true },
                            onEditSound = { editing = it },
                        )
                    }
                    composable("soundboard") {
                        SoundboardScreen(onEditSound = { editing = it })
                    }
                    composable("mysounds") {
                        MySoundsScreen(
                            onOpenAdd = { showAddSheet = true },
                            onOpenRecord = { showRecordSheet = true },
                            onEditSound = { editing = it },
                            onOpenPack = { id -> navController.navigate("pack/$id") },
                        )
                    }
                    composable("favorites") {
                        FavoritesScreen(onEditSound = { editing = it })
                    }
                    composable("settings") {
                        SettingsScreen(
                            onMessage = showMessage,
                            onOpenDiagnostics = { navController.navigate("diagnostics") },
                        )
                    }
                    composable("diagnostics") {
                        com.gamesoundpro.app.ui.diagnostics.DiagnosticsScreen(onBack = { navController.popBackStack() })
                    }
                    composable("packs") {
                        PacksScreen(
                            onOpenPack = { id -> navController.navigate("pack/$id") },
                            onBack = { navController.popBackStack() },
                            onMessage = showMessage,
                        )
                    }
                    composable("pack/{packId}") {
                        PackDetailScreen(
                            onBack = { navController.popBackStack() },
                            onEditSound = { editing = it },
                            onMessage = showMessage,
                        )
                    }
                }
            }
        }

        // ---- Global sheets ----
        if (showAddSheet) {
            AddSoundSheet(
                container = container,
                onDismiss = { showAddSheet = false },
                onOpenRecord = { showRecordSheet = true },
                onCreatePack = { showCreatePack = true },
                onMessage = showMessage,
            )
        }
        if (showRecordSheet) {
            RecordSheet(
                container = container,
                onDismiss = { showRecordSheet = false },
                onMessage = showMessage,
            )
        }
        if (showMixerSheet) {
            MixerSheet(container = container, onDismiss = { showMixerSheet = false })
        }
        if (showPlayerSheet) {
            PlayerSheet(
                state = musicState,
                viewModel = playbackViewModel,
                onDismiss = { showPlayerSheet = false },
            )
        }
        editing?.let { sound ->
            EditSoundSheet(
                sound = sound,
                packs = packs,
                container = container,
                onDismiss = { editing = null },
                onMessage = showMessage,
            )
        }
        if (showCreatePack) {
            PackCreateDialog(
                onCreate = { name, icon ->
                    scope.launch { container.soundRepository.createPack(name, icon) }
                    showCreatePack = false
                },
                onDismiss = { showCreatePack = false },
            )
        }
    }
}

/** Collects the audio engine's one-shot error flow for the lifetime of the composition. */
@Composable
private fun LaunchedEffectOnce(block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}

/** Slim banner confirming Gaming Mode while the app is in the foreground. */
@Composable
private fun GamingBanner(onExit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    )
                )
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text("🎮", modifier = Modifier.width(24.dp))
        Text(
            "GAMING MODE ACTIVE",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onExit) { Text("Exit") }
    }
}
