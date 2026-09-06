package com.rigstudio.app.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rigstudio.app.R
import com.rigstudio.app.editor.EDITOR_BACKGROUND_PRESETS
import com.rigstudio.app.editor.EditorNavigation
import com.rigstudio.app.editor.EditorState
import com.rigstudio.app.editor.EditorViewModel
import com.rigstudio.app.editor.TransportAction
import com.rigstudio.app.render.StageBackground
import com.rigstudio.app.render.StageSource
import com.rigstudio.app.render.StageView
import com.rigstudio.app.ui.components.BusyIndicator
import com.rigstudio.app.ui.components.ChipStrip
import com.rigstudio.app.ui.components.FieldLabel
import com.rigstudio.app.ui.components.RigChip
import com.rigstudio.app.ui.components.RigPrimaryButton
import com.rigstudio.app.ui.components.RigTextButton
import com.rigstudio.app.ui.components.RigChip
import com.rigstudio.app.ui.components.RigTopBar
import com.rigstudio.app.ui.components.SectionCard
import com.rigstudio.app.ui.components.StatusPill
import com.rigstudio.app.ui.theme.RigColors
import com.rigstudio.core.anim.ClipCategory
import com.rigstudio.core.export.ExportLimits
import com.rigstudio.core.model.Expression
import com.rigstudio.core.model.MouthShape
import com.rigstudio.core.model.ViewKind
import kotlin.math.roundToInt

/**
 * The editor: a 16:9 stage, a transport bar, and panels for view, animation, expression and
 * background.
 *
 * The stage is a real Android [StageView] hosted by [AndroidView], because the frame clock and the
 * pixel pipeline belong together and must not wait on recomposition. Compose owns every control
 * around it.
 */
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    projectId: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onOpenTemplate: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stageSource by viewModel.stageSource.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.setBackgroundImage(uri)
    }

    LaunchedEffect(projectId) { viewModel.load(projectId) }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    val navigation by viewModel.navigation.collectAsStateWithLifecycle()
    LaunchedEffect(navigation) {
        when (viewModel.consumeNavigation()) {
            EditorNavigation.Library -> onBack()
            is EditorNavigation.Export -> onExport()
            EditorNavigation.Template -> onOpenTemplate()
            null -> Unit
        }
    }

    Scaffold(
        containerColor = RigColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            RigTopBar(
                title = state.characterName.ifBlank { "Editor" },
                subtitle = state.clip?.let { "${it.name} · ${state.view.displayName}" }
                    ?: "Loading character…",
                onBack = onBack,
                actions = {
                    // V4 §28: Undo / Redo over every editor action (view, clip, face, stage).
                    IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                        Icon(
                            Icons.Filled.KeyboardArrowLeft,
                            contentDescription = "Undo",
                            tint = if (state.canUndo) RigColors.Primary else RigColors.TextDisabled,
                        )
                    }
                    IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                        Icon(
                            Icons.Filled.KeyboardArrowRight,
                            contentDescription = "Redo",
                            tint = if (state.canRedo) RigColors.Primary else RigColors.TextDisabled,
                        )
                    }
                    IconButton(onClick = onExport, enabled = state.loaded) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.editor_export),
                            tint = if (state.loaded) RigColors.Primary else RigColors.TextDisabled,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = RigColors.Surface, border = BorderStroke(1.dp, RigColors.OutlineSoft)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = state.clip?.name ?: "—",
                            style = MaterialTheme.typography.titleSmall,
                            color = RigColors.TextPrimary,
                        )
                        Text(
                            text = "${state.view.displayName} · ${"%.2f".format(state.cycleSeconds)} s per cycle",
                            style = MaterialTheme.typography.labelSmall,
                            color = RigColors.TextSecondary,
                        )
                    }
                    RigPrimaryButton(
                        text = stringResource(R.string.editor_export),
                        onClick = onExport,
                        enabled = state.loaded,
                        modifier = Modifier.width(150.dp),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            StageArea(
                viewModel = viewModel,
                stageSource = stageSource,
                loaded = state.loaded,
                loading = state.loading,
                viewLabel = state.view.displayName,
                clipName = state.clip?.name,
                showChecker = state.showChecker,
                speed = state.speed,
                looping = state.looping,
                debugOverlay = state.debugOverlay,
            )

            TransportBar(
                playing = state.playing,
                normalizedTime = state.normalizedTime,
                timeSeconds = state.timeSeconds,
                cycleSeconds = state.cycleSeconds,
                looping = state.looping,
                enabled = state.loaded,
                onToggle = viewModel::togglePlay,
                onRestart = viewModel::restart,
                onStop = viewModel::stop,
                onLoopToggle = { viewModel.setLooping(!state.looping) },
                onScrub = viewModel::seek,
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.loading && !state.loaded) {
                    BusyIndicator("Rebuilding the rig from saved parts…")
                }

                if (state.notes.isNotEmpty()) {
                    NotesCard(state.notes, viewModel::dismissNotes)
                }

                ViewCard(
                    views = state.views,
                    selected = state.view,
                    mirrored = state.mirroredSideView,
                    onSelect = viewModel::selectView,
                )

                AnimationCard(
                    state = state,
                    onSelect = viewModel::selectClip,
                    onUnavailableInfo = viewModel::showClipReason,
                )

                SpeedCard(
                    speed = state.speed,
                    enabled = state.loaded,
                    onSpeedChange = viewModel::setSpeed,
                )

                if (state.expressions.isNotEmpty() || state.mouthShapes.isNotEmpty()) {
                    FaceCard(
                        state = state,
                        onExpression = viewModel::setExpression,
                        onMouth = viewModel::setMouth,
                    )
                }

                BackgroundCard(
                    background = state.background,
                    showChecker = state.showChecker,
                    onPreset = viewModel::setSolidBackground,
                    onTransparent = viewModel::setTransparentBackground,
                    onPickImage = { backgroundPicker.launch(arrayOf("image/*")) },
                    onShowCheckerChange = viewModel::setShowChecker,
                )
            }
        }
    }
}

