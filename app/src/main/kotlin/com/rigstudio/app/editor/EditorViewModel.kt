package com.rigstudio.app.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rigstudio.app.RigStudioApplication
import com.rigstudio.app.data.LoadedCharacter
import com.rigstudio.app.data.ProjectStore
import com.rigstudio.app.render.StageBackground
import com.rigstudio.app.render.StageSource
import com.rigstudio.app.render.downscaleTo
import com.rigstudio.core.anim.AnimationClip
import com.rigstudio.core.anim.AnimationLibrary
import com.rigstudio.core.export.ExportFrameRate
import com.rigstudio.core.export.ExportLimits
import com.rigstudio.core.export.ExportResolution
import com.rigstudio.core.export.ExportSettings
import com.rigstudio.core.model.Expression
import com.rigstudio.core.model.MouthShape
import com.rigstudio.core.model.ViewKind
import com.rigstudio.core.util.HistoryStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A one-shot transport instruction for the stage view.
 *
 * The view owns the frame clock, so "play / pause / restart / seek" are sent as commands with a
 * monotonically increasing id: the view applies each exactly once, and the playhead it reports back
 * never re-triggers the command that produced it (which is the feedback loop this avoids).
 */
data class TransportCommand(val id: Long, val action: TransportAction)

sealed interface TransportAction {
    data object Play : TransportAction
    data object Pause : TransportAction
    data object Restart : TransportAction
    data object Stop : TransportAction
    data class Seek(val normalizedTime: Float) : TransportAction
}

/** One-shot navigation out of the editor. */
sealed interface EditorNavigation {
    data object Library : EditorNavigation
    data class Export(val projectId: String) : EditorNavigation
    data object Template : EditorNavigation
}

/**
 * The editor: choose a view, choose an animation, scrub it, dress the stage.
 *
 * Two flows are published on purpose:
 *  - [state] is the small, comparable snapshot Compose renders from;
 *  - [stageSource] carries the heavy objects (rig, sprite bitmaps, background image) straight to the
 *    [com.rigstudio.app.render.StageView], which owns the frame clock.
 *
 * Splitting them is what keeps a 60 fps preview from recomposing the whole screen every frame.
 */
class EditorViewModel(private val app: RigStudioApplication) : ViewModel() {

    private val store: ProjectStore = app.store

    private var character: LoadedCharacter? = null
    private var resolver: ((String) -> Bitmap?)? = null
    private var persistJob: Job? = null

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _stageSource = MutableStateFlow<StageSource?>(null)
    val stageSource: StateFlow<StageSource?> = _stageSource.asStateFlow()

    private val _navigation = MutableStateFlow<EditorNavigation?>(null)
    val navigation: StateFlow<EditorNavigation?> = _navigation.asStateFlow()

    private val _transport = MutableStateFlow<TransportCommand?>(null)
    val transport: StateFlow<TransportCommand?> = _transport.asStateFlow()

    private var transportSequence = 0L

    /** V4 §28: bounded undo/redo over the editor's editable state. */
    private val history = HistoryStack<EditorSnapshot>()

    /** True while undo()/redo() re-applies a snapshot — setters must not record new steps then. */
    private var restoringHistory = false

    // --- V5 §62/§63: temporary gestures return to the previous base animation -----------------
    private var baseClipId: String? = null
    private var gestureReturnJob: Job? = null

    /** User-level defaults (V4 §49): loop-by-default and the hidden debug overlay. */
    private val appSettings by lazy { app.settingsStore.load() }

