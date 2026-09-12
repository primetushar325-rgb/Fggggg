package com.rigstudio.core.rig

import com.rigstudio.core.geom.Vec2
import com.rigstudio.core.model.Expression
import com.rigstudio.core.model.MouthShape

/**
 * Animated local state of one bone.
 *
 * Rotation is in degrees (positive = clockwise on screen). [offset] is a translation expressed
 * in **character-height fractions**, i.e. view units: because the assembled character is exactly
 * 1.0 units tall, the same clip data works at any output resolution with no rescaling.
 */
data class BonePose(
    val rotationDeg: Float = 0f,
    val offset: Vec2 = Vec2.ZERO,
    val scale: Float = 1f,
) {
    fun mirrored() = BonePose(-rotationDeg, Vec2(-offset.x, offset.y), scale)

    companion object {
        val REST = BonePose()
    }
}

/**
 * A fully resolved instant of an animation: every bone's local transform, the whole-body root
 * motion, and which facial sprites are showing.
 *
 * This is the single contract between the animation engine and every renderer (preview, MP4
 * export, thumbnail generation), which is why preview and export can never drift apart.
 */
/**
 * An accessory's animated state at one instant (V6: props with keyframes). Values override the
 * accessory definition's own transform; interpolation happens between keyframes.
 */
data class AccessoryState(
    val rotationDeg: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
)

data class Pose(
    val timeSeconds: Float = 0f,
    val root: BonePose = BonePose.REST,
    val bones: Map<String, BonePose> = emptyMap(),
    val expression: Expression = Expression.NEUTRAL,
    val mouth: MouthShape = MouthShape.CLOSED,
    /** Per-accessory animated state (V6); empty = every prop uses its definition's transform. */
    val accessories: Map<String, AccessoryState> = emptyMap(),
) {

    fun rotationOf(boneId: String): Float = bones[boneId]?.rotationDeg ?: 0f

    fun poseOf(boneId: String): BonePose = bones[boneId] ?: BonePose.REST

    /**
     * Applies a partial pose on top of this one: bones present in [partial] replace their
     * counterpart (whole [BonePose], not merged field-by-field), everything else keeps running.
     * This is what a pose-editor drag produces — the edited limb is pinned while the rest of
     * the animation keeps playing underneath it.
     */
    fun override(partial: Pose): Pose {
        if (partial.bones.isEmpty() && partial.root == BonePose.REST) return this
        val merged = LinkedHashMap<String, BonePose>(bones.size + partial.bones.size)
        merged.putAll(bones)
        merged.putAll(partial.bones)
        val mergedAccessories = if (partial.accessories.isEmpty()) {
            accessories
        } else {
            LinkedHashMap(accessories).apply { putAll(partial.accessories) }
        }
        return copy(
            root = if (partial.root == BonePose.REST) root else partial.root,
            bones = merged,
            accessories = mergedAccessories,
        )
    }

    /**
     * Horizontally mirrored pose: `_l` and `_r` swap and rotations/offsets flip sign, so bone
     * semantics survive the mirror (the left arm stays the left arm, it just appears on the
     * other side).
     */
    fun mirrored(): Pose {
        val swapped = HashMap<String, BonePose>(bones.size)
        for ((boneId, bonePose) in bones) {
            swapped[com.rigstudio.core.model.BoneIds.mirrorOf(boneId)] = bonePose.mirrored()
        }
        return Pose(
            timeSeconds = timeSeconds,
            root = root.mirrored(),
            bones = swapped,
            expression = expression,
            mouth = mouth,
        )
    }

    companion object {
        val REST = Pose()
    }
}