/** The 16:9 viewport. */
@Composable
private fun StageArea(
    viewModel: EditorViewModel,
    stageSource: StageSource?,
    loaded: Boolean,
    loading: Boolean,
    viewLabel: String,
    clipName: String?,
    showChecker: Boolean,
    speed: Float,
    looping: Boolean,
    debugOverlay: Boolean,
) {
    val lastClipId = remember { mutableStateOf<String?>(null) }
    val stageRef = remember { mutableStateOf<StageView?>(null) }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(RigColors.StageDefault)
            .border(1.dp, RigColors.OutlineSoft, RoundedCornerShape(16.dp)),
    ) {
        AndroidView(
            factory = { context ->
                StageView(context).apply {
                    drawChecker = showChecker
                    onFrame = { time, playing -> viewModel.onFrameReported(time, playing) }
                    onFinished = { viewModel.onPlaybackFinished() }
                    onTap = { viewModel.togglePlay() }
                    stageRef.value = this
                }
            },
            update = { view ->
                view.stageSource = stageSource
                view.drawChecker = showChecker
                view.speed = speed
                view.loop = looping
                view.debugOverlay = debugOverlay

                // A different clip: keep the proportional playhead instead of snapping to zero.
                val clip = stageSource?.clip
                if (clip != null && lastClipId.value != clip.id) {
                    view.retarget(clip.durationSeconds, speed, clip.loop)
                    lastClipId.value = clip.id
                }

                when (val action = viewModel.consumeTransport()?.action) {
                    TransportAction.Play -> view.play()
                    TransportAction.Pause -> view.pause()
                    TransportAction.Restart -> view.restart()
                    TransportAction.Stop -> view.stop()
                    is TransportAction.Seek -> view.normalizedTime = action.normalizedTime
                    null -> Unit
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusPill(viewLabel.uppercase(), RigColors.Primary)
            if (clipName != null) {
                StatusPill(clipName, RigColors.Secondary)
            }
        }

        // V4 §32: reset the user camera back to the auto framing.
        RigChip(
            label = "Reset view",
            selected = false,
            onClick = { stageRef.value?.resetCamera() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
        )
        if (debugOverlay) {
            StatusPill(
                "DEBUG",
                RigColors.Error,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
            )
        }

        if (!loaded) {
            Box(Modifier.fillMaxSize().background(RigColors.Background.copy(alpha = 0.72f))) {
                if (loading) {
                    BusyIndicator("Loading character…", Modifier.align(Alignment.Center))
                } else {
                    Text(
                        text = "This character could not be opened.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RigColors.Error,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }
            }
        }
    }
}

/** Play / pause / restart, the scrubber and the time readout. */
@Composable
private fun TransportBar(
    playing: Boolean,
    normalizedTime: Float,
    timeSeconds: Float,
    cycleSeconds: Float,
    looping: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
    onLoopToggle: () -> Unit,
    onScrub: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TransportButton(onClick = onRestart, enabled = enabled, label = stringResource(R.string.editor_restart)) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = RigColors.TextPrimary, modifier = Modifier.size(18.dp))
            }
            TransportButton(onClick = onStop, enabled = enabled, label = "Stop") {
                Icon(Icons.Filled.Close, contentDescription = null, tint = RigColors.TextPrimary, modifier = Modifier.size(18.dp))
            }
            TransportButton(
                onClick = onToggle,
                enabled = enabled,
                label = stringResource(if (playing) R.string.editor_pause else R.string.editor_play),
                highlighted = true,
            ) {
                PlayPauseIcon(playing)
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "%.2f s / %.2f s".format(timeSeconds, cycleSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = RigColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (looping) "Looping cycle" else "One-shot — holds the last frame",
                    style = MaterialTheme.typography.labelSmall,
                    color = RigColors.TextDisabled,
                )
            }
            RigChip(label = "Loop", selected = looping, onClick = onLoopToggle, enabled = enabled)
        }
        Spacer(Modifier.height(6.dp))
        ZoomableTimeline(
            normalizedTime = normalizedTime,
            cycleSeconds = cycleSeconds,
            enabled = enabled,
            onScrub = onScrub,
        )
    }
}