    /** Loads a saved character and restores where the user left off. */
    fun load(projectId: String) {
        val current = _state.value
        if (current.projectId == projectId && current.loaded) return
        if (current.projectId != projectId) {
            // Different character: drop everything from the previous one before loading.
            character = null
            resolver = null
            _stageSource.value = null
            _transport.value = null
            _state.value = EditorState(projectId = projectId, loading = true)
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, projectId = projectId) }
            val loaded = withContext(Dispatchers.IO) {
                runCatching { store.load(projectId) }
            }
            val loadedCharacter = loaded.getOrNull()
            if (loadedCharacter == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        loaded = false,
                        message = loaded.exceptionOrNull()?.message
                            ?: "This character could not be opened.",
                    )
                }
                return@launch
            }

            character = loadedCharacter
            resolver = store.bitmapResolver(loadedCharacter.project)
            withContext(Dispatchers.IO) { store.touch(projectId, System.currentTimeMillis()) }

            val project = loadedCharacter.project
            val views = loadedCharacter.availableViews.ifEmpty { listOf(ViewKind.FRONT) }
            val startView = project.lastView.takeIf { it in views } ?: ViewKind.FRONT
            val clips = AnimationLibrary.playableIn(startView, loadedCharacter.hasProfileArtwork)
            val startClip = AnimationLibrary.byId(project.lastClipId)?.takeIf { clip -> clip.id in clips.map { it.id } }
                ?: clips.firstOrNull()
                ?: AnimationLibrary.IDLE
            val background = project.lastBackgroundArgb
                ?.let { StageBackground.Solid(it) }
                ?: StageBackground.Solid(ExportSettings.DEFAULT_BACKGROUND)

            _state.update {
                it.copy(
                    loading = false,
                    loaded = true,
                    characterName = project.name,
                    notes = project.notes,
                    views = views,
                    view = startView,
                    mirroredSideView = project.mirroredSideView,
                    clips = clips,
                    clip = startClip,
                    expressions = project.availableExpressions,
                    mouthShapes = project.availableMouthShapes,
                    background = background,
                    speed = project.lastSpeed,
                    looping = startClip.loop && appSettings.loopByDefault,
                    debugOverlay = appSettings.debugOverlays,
                )
            }
            publishStage()
        }
    }

    // --- undo / redo (V4 §28) -----------------------------------------------------------------

    /** The editable slice of the editor, as one immutable snapshot for the history stack. */
    private data class EditorSnapshot(
        val view: ViewKind,
        val clipId: String?,
        val expressionOverride: Expression?,
        val mouthOverride: MouthShape?,
        val background: StageBackground,
        val showChecker: Boolean,
        val speed: Float,
        val looping: Boolean,
    )

    private fun snapshotOf(s: EditorState) = EditorSnapshot(
        view = s.view,
        clipId = s.clip?.id,
        expressionOverride = s.expressionOverride,
        mouthOverride = s.mouthOverride,
        background = s.background,
        showChecker = s.showChecker,
        speed = s.speed,
        looping = s.looping,
    )

    /** Files the current state as an undo step. Call before applying any user-driven mutation. */
    private fun recordHistory() {
        if (restoringHistory) return
        history.record(snapshotOf(_state.value))
        publishUndoAvailability()
    }

    private fun publishUndoAvailability() {
        _state.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo) }
    }

    fun undo() {
        if (restoringHistory) return
        val restore = history.undo(snapshotOf(_state.value)) ?: return
        restoringHistory = true
        try {
            applySnapshot(restore)
        } finally {
            restoringHistory = false
            publishUndoAvailability()
        }
    }

    fun redo() {
        if (restoringHistory) return
        val restore = history.redo(snapshotOf(_state.value)) ?: return
        restoringHistory = true
        try {
            applySnapshot(restore)
        } finally {
            restoringHistory = false
            publishUndoAvailability()
        }
    }

    /** Re-runs the user's own setters so every side effect (stage, persist) happens as usual. */
    private fun applySnapshot(snapshot: EditorSnapshot) {
        selectView(snapshot.view)
        snapshot.clipId?.let { selectClip(it) }
        // selectClip clears overrides, so re-apply them after it.
        setExpression(snapshot.expressionOverride)
        setMouth(snapshot.mouthOverride)
        applyBackground(snapshot.background)
        setShowChecker(snapshot.showChecker)
        setSpeed(snapshot.speed)
        setLoopingInternal(snapshot.looping)
        schedulePersist()
    }

    // --- view & clip selection ----------------------------------------------------------------

    fun selectView(view: ViewKind) {
        val current = _state.value
        if (view == current.view) return
        gestureReturnJob?.cancel()
        if (view !in current.views) {
            _state.update { it.copy(message = unavailableViewMessage(view)) }
            return
        }
        recordHistory()
        val clips = character?.let { AnimationLibrary.playableIn(view, it.hasProfileArtwork) } ?: emptyList()
        val keepClip = current.clip?.takeIf { clip -> clip.id in clips.map { it.id } }
            ?: clips.firstOrNull()
            ?: AnimationLibrary.IDLE
        _state.update {
            it.copy(
                view = view,
                clips = clips,
                clip = keepClip,
                looping = keepClip.loop,
                normalizedTime = 0f,
                expressionOverride = null,
                mouthOverride = null,
            )
        }
        publishStage()
        schedulePersist()
    }

    fun selectClip(clipId: String) {
        val clip = AnimationLibrary.byId(clipId) ?: return
        scheduleGestureReturn(clip)
        val current = _state.value
        val character = character ?: return

        // A side clip needs profile artwork. Switch to a profile view instead of refusing.
        var view = current.view
        if (clip.needsSideView) {
            if (!character.hasProfileArtwork) {
                _state.update { it.copy(message = SIDE_VIEW_MISSING) }
                return
            }
            val profile = listOf(ViewKind.SIDE_LEFT, ViewKind.SIDE_RIGHT).firstOrNull { it in current.views }
            if (profile != null) view = profile
        }
        val required = clip.requiredView
        if (required != null && required in current.views) view = required
        if (view == current.view && clip == current.clip) return
        recordHistory()

        val clips = AnimationLibrary.playableIn(view, character.hasProfileArtwork)
        _state.update {
            it.copy(
                view = view,
                clips = clips,
                clip = clip,
                looping = clip.loop,
                normalizedTime = 0f,
                expressionOverride = null,
                mouthOverride = null,
            )
        }
        publishStage()
        schedulePersist()
    }

    /**
     * V5 §63: one-shot gestures (wave, emotions, look back, jump) play for about two cycles and
     * then hand control back to the previous BASE animation (walk, run, idle…) instead of
     * always dropping to idle. The crossfade in [com.rigstudio.app.render.StageView] smooths
     * the hand-off.
     */
    private fun scheduleGestureReturn(clip: AnimationClip) {
        gestureReturnJob?.cancel()
        gestureReturnJob = null
        if (clip.id !in GESTURE_CLIPS) {
            baseClipId = clip.id
            return
        }
        val current = _state.value
        if (baseClipId == null || baseClipId == clip.id) {
            baseClipId = current.clip?.id?.takeIf { it != clip.id && it !in GESTURE_CLIPS } ?: IDLE_CLIP_ID
        }
        val speed = current.speed.coerceAtLeast(0.05f)
        val holdMillis = (clip.durationSeconds * GESTURE_CYCLES * 1000f / speed).toLong()
        gestureReturnJob = viewModelScope.launch {
            delay(holdMillis)
            val target = baseClipId
            if (target != null && _state.value.clip?.id != target) {
                selectClip(target)
            }
        }
    }

    // --- transport ----------------------------------------------------------------------------

    fun play() {
        _state.update { it.copy(playing = true) }
        postTransport(TransportAction.Play)
    }

    fun pause() {
        _state.update { it.copy(playing = false) }
        postTransport(TransportAction.Pause)
    }

    fun togglePlay() {
        if (_state.value.playing) pause() else play()
    }

    /** V4 §29 transport: stop = pause and rewind to the first frame. */
    fun stop() {
        _state.update { it.copy(playing = false, normalizedTime = 0f) }
        postTransport(TransportAction.Stop)
    }

    fun restart() {
        _state.update { it.copy(normalizedTime = 0f, playing = true) }
        postTransport(TransportAction.Restart)
    }

    fun seek(normalizedTime: Float) {
        val clamped = normalizedTime.coerceIn(0f, 1f)
        _state.update { it.copy(normalizedTime = clamped) }
        postTransport(TransportAction.Seek(clamped))
    }

    /** Called by the stage view once it has applied a command. */
    fun consumeTransport(): TransportCommand? {
        val current = _transport.value ?: return null
        _transport.value = null
        return current
    }

    private fun postTransport(action: TransportAction) {
        transportSequence += 1
        _transport.value = TransportCommand(transportSequence, action)
    }

    /** The stage view reports its playhead back at ~15 Hz; mirror it for the timeline readout. */
    fun onFrameReported(normalizedTime: Float, isPlaying: Boolean) {
        _state.update {
            if (it.normalizedTime == normalizedTime && it.playing == isPlaying) {
                it
            } else {
                it.copy(normalizedTime = normalizedTime, playing = isPlaying)
            }
        }
    }

    fun onPlaybackFinished() {
        _state.update { it.copy(playing = false, normalizedTime = 1f) }
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(ExportLimits.MIN_SPEED, ExportLimits.MAX_SPEED)
        if (clamped == _state.value.speed) return
        recordHistory()
        _state.update { it.copy(speed = clamped) }
        schedulePersist()
    }

    /** V4 §29: the timeline's loop toggle. */
    fun setLooping(looping: Boolean) {
        if (looping == _state.value.looping) return
        recordHistory()
        setLoopingInternal(looping)
        schedulePersist()
    }

    private fun setLoopingInternal(looping: Boolean) {
        _state.update { it.copy(looping = looping) }
    }

    // --- stage dressing ----------------------------------------------------------------------

    fun setSolidBackground(argb: Int) {
        recordHistory()
        applyBackground(StageBackground.Solid(argb))
        schedulePersist()
    }

    fun setTransparentBackground() {
        recordHistory()
        applyBackground(StageBackground.Transparent)
        _state.update { it.copy(showChecker = true) }
        schedulePersist()
    }

    /** Sets the stage background without touching history (undo/redo call this directly). */
    private fun applyBackground(background: StageBackground) {
        _state.update { it.copy(background = background) }
        publishStage()
    }

    fun setBackgroundImage(uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.downscaleTo(BACKGROUND_MAX_PX)
                    }
                }.getOrNull()
            }
            if (bitmap == null) {
                _state.update { it.copy(message = "That image could not be used as a background.") }
                return@launch
            }
            recordHistory()
            applyBackground(StageBackground.Image(bitmap))
        }
    }

    fun setShowChecker(show: Boolean) {
        if (show == _state.value.showChecker) return
        recordHistory()
        _state.update { it.copy(showChecker = show) }
    }

    /** Pins the eyes to one expression instead of following the clip. Null restores the clip. */
    fun setExpression(expression: Expression?) {
        if (expression != null && expression !in _state.value.expressions) {
            _state.update { it.copy(message = "This character sheet has no ${expression.displayName} eyes.") }
            return
        }
        if (expression == _state.value.expressionOverride) return
        recordHistory()
        _state.update { it.copy(expressionOverride = expression) }
        publishStage()
    }

    /** Pins the mouth shape instead of following the clip's lip-sync track. */
    fun setMouth(shape: MouthShape?) {
        if (shape != null && shape !in _state.value.mouthShapes) {
            _state.update { it.copy(message = "This character sheet has no ${shape.displayName} mouth.") }
            return
        }
        if (shape == _state.value.mouthOverride) return
        recordHistory()
        _state.update { it.copy(mouthOverride = shape) }
        publishStage()
    }

    // --- navigation & housekeeping -----------------------------------------------------------

    fun openExport() {
        val current = _state.value
        if (!current.loaded) return
        _navigation.value = EditorNavigation.Export(current.projectId)
    }

    fun backToLibrary() {
        _navigation.value = EditorNavigation.Library
    }

    fun openTemplate() {
        _navigation.value = EditorNavigation.Template
    }

    fun consumeNavigation(): EditorNavigation? {
        val current = _navigation.value ?: return null
        _navigation.value = null
        return current
    }

    /** Surfaces why an animation cannot play on this character. */
    fun showClipReason(reason: String) {
        _state.update { it.copy(message = reason) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun dismissNotes() {
        _state.update { it.copy(notes = emptyList()) }
    }

    /** The export screen seeds itself from whatever the editor is showing right now. */
    fun exportSeed(): ExportSettings {
        val current = _state.value
        val backgroundArgb = when (val background = current.background) {
            is StageBackground.Solid -> background.argb
            else -> ExportSettings.DEFAULT_BACKGROUND
        }
        return ExportSettings(
            clipId = current.clip?.id ?: AnimationLibrary.IDLE.id,
            view = current.view,
            resolution = ExportResolution.FULL_HD_1080,
            frameRate = ExportFrameRate.FPS_30,
            durationSeconds = (current.clip?.durationSeconds ?: 3f).coerceIn(1f, 10f),
            speed = current.speed,
            backgroundArgb = backgroundArgb,
            transparentBackground = current.background == StageBackground.Transparent,
        )
    }

    private fun unavailableViewMessage(view: ViewKind): String = when (view) {
        ViewKind.SIDE_LEFT, ViewKind.SIDE_RIGHT -> SIDE_VIEW_MISSING
        ViewKind.BACK -> "Back View Assets Not Found — this character sheet has no back-view artwork."
        ViewKind.FRONT -> "Front view is always available."
    }

    private fun publishStage() {
        val current = _state.value
        val loadedCharacter = character
        val bitmapResolver = resolver
        val clip = current.clip
        if (loadedCharacter == null || bitmapResolver == null || clip == null) {
            _stageSource.value = null
            return
        }
        val rig = loadedCharacter.rigFor(current.view)
        if (rig == null) {
            _stageSource.value = null
            _state.update { it.copy(message = unavailableViewMessage(current.view)) }
            return
        }
        _stageSource.value = StageSource(
            rig = rig,
            clip = clip,
            bitmaps = bitmapResolver,
            background = current.background,
            expressionOverride = current.expressionOverride,
            mouthOverride = current.mouthOverride,
        )
    }

    /** Debounced write of the editor session (last clip/view/speed/background) into project.json. */
    private fun schedulePersist() {
        val loadedCharacter = character ?: return
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DEBOUNCE_MILLIS)
            val current = _state.value
            val backgroundArgb = (current.background as? StageBackground.Solid)?.argb
            withContext(Dispatchers.IO) {
                runCatching {
                    store.writeProject(
                        loadedCharacter.project.copy(
                            lastClipId = current.clip?.id ?: loadedCharacter.project.lastClipId,
                            lastView = current.view,
                            lastSpeed = current.speed,
                            lastBackgroundArgb = backgroundArgb,
                        ),
                    )
                }
            }
        }
    }

    override fun onCleared() {
        // Small JSON write; done synchronously so the session is never lost when the screen closes.
        val loadedCharacter = character
        val current = _state.value
        if (loadedCharacter != null) {
            val backgroundArgb = (current.background as? StageBackground.Solid)?.argb
            runCatching {
                store.writeProject(
                    loadedCharacter.project.copy(
                        lastClipId = current.clip?.id ?: loadedCharacter.project.lastClipId,
                        lastView = current.view,
                        lastSpeed = current.speed,
                        lastBackgroundArgb = backgroundArgb,
                    ),
                )
            }
        }
        resolver = null
        character = null
        super.onCleared()
    }

    companion object {
        /** Exact wording required by the spec when profile artwork is missing. */
        const val SIDE_VIEW_MISSING = "Side View Assets Not Found"

        /** V5 §63: clips that play as temporary gestures and then return to the base clip. */
        val GESTURE_CLIPS = setOf("wave", "jump", "happy", "sad", "angry", "surprised", "look_back")
        private const val GESTURE_CYCLES = 2f
        private const val IDLE_CLIP_ID = "idle"

        private const val PERSIST_DEBOUNCE_MILLIS = 400L
        private const val BACKGROUND_MAX_PX = 1920

        fun factory(app: RigStudioApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorViewModel(app) as T
            }
    }
}
