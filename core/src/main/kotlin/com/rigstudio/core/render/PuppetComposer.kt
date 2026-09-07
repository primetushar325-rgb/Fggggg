package com.rigstudio.core.render

import com.rigstudio.core.geom.Affine
import com.rigstudio.core.geom.FloatRect
import com.rigstudio.core.geom.Vec2
import com.rigstudio.core.model.BoneIds
import com.rigstudio.core.rig.AccessoryDef
import com.rigstudio.core.rig.CharacterRig
import com.rigstudio.core.rig.FaceSet
import com.rigstudio.core.rig.FkSolution
import com.rigstudio.core.rig.ForwardKinematics
import com.rigstudio.core.rig.Pose
import com.rigstudio.core.rig.SpriteAsset
import com.rigstudio.core.util.MathUtils

/** What a draw call is for: body artwork, or one of the face overlays. */
enum class PuppetPart { BODY, EYES, MOUTH }

/**
 * One sprite to blit, fully resolved.
 *
 * [world] maps the sprite's rest-space rectangle ([restRect], in view units) into output space.
 * A renderer therefore needs no geometry knowledge at all: it converts [world] to a matrix,
 * draws its bitmap into [restRect] and moves on. Because the same list drives the on-screen
 * preview and the video encoder, the exported MP4 is frame-identical to what the user saw.
 */
data class PuppetDraw(
    /** Slot id of the artwork — the key into the app's bitmap cache. */
    val slotId: String,
    val sprite: SpriteAsset,
    val world: Affine,
    val restRect: FloatRect,
    val z: Int,
    /** Depth shading (1 = full colour, <1 = a limb sitting behind the body). */
    val shade: Float,
    val part: PuppetPart,
) {
    /** Centre of the artwork in output space — handy for hit testing and thumbnails. */
    val centre get() = world.transform(restRect.centerX, restRect.centerY)
}

/**
 * Turns a pose into an ordered draw list: the whole "what does the character look like" decision.
 *
 * Body parts come from forward kinematics (which already resolves per-pose layering, so a walking
 * character swaps its legs in front of and behind the torso). Face sprites are attached to the
 * head's transform, so they rotate, translate and mirror with it — and a view without face
 * artwork, or a back view, simply produces none.
 */
object PuppetComposer {

    /** Face layers sit above the head (`z = 40`) and below nothing else. */
    const val EYES_Z = 60
    const val MOUTH_Z = 61

    fun compose(
        rig: CharacterRig,
        pose: Pose,
        viewTransform: Affine = Affine.IDENTITY,
        accessories: List<AccessoryDef> = emptyList(),
    ): List<PuppetDraw> {
        val solution = ForwardKinematics.solve(rig, pose, viewTransform)
        val draws = ArrayList<PuppetDraw>(solution.draws.size + 2 + accessories.size)

        for (draw in solution.draws) {
            val sprite = draw.bone.sprite ?: continue
            draws += PuppetDraw(
                slotId = sprite.slotId,
                sprite = sprite,
                world = draw.world,
                restRect = draw.restRect,
                z = draw.z,
                shade = draw.depthShade,
                part = PuppetPart.BODY,
            )
        }
        draws += faceDraws(rig, solution, pose)
        draws += accessoryDraws(rig, solution, accessories)
        draws.sortWith(compareBy({ it.z }, { it.slotId }))
        return draws
    }

    /**
     * Props/accessories (V6): each one inherits its host bone's full animated transform — the
     * same chain that paints the limb — then adds the user's own rotation/offset/scale on top.
     * A hat follows the head through walk, run and pose-editor drags with no extra keyframes.
     */
    private fun accessoryDraws(
        rig: CharacterRig,
        solution: FkSolution,
        accessories: List<AccessoryDef>,
    ): List<PuppetDraw> {
        if (accessories.isEmpty()) return emptyList()
        val draws = ArrayList<PuppetDraw>(accessories.size)
        for (accessory in accessories) {
            val bone = rig.bone(accessory.attachBoneId) ?: continue
            val boneWorld = solution.transformOf(bone.id)
            val restRect = bone.restRect
            if (restRect.isEmpty()) continue

            // The joint inside the host bone (fractions of its rest rect, y down).
            val anchor = Vec2(
                restRect.left + accessory.anchorX * restRect.width,
                restRect.top + accessory.anchorY * restRect.height,
            )
            // Same local formulation as a bone: rotate/scale about the anchor, offset the joint.
            var local = Affine.translation(anchor.x + accessory.offsetX, anchor.y + accessory.offsetY)
                .multiply(Affine.rotation(MathUtils.degToRad(accessory.rotationDeg)))
            if (accessory.scale != 1f) {
                local = local.multiply(Affine.scaling(accessory.scale))
            }
            local = local.multiply(Affine.translation(-anchor.x, -anchor.y))
            val world = boneWorld.multiply(local)

            // Accessory art rect in bone rest space, placed by its own pivot at the anchor.
            val sprite = accessory.toSpriteAsset()
            val h = accessory.targetHeight
            val w = h * sprite.aspect
            val rect = FloatRect(
                left = anchor.x + accessory.offsetX - accessory.pivotX * w,
                top = anchor.y + accessory.offsetY - accessory.pivotY * h,
                right = anchor.x + accessory.offsetX + (1f - accessory.pivotX) * w,
                bottom = anchor.y + accessory.offsetY + (1f - accessory.pivotY) * h,
            )
            draws += PuppetDraw(
                slotId = accessory.id,
                sprite = sprite,
                world = world,
                restRect = rect,
                z = accessory.z,
                shade = 1f,
                part = PuppetPart.BODY,
            )
        }
        return draws
    }