/**
 * V4 §29: a timeline with a draggable playhead and real zoom (pinch or buttons, 1×–6×).
 * Zoom spreads the ruler so single frames become draggable targets; the playhead stays
 * synchronised with the stage because it reads the same [normalizedTime] the editor publishes.
 */
@Composable
private fun ZoomableTimeline(
    normalizedTime: Float,
    cycleSeconds: Float,
    enabled: Boolean,
    onScrub: (Float) -> Unit,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    val scroll = rememberScrollState()

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val trackWidth = maxWidth * zoom
            Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
                Canvas(
                    Modifier
                        .width(trackWidth)
                        .height(44.dp)
                        .pointerInput(enabled, zoom, cycleSeconds) {
                            detectTapGestures { offset ->
                                if (enabled && trackWidth.toPx() > 0f) {
                                    onScrub((offset.x / trackWidth.toPx()).coerceIn(0f, 1f))
                                }
                            }
                        }
                        .pointerInput(enabled, zoom, cycleSeconds) {
                            detectHorizontalDragGestures { change, _ ->
                                if (enabled && trackWidth.toPx() > 0f) {
                                    change.consume()
                                    onScrub((change.position.x / trackWidth.toPx()).coerceIn(0f, 1f))
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, scale, _ ->
                                zoom = (zoom * scale).coerceIn(1f, 6f)
                            }
                        },
                ) {
                    val widthPx = size.width
                    val heightPx = size.height
                    val lineY = heightPx * 0.72f

                    drawLine(RigColors.OutlineSoft, Offset(0f, lineY), Offset(widthPx, lineY), strokeWidth = 2f)

                    // Second ticks (and quarter-second minors) that zoom reveals.
                    val seconds = cycleSeconds.coerceAtLeast(0.01f)
                    val pxPerSecond = widthPx / seconds
                    var minor = 0
                    while (minor * 0.25f <= seconds + 1e-4f) {
                        val x = minor * 0.25f * pxPerSecond
                        val major = minor % 4 == 0
                        drawLine(
                            if (major) RigColors.TextSecondary else RigColors.OutlineSoft,
                            Offset(x, lineY),
                            Offset(x, lineY - if (major) heightPx * 0.30f else heightPx * 0.14f),
                            strokeWidth = if (major) 3f else 2f,
                        )
                        minor++
                    }

                    // Playhead.
                    val playX = normalizedTime.coerceIn(0f, 1f) * widthPx
                    drawLine(RigColors.Primary, Offset(playX, heightPx * 0.08f), Offset(playX, lineY), strokeWidth = 4f)
                    drawCircle(RigColors.Primary, radius = 6f, center = Offset(playX, heightPx * 0.08f))
                }
            }
        }
        TransportButton(
            onClick = { zoom = (zoom / 1.5f).coerceIn(1f, 6f) },
            enabled = true,
            label = "Zoom out",
        ) {
            Text("−", color = RigColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = "%.1f×".format(zoom),
            style = MaterialTheme.typography.labelSmall,
            color = RigColors.TextSecondary,
        )
        TransportButton(
            onClick = { zoom = (zoom * 1.5f).coerceIn(1f, 6f) },
            enabled = true,
            label = "Zoom in",
        ) {
            Text("+", color = RigColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TransportButton(
    onClick: () -> Unit,
    enabled: Boolean,
    label: String,
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (highlighted) RigColors.Primary else RigColors.SurfaceRaised,
        contentColor = if (highlighted) RigColors.OnPrimary else RigColors.TextPrimary,
        border = BorderStroke(1.dp, if (highlighted) RigColors.Primary else RigColors.Outline),
        modifier = Modifier
            .size(if (highlighted) 48.dp else 42.dp)
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** Hand-drawn play/pause glyph — Material's core icon set has no pause icon. */
@Composable
private fun PlayPauseIcon(playing: Boolean, modifier: Modifier = Modifier) {
    val color = RigColors.OnPrimary
    Canvas(modifier.size(20.dp)) {
        if (playing) {
            val barWidth = size.width * 0.24f
            val gap = size.width * 0.16f
            val corner = CornerRadius(barWidth * 0.35f, barWidth * 0.35f)
            drawRoundRect(
                color = color,
                topLeft = Offset((size.width - barWidth * 2 - gap) / 2f, size.height * 0.14f),
                size = Size(barWidth, size.height * 0.72f),
                cornerRadius = corner,
            )
            drawRoundRect(
                color = color,
                topLeft = Offset((size.width + gap) / 2f, size.height * 0.14f),
                size = Size(barWidth, size.height * 0.72f),
                cornerRadius = corner,
            )
        } else {
            val path = Path().apply {
                moveTo(size.width * 0.24f, size.height * 0.12f)
                lineTo(size.width * 0.88f, size.height * 0.5f)
                lineTo(size.width * 0.24f, size.height * 0.88f)
                close()
            }
            drawPath(path, color)
        }
    }
}

@Composable
private fun NotesCard(notes: List<String>, onDismiss: () -> Unit) {
    SectionCard(
        title = "Notes from import",
        trailing = { RigTextButton("Dismiss", onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            notes.forEach { note ->
                Text("•  $note", style = MaterialTheme.typography.bodySmall, color = RigColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun ViewCard(
    views: List<ViewKind>,
    selected: ViewKind,
    mirrored: Boolean,
    onSelect: (ViewKind) -> Unit,
) {
    SectionCard(title = stringResource(R.string.editor_view)) {
        ChipStrip {
            ViewKind.entries.forEach { view ->
                val available = view in views
                RigChip(
                    label = view.displayName,
                    selected = view == selected,
                    enabled = available,
                    onClick = { onSelect(view) },
                    sublabel = if (available) null else "not drawn",
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = when {
                mirrored -> "Right-facing profile is mirrored from your left-facing artwork."
                ViewKind.SIDE_LEFT in views || ViewKind.SIDE_RIGHT in views ->
                    "Profile artwork found. Side animations are enabled."
                else -> "No profile artwork on this sheet: side animations stay disabled " +
                    "(Side View Assets Not Found). The front view is unaffected."
            },
            style = MaterialTheme.typography.bodySmall,
            color = RigColors.TextSecondary,
        )
        if (ViewKind.BACK !in views) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Back View Assets Not Found — the back view is never faked from a front drawing.",
                style = MaterialTheme.typography.bodySmall,
                color = RigColors.TextDisabled,
            )
        }
    }
}

@Composable
private fun AnimationCard(
    state: EditorState,
    onSelect: (String) -> Unit,
    onUnavailableInfo: (String) -> Unit,
) {
    var showUnavailable by remember { mutableStateOf(false) }
    val unavailable = remember(state.clips, state.views) { state.unavailableClips() }

    SectionCard(
        title = stringResource(R.string.editor_animation),
        trailing = {
            if (unavailable.isNotEmpty()) {
                RigTextButton(
                    if (showUnavailable) "Hide" else "${unavailable.size} unavailable",
                    onClick = { showUnavailable = !showUnavailable },
                )
            }
        },
    ) {
        val grouped = remember(state.clips) { state.clips.groupBy { it.category } }
        ClipCategory.entries.forEach { category ->
            val clips = grouped[category] ?: return@forEach
            FieldLabel(category.displayName)
            Spacer(Modifier.height(6.dp))
            ChipStrip {
                clips.forEach { clip ->
                    RigChip(
                        label = clip.name,
                        selected = clip.id == state.clip?.id,
                        onClick = { onSelect(clip.id) },
                        sublabel = "%.1fs".format(clip.durationSeconds),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = state.clip?.description?.takeIf { it.isNotBlank() }
                ?: "18 predefined animations play on every character sheet.",
            style = MaterialTheme.typography.bodySmall,
            color = RigColors.TextSecondary,
        )

        if (showUnavailable && unavailable.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FieldLabel("Unavailable on this character")
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                unavailable.forEach { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(RigColors.SurfaceRaised)
                            .clickable { onUnavailableInfo(entry.reason) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.clip.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = RigColors.TextDisabled,
                            modifier = Modifier.width(110.dp),
                        )
                        Text(
                            text = entry.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = RigColors.TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedCard(speed: Float, enabled: Boolean, onSpeedChange: (Float) -> Unit) {
    SectionCard(title = stringResource(R.string.editor_speed)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "%.2f×".format(speed),
                style = MaterialTheme.typography.titleMedium,
                color = RigColors.Primary,
                modifier = Modifier.width(64.dp),
            )
            Slider(
                value = speed,
                onValueChange = onSpeedChange,
                enabled = enabled,
                valueRange = ExportLimits.MIN_SPEED..ExportLimits.MAX_SPEED,
                steps = ((ExportLimits.MAX_SPEED - ExportLimits.MIN_SPEED) / 0.25f).roundToInt() - 1,
                colors = SliderDefaults.colors(
                    thumbColor = RigColors.Primary,
                    activeTrackColor = RigColors.Primary,
                    inactiveTrackColor = RigColors.SurfaceVariant,
                    disabledThumbColor = RigColors.TextDisabled,
                    disabledActiveTrackColor = RigColors.Outline,
                    disabledInactiveTrackColor = RigColors.SurfaceVariant,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0.25×", style = MaterialTheme.typography.labelSmall, color = RigColors.TextDisabled)
            Text("1×", style = MaterialTheme.typography.labelSmall, color = RigColors.TextDisabled)
            Text("3×", style = MaterialTheme.typography.labelSmall, color = RigColors.TextDisabled)
        }
    }
}

@Composable
private fun FaceCard(
    state: EditorState,
    onExpression: (Expression?) -> Unit,
    onMouth: (MouthShape?) -> Unit,
) {
    SectionCard(title = stringResource(R.string.editor_expression)) {
        if (state.expressions.isNotEmpty()) {
            FieldLabel("Eyes (overrides the clip)")
            Spacer(Modifier.height(6.dp))
            ChipStrip {
                RigChip(
                    label = "From clip",
                    selected = state.expressionOverride == null,
                    onClick = { onExpression(null) },
                )
                state.expressions.forEach { expression ->
                    RigChip(
                        label = expression.displayName,
                        selected = state.expressionOverride == expression,
                        onClick = { onExpression(expression) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (state.mouthShapes.isNotEmpty()) {
            FieldLabel("Mouth (overrides lip sync)")
            Spacer(Modifier.height(6.dp))
            ChipStrip {
                RigChip(
                    label = "From clip",
                    selected = state.mouthOverride == null,
                    onClick = { onMouth(null) },
                )
                state.mouthShapes.forEach { shape ->
                    RigChip(
                        label = shape.displayName,
                        selected = state.mouthOverride == shape,
                        onClick = { onMouth(shape) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Text(
            text = "Talk and Side Talk cycle the mouth shapes you drew (closed → A → E → O → closed). " +
                "Overrides pin one sprite; they never deform artwork.",
            style = MaterialTheme.typography.bodySmall,
            color = RigColors.TextSecondary,
        )
    }
}

@Composable
private fun BackgroundCard(
    background: StageBackground,
    showChecker: Boolean,
    onPreset: (Int) -> Unit,
    onTransparent: () -> Unit,
    onPickImage: () -> Unit,
    onShowCheckerChange: (Boolean) -> Unit,
) {
    SectionCard(title = stringResource(R.string.editor_background)) {
        ChipStrip {
            RigChip(
                label = stringResource(R.string.background_transparent),
                selected = background == StageBackground.Transparent,
                onClick = onTransparent,
            )
            RigChip(
                label = stringResource(R.string.background_image),
                selected = background is StageBackground.Image,
                onClick = onPickImage,
            )
        }
        Spacer(Modifier.height(12.dp))
        FieldLabel("Solid colours")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EDITOR_BACKGROUND_PRESETS.forEach { preset ->
                val selected = background is StageBackground.Solid && background.argb == preset.argb
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(preset.argb))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) RigColors.Primary else RigColors.Outline,
                            shape = CircleShape,
                        )
                        .clickable { onPreset(preset.argb) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Checkerboard behind transparency",
                    style = MaterialTheme.typography.titleSmall,
                    color = RigColors.TextPrimary,
                )
                Text(
                    text = "Preview aid only. Exports keep real transparency.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RigColors.TextSecondary,
                )
            }
            Switch(
                checked = showChecker,
                onCheckedChange = onShowCheckerChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = RigColors.OnPrimary,
                    checkedTrackColor = RigColors.Primary,
                    uncheckedThumbColor = RigColors.TextSecondary,
                    uncheckedTrackColor = RigColors.SurfaceVariant,
                    uncheckedBorderColor = RigColors.Outline,
                ),
            )
        }
    }
}
