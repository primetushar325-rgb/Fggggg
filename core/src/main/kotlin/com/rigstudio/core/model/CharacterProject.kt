package com.rigstudio.core.model

import com.rigstudio.core.geom.IntRect
import com.rigstudio.core.geom.Vec2
import com.rigstudio.core.rig.SpriteAsset

/** One extracted sprite as stored on disk (pixels live in a PNG next to the project file). */
data class SpriteManifestEntry(
    val slotId: String,
    val fileName: String,
    val width: Int,
    val height: Int,
    val pivotX: Float,
    val pivotY: Float,
    val coverage: Float,
    val sourceRect: IntRect,
    val contentRect: IntRect,
) {
    fun toAsset(): SpriteAsset = SpriteAsset(
        slotId = slotId,
        width = width,
        height = height,
        pivot = Vec2(pivotX, pivotY),
        coverage = coverage,
        sourceRect = sourceRect,
        contentRect = contentRect,
    )
}

/**
 * A saved character: everything needed to reopen it, rebuild its rigs and resume where the user
 * left off. Persisted as JSON plus PNG files in app-private storage — no accounts, no cloud.
 */
data class CharacterProject(
    val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastOpenedAtEpochMillis: Long,
    /** Original character sheet, copied into the project folder. */
    val sheetFileName: String,
    val sheetWidth: Int,
    val sheetHeight: Int,
    val thumbnailFileName: String?,
    val sprites: List<SpriteManifestEntry>,
    val availableViews: List<ViewKind>,
    val mirroredSideView: Boolean,
    val availableExpressions: List<Expression>,
    val availableMouthShapes: List<MouthShape>,
    /** Non-blocking validation findings, re-shown when the project is reopened. */
    val notes: List<String>,
    val lastClipId: String,
    val lastView: ViewKind,
    val lastBackgroundArgb: Int?,
    val lastSpeed: Float,
    val templateVersion: Int = 1,
    // --- V6 (schemaVersion 6) ---------------------------------------------------------------
    /** Camera the stage was left at (1 = fit); preserved across view switches and reopens. */
    val lastCameraZoom: Float = 1f,
    val lastCameraPanX: Float = 0f,
    val lastCameraPanY: Float = 0f,
    /**
     * Manual z-order edits: universal slot id → new z. Empty means the rig's authored order
     * is untouched; entries only exist for parts the user actually moved.
     */
    val zOrderOverrides: Map<String, Int> = emptyMap(),
    /** User-saved poses in this project (V6 §47), persisted beside the manifest. */
    val posePresetIds: List<String> = emptyList(),
    /** User-authored animation ids in this project (V6 §48). */
    val userClipIds: List<String> = emptyList(),
) {
    fun spriteAssets(): Map<String, SpriteAsset> = sprites.associate { it.slotId to it.toAsset() }

    fun hasView(view: ViewKind): Boolean = view in availableViews

    val hasProfileArtwork: Boolean
        get() = ViewKind.SIDE_LEFT in availableViews || ViewKind.SIDE_RIGHT in availableViews

    fun renamed(newName: String, nowEpochMillis: Long) =
        copy(name = newName, updatedAtEpochMillis = nowEpochMillis)

    fun touched(nowEpochMillis: Long) = copy(
        lastOpenedAtEpochMillis = nowEpochMillis,
        updatedAtEpochMillis = nowEpochMillis,
    )

    /** A copy of this project with a fresh id and name, for "Duplicate". */
    fun duplicated(newId: String, newName: String, nowEpochMillis: Long) = copy(
        id = newId,
        name = newName,
        createdAtEpochMillis = nowEpochMillis,
        updatedAtEpochMillis = nowEpochMillis,
        lastOpenedAtEpochMillis = nowEpochMillis,
    )
}