    /**
     * Applies the project's manual z-order edits (V6 §26): a draw whose slot has an override is
     * re-stamped and the list re-sorted, which is exactly how "bring forward / send backward /
     * to front / to back" behaves. Overrides that collide keep the slot-id tiebreak, so the
     * result is deterministic whatever the user does.
     */
    fun applyZOverrides(
        draws: List<PuppetDraw>,
        overrides: Map<String, Int>,
    ): List<PuppetDraw> {
        if (overrides.isEmpty()) return draws
        val reZ = draws.map { draw ->
            val override = overrides[draw.slotId]
            if (override == null || override == draw.z) draw else draw.copy(z = override)
        }
        return reZ.sortedWith(compareBy({ it.z }, { it.slotId }))
    }

    /**
     * Eye and mouth overlays for the current pose.
     *
     * Anchors are fractions of the head's rest rectangle, so the face scales with whatever head
     * the user drew. Sprites the user did not draw fall back through [com.rigstudio.core.rig.FaceSet],
     * which is why Talk still plays — silently — when no mouth shapes exist.
     */
    private fun faceDraws(
        rig: CharacterRig,
        solution: FkSolution,
        pose: Pose,
    ): List<PuppetDraw> {
        if (rig.faceSet.isEmpty()) return emptyList()
        val headDraw = solution.draws.firstOrNull { it.bone.id == BoneIds.HEAD } ?: return emptyList()
        val headRect = headDraw.restRect
        if (headRect.isEmpty()) return emptyList()

        val anchors = rig.faceAnchors
        val out = ArrayList<PuppetDraw>(2)

        rig.faceSet.eyeSprite(pose.expression)?.let { eyes ->
            centredRect(headRect, anchors.eyeCenter.x, anchors.eyeCenter.y, anchors.eyeWidth, eyes)?.let { rect ->
                out += PuppetDraw(
                    slotId = eyes.slotId,
                    sprite = eyes,
                    // The head's draw transform already contains its mirror, so profile faces
                    // land on the facing side of the head and flip with the artwork.
                    world = headDraw.world,
                    restRect = rect,
                    z = EYES_Z,
                    shade = 1f,
                    part = PuppetPart.EYES,
                )
            }
        }

        rig.faceSet.mouthSprite(pose.mouth)?.let { mouth ->
            centredRect(headRect, anchors.mouthCenter.x, anchors.mouthCenter.y, anchors.mouthWidth, mouth)
                ?.let { rect ->
                    out += PuppetDraw(
                        slotId = mouth.slotId,
                        sprite = mouth,
                        world = headDraw.world,
                        restRect = rect,
                        z = MOUTH_Z,
                        shade = 1f,
                        part = PuppetPart.MOUTH,
                    )
                }
        }
        return out
    }

    /**
     * Rectangle of width `widthFraction * head width`, centred on an anchor point, with the
     * height derived from the sprite's own aspect so faces never stretch.
     */
    private fun centredRect(
        headRect: FloatRect,
        anchorX: Float,
        anchorY: Float,
        widthFraction: Float,
        sprite: SpriteAsset,
    ): FloatRect? {
        val width = headRect.width * widthFraction
        if (width <= 0f || sprite.aspect <= 0f) return null
        val height = width / sprite.aspect
        val cx = headRect.left + headRect.width * anchorX
        val cy = headRect.top + headRect.height * anchorY
        return FloatRect(cx - width * 0.5f, cy - height * 0.5f, cx + width * 0.5f, cy + height * 0.5f)
    }

    private fun FaceSet.isEmpty(): Boolean = !hasEyes && !hasMouths
}
