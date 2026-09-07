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
import com.rigstudio.core.anim.AnimationEngine
import com.rigstudio.core.anim.AnimationLibrary
import com.rigstudio.core.anim.IkChain
import com.rigstudio.core.anim.PoseEditing
import com.rigstudio.core.export.ExportFrameRate
import com.rigstudio.core.export.ExportLimits
import com.rigstudio.core.export.ExportResolution
import com.rigstudio.core.export.ExportSettings
import com.rigstudio.core.geom.Vec2
import com.rigstudio.core.model.Expression
import com.rigstudio.core.model.MouthShape
import com.rigstudio.core.model.ViewKind
import com.rigstudio.core.rig.Pose
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

    // --- V6 pose editor (§10–§12, §47–§48) ----------------------------------------------------
    /** The partial pose currently pinned by pose-editor drags; null = clip untouched. */
    private var poseOverride: Pose? = null

    /** Last reported user camera (V6: preserved across view switches and persisted per project). */
    private var cameraZoom: Float = 1f
    private var cameraPanX: Float = 0f
    private var cameraPanY: Float = 0f

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
            val userPoses = withContext(Dispatchers.IO) { store.loadPoses(projectId) }
            val userClips = withContext(Dispatchers.IO) { store.loadUserClips(projectId) }
            val views = loadedCharacter.availableViews.ifEmpty { listOf(ViewKind.FRONT) }
            val startView = project.lastView.takeIf { it in views } ?: ViewKind.FRONT
            val clips = AnimationLibrary.playableIn(startView, loadedCharacter.hasProfileArtwork) +
                userClips.map { it.toAnimationClip() }
            val startClip = clips.firstOrNull { it.id == project.lastClipId }
                ?: AnimationLibrary.byId(project.lastClipId)?.takeIf { clip -> clip.id in clips.map { it.id } }
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
                    userPoses = userPoses,
                    userClips = userClips,
                    zOrderOverrides = project.zOrderOverrides,
                )
            }
            cameraZoom = project.lastCameraZoom
            cameraPanX = project.lastCameraPanX
            cameraPanY = project.lastCameraPanY
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
        // V6: the pose editor's pinned pose, authored keyframes and z-order edits are first-class
        // undoable state — a drag, a keyframe and a layer move all appear in the same history.
        val poseOverride: Pose?,
        val keyframes: List<com.rigstudio.core.anim.UserKeyframe>,
        val zOrderOverrides: Map<String, Int>,
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
        poseOverride = poseOverride,
        keyframes = s.keyframes,
        zOrderOverrides = s.zOrderOverrides,
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
        restorePoseEditing(snapshot)
        schedulePersist()
    }

    // --- V6 pose editor (§10–§12) ---------------------------------------------------------------

    /** Enters/leaves Pose Mode. Entering pauses playback so the pose is stable under the finger. */
    fun setPoseMode(enabled: Boolean) {
        if (enabled == _state.value.poseMode) return
        if (enabled) pause()
        _state.update { it.copy(poseMode = enabled) }
    }

    /**
     * A limb drag reported by the stage (already converted to view units). Each drag is ONE undo
     * step: history is filed on grab, every move updates the pinned pose, release commits it.
     */
    fun onStagePoseDrag(chainId: String, viewX: Float, viewY: Float, started: Boolean, ended: Boolean) {
        val loadedCharacter = character ?: return
        val rig = loadedCharacter.rigFor(_state.value.view) ?: return
        val chain = IkChain.byId(chainId) ?: return
        if (started) {
            recordHistory()
            _state.update { it.copy(draggingLimb = true) }
        }
        if (!ended) {
            // The pose the drag composes onto: the clip's current pose with any previous pin.
            val base = poseOverride ?: Pose()
            val result = PoseEditing.dragChain(rig, base, chain, Vec2(viewX, viewY))
            if (result != null) {
                poseOverride = result.pose
                publishStage()
            }
        } else {
            _state.update { it.copy(draggingLimb = false) }
        }
    }

    /** Reset (V6 §10): clears every pose-editor pin; the clip plays untouched again. */
    fun resetPose() {
        if (poseOverride == null) return
        recordHistory()
        poseOverride = null
        publishStage()
    }

    /** Mirror Pose (V6 §10): the pinned pose's left/right bones swap with negated rotations. */
    fun mirrorPose() {
        val current = poseOverride ?: return
        recordHistory()
        poseOverride = current.mirrored()
        publishStage()
    }

    /** Copy (V6 §10): stores the current pinned (or clip) pose on the clipboard. */
    fun copyPose() {
        val rig = character?.rigFor(_state.value.view) ?: return
        val current = poseOverride
        val captured = if (current != null) {
            current
        } else {
            // Nothing pinned: capture the clip's pose at the playhead.
            val clip = _state.value.clip ?: return
            AnimationEngine.evaluate(clip, _state.value.normalizedTime)
        }
        val preset = com.rigstudio.core.anim.PosePreset(
            id = "clipboard",
            name = "Clipboard",
            rotations = captured.bones,
        )
        _state.update { it.copy(clipboardPose = preset) }
    }

    /** Paste (V6 §10): applies the clipboard pose as the new pin. */
    fun pastePose() {
        val clipboard = _state.value.clipboardPose ?: run {
            _state.update { it.copy(message = "Copy a pose first.") }
            return
        }
        recordHistory()
        poseOverride = clipboard.toPose()
        publishStage()
    }

    // --- keyframes & custom animations (V6 §12, §48) --------------------------------------------

    /** Captures the currently visible pose (pin + clip at playhead) as a keyframe. */
    fun addKeyframe() {
        val clip = _state.value.clip ?: return
        val t = _state.value.normalizedTime
        val pinned = poseOverride
        val live = AnimationEngine.evaluate(clip, t)
        val captured = pinned?.let { live.override(it) } ?: live
        recordHistory()
        val keyframe = com.rigstudio.core.anim.UserKeyframe(
            time = t,
            rotations = captured.bones.mapValues { it.value.rotationDeg },
            expression = captured.expression,
            mouth = captured.mouth,
        )
        _state.update { state ->
            state.copy(keyframes = (state.keyframes + keyframe).sortedBy { it.time })
        }
    }

    /** Deletes the keyframe nearest to the playhead (within a small window), else the last one. */
    fun deleteKeyframe(index: Int) {
        val keys = _state.value.keyframes
        if (index !in keys.indices) return
        recordHistory()
        _state.update { state ->
            state.copy(keyframes = keys.filterIndexed { i, _ -> i != index })
        }
    }

    /** Clears every authored keyframe. */
    fun clearKeyframes() {
        if (_state.value.keyframes.isEmpty()) return
        recordHistory()
        _state.update { it.copy(keyframes = emptyList()) }
    }

    /** Saves the authored keyframes as a named custom animation and starts playing it. */
    fun saveKeyframesAsClip(name: String, durationSeconds: Float) {
        val keys = _state.value.keyframes
        if (keys.size < 2) {
            _state.update { it.copy(message = "Add at least two keyframes to save an animation.") }
            return
        }
        val loadedCharacter = character ?: return
        val id = "user_${System.currentTimeMillis() % 100_000}"
        val clip = com.rigstudio.core.anim.UserClip(
            id = id,
            name = name.ifBlank { "My animation" },
            durationSeconds = durationSeconds.coerceIn(0.2f, 30f),
            loop = true,
            keys = keys,
        )
        recordHistory()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val saved = store.loadUserClips(loadedCharacter.project.id) + clip
                store.saveUserClips(loadedCharacter.project.id, saved)
            }
        }
        _state.update { state ->
            state.copy(
                userClips = state.userClips + clip,
                keyframes = emptyList(),
            )
        }
        playUserClip(id)
    }

    /** Plays a user-authored animation through the exact same stage pipeline as library clips. */
    fun playUserClip(clipId: String) {
        val clip = _state.value.userClips.firstOrNull { it.id == clipId } ?: return
        gestureReturnJob?.cancel()
        recordHistory()
        val converted = clip.toAnimationClip()
        _state.update {
            it.copy(
                clip = converted,
                looping = true,
                normalizedTime = 0f,
                expressionOverride = null,
                mouthOverride = null,
            )
        }
        publishStage()
        play()
    }

    fun deleteUserClip(clipId: String) {
        val loadedCharacter = character ?: return
        recordHistory()
        val remaining = _state.value.userClips.filterNot { it.id == clipId }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { store.saveUserClips(loadedCharacter.project.id, remaining) }
        }
        _state.update { it.copy(userClips = remaining) }
    }

    /** Mirror Animation (V6 §56): saves and plays the L↔R remapped copy of a user animation. */
    fun mirrorUserClip(clipId: String) {
        val clip = _state.value.userClips.firstOrNull { it.id == clipId } ?: return
        val mirrored = clip.mirrored()
        saveKeyframesAsExisting(mirrored)
    }

    private fun saveKeyframesAsExisting(clip: com.rigstudio.core.anim.UserClip) {
        val loadedCharacter = character ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val saved = store.loadUserClips(loadedCharacter.project.id)
                    .filterNot { it.id == clip.id } + clip
                store.saveUserClips(loadedCharacter.project.id, saved)
            }
        }
        _state.update { state ->
            state.copy(userClips = state.userClips.filterNot { it.id == clip.id } + clip)
        }
        playUserClip(clip.id)
    }

    // --- pose library (V6 §47) --------------------------------------------------------------------

    /** Saves the current visible pose into this character's pose library. */
    fun saveCurrentPose(name: String) {
        val loadedCharacter = character ?: return
        val clip = _state.value.clip ?: return
        val t = _state.value.normalizedTime
        val pinned = poseOverride
        val live = AnimationEngine.evaluate(clip, t)
        val captured = pinned?.let { live.override(it) } ?: live
        val preset = com.rigstudio.core.anim.PosePreset(
            id = "pose_${System.currentTimeMillis() % 100_000}",
            name = name.ifBlank { "My pose" },
            rotations = captured.bones,
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                store.savePoses(loadedCharacter.project.id, store.loadPoses(loadedCharacter.project.id) + preset)
            }
        }
        _state.update { it.copy(userPoses = it.userPoses + preset) }
    }

    /** Applies a saved or built-in pose as the pose editor's pin. */
    fun applyPose(presetId: String) {
        val preset = _state.value.userPoses.firstOrNull { it.id == presetId }
            ?: com.rigstudio.core.anim.PoseLibrary.byId(presetId) ?: return
        recordHistory()
        poseOverride = preset.toPose()
        publishStage()
    }

    fun deleteUserPose(presetId: String) {
        val loadedCharacter = character ?: return
        val remaining = _state.value.userPoses.filterNot { it.id == presetId }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { store.savePoses(loadedCharacter.project.id, remaining) }
        }
        _state.update { it.copy(userPoses = remaining) }
    }

    fun duplicateUserPose(presetId: String) {
        val preset = _state.value.userPoses.firstOrNull { it.id == presetId } ?: return
        val copy = preset.copy(
            id = "pose_${System.currentTimeMillis() % 100_000}",
            name = "${preset.name} (copy)",
        )
        val loadedCharacter = character ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                store.savePoses(loadedCharacter.project.id, store.loadPoses(loadedCharacter.project.id) + copy)
            }
        }
        _state.update { it.copy(userPoses = it.userPoses + copy) }
    }

    // --- z-order editing (V6 §26) -------------------------------------------------------------------

    /**
     * The slot the user tapped on the stage in Pose Mode: drives both the layer tools and the
     * rig inspector (V6 Rig Mode: name / parent / length / rotation / limits).
     */
    fun setSelectedSlot(slotId: String?) {
        val info = slotId?.let { id -> boneInfoFor(id) }
        _state.update { it.copy(selectedSlotId = slotId, selectedBone = info) }
    }

    private fun boneInfoFor(slotId: String): SelectedBoneInfo? {
        val rig = character?.rigFor(_state.value.view) ?: return null
        val bone = rig.bones.firstOrNull { it.sprite?.slotId == slotId } ?: return null
        val parentId = bone.parentId
        val parentLength = rig.bone(parentId ?: "")?.targetHeight
        val pose = poseOverride ?: Pose()
        return SelectedBoneInfo(
            boneId = bone.id,
            slotId = slotId,
            parentBoneId = parentId,
            lengthViewUnits = bone.targetHeight,
            parentLengthViewUnits = parentLength,
            rotationDeg = pose.rotationOf(bone.id),
            minRotationDeg = bone.constraint.minRotationDeg,
            maxRotationDeg = bone.constraint.maxRotationDeg,
        )
    }

    /**
     * Rig Mode editing: sets the selected bone's rotation directly (the inspector's dial),
     * clamped to the bone's constraint range, as one undo step per committed drag segment.
     */
    fun rotateSelectedBone(degrees: Float) {
        val info = _state.value.selectedBone ?: return
        val rig = character?.rigFor(_state.value.view) ?: return
        val bone = rig.bone(info.boneId) ?: return
        val clamped = degrees.coerceIn(bone.constraint.minRotationDeg, bone.constraint.maxRotationDeg)
        val current = poseOverride ?: Pose()
        val edited = current.copy(
            bones = current.bones + mapOf(
                bone.id to current.poseOf(bone.id).copy(rotationDeg = clamped),
            ),
        )
        poseOverride = edited
        _state.update {
            it.copy(selectedBone = it.selectedBone?.copy(rotationDeg = clamped))
        }
        publishStage()
    }

    fun beginBoneRotationEdit() {
        recordHistory()
    }

    /** Applies one of the four layer moves to the selected slot (or clears its override). */
    fun moveLayer(action: LayerAction) {
        val loadedCharacter = character ?: return
        val rig = loadedCharacter.rigFor(_state.value.view) ?: return
        val slotId = _state.value.selectedSlotId ?: run {
            _state.update { it.copy(message = "Tap a part on the stage first.") }
            return
        }
        val draws = com.rigstudio.core.rig.ForwardKinematics.solve(rig, poseOverride ?: Pose()).draws
        val currentZ = _state.value.zOrderOverrides[slotId]
            ?: draws.firstOrNull { it.bone.sprite?.slotId == slotId }?.z ?: return
        val others = draws.map { it.z }.distinct()
        val newZ = when (action) {
            LayerAction.FRONT -> (others.maxOrNull() ?: 0) + 1
            LayerAction.BACK -> (others.minOrNull() ?: 0) - 1
            LayerAction.FORWARD -> currentZ + 1
            LayerAction.BACKWARD -> currentZ - 1
            LayerAction.CLEAR -> null
        }
        recordHistory()
        val updated = _state.value.zOrderOverrides.toMutableMap()
        if (newZ == null) updated.remove(slotId) else updated[slotId] = newZ!!
        _state.update { it.copy(zOrderOverrides = updated) }
        publishStage()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                store.writeProject(
                    loadedCharacter.project.copy(zOrderOverrides = updated),
                )
            }
        }
    }

    /** Restores the pose-editing slice of a snapshot (undo/redo path). */
    private fun restorePoseEditing(snapshot: EditorSnapshot) {
        poseOverride = snapshot.poseOverride
        _state.update {
            it.copy(
                keyframes = snapshot.keyframes,
                zOrderOverrides = snapshot.zOrderOverrides,
            )
        }
        publishStage()
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
        val clips = (character?.let { AnimationLibrary.playableIn(view, it.hasProfileArtwork) } ?: emptyList()) +
            current.userClips.map { it.toAnimationClip() }
        val keepClip = current.clip?.takeIf { clip -> clip.id in clips.map { it.id } }
            ?: clips.firstOrNull()
            ?: AnimationLibrary.IDLE
        // V6: switching views preserves the playhead, playback state and the user camera — only
        // the artwork changes. A clip that cannot play in the new view falls back to the first
        // playable one and restarts, which is the one exception.
        val sameClip = keepClip.id == current.clip?.id
        val keptTime = if (sameClip) current.normalizedTime else 0f
        val keptLooping = if (sameClip) current.looping else keepClip.loop
        _state.update {
            it.copy(
                view = view,
                clips = clips,
                clip = keepClip,
                looping = keptLooping,
                normalizedTime = keptTime,
                expressionOverride = null,
                mouthOverride = null,
            )
        }
        publishStage()
        if (sameClip && keptTime > 0f) postTransport(TransportAction.Seek(keptTime))
        schedulePersist()
    }

    fun selectClip(clipId: String) {
        val clip = _state.value.clips.firstOrNull { it.id == clipId }
            ?: AnimationLibrary.byId(clipId) ?: return
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

        val clips = AnimationLibrary.playableIn(view, character.hasProfileArtwork) +
            _state.value.userClips.map { it.toAnimationClip() }
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

    /** The stage reports the settled camera framing (V6: preserved per project). */
    fun onCameraChanged(zoom: Float, panX: Float, panY: Float) {
        cameraZoom = zoom
        cameraPanX = panX
        cameraPanY = panY
        schedulePersist()
    }

    /** Framing to restore into the stage when the project opens. */
    fun savedCamera(): Triple<Float, Float, Float> = Triple(cameraZoom, cameraPanX, cameraPanY)

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
            zOrderOverrides = current.zOrderOverrides,
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
            poseOverride = poseOverride,
            zOverrides = current.zOrderOverrides,
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
                            zOrderOverrides = current.zOrderOverrides,
                            lastCameraZoom = cameraZoom,
                            lastCameraPanX = cameraPanX,
                            lastCameraPanY = cameraPanY,
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
                        zOrderOverrides = current.zOrderOverrides,
                        lastCameraZoom = cameraZoom,
                        lastCameraPanX = cameraPanX,
                        lastCameraPanY = cameraPanY,
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
