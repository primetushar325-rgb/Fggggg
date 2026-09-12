package com.rigstudio.core.anim

import com.rigstudio.core.geom.Affine
import com.rigstudio.core.geom.Vec2
import com.rigstudio.core.rig.BoneConstraints
import com.rigstudio.core.rig.CharacterRig
import com.rigstudio.core.rig.ForwardKinematics
import com.rigstudio.core.rig.Pose
import com.rigstudio.core.rig.RigBone

/**
 * Interactive pose editing (V6 §9–§10): the bridge between a finger drag on the stage and the
 * [TwoBoneIk] solver.
 *
 * All geometry happens in **view units** (character height = 1.0), the same space bones and
 * cameras live in — never screen pixels. The caller converts a touch to view units once (by
 * inverting the camera), everything here is pure and deterministic, so a pose dragged on a
 * phone screen and one dragged in a unit test produce identical results.
 */
object PoseEditing {

    /** Where a chain's three joints sit for the current pose, in view units. */
    data class ChainJoints(
        /** Shoulder / hip. */
        val root: Vec2,
        /** Elbow / knee. */
        val mid: Vec2,
        /** Wrist / ankle (or the lower bone's far anchor when the part was never drawn). */
        val end: Vec2,
    )

    /**
     * Resolves the chain joints for [pose] by running forward kinematics — the same solver that
     * paints the frame, so the handles sit exactly where the artwork shows them.
     */
    fun chainJoints(rig: CharacterRig, pose: Pose, chain: IkChain): ChainJoints? {
        val upper = rig.bone(chain.upperId) ?: return null
        val lower = rig.bone(chain.lowerId) ?: return null
        val solution = ForwardKinematics.solve(rig, pose)
        val rootJoint = solution.transformOf(upper.id).transform(upper.joint)
        val midJoint = solution.transformOf(lower.id).transform(lower.joint)
        val endJoint = endAnchor(solution.transformOf(lower.id), lower, chain, rig)
        return ChainJoints(rootJoint, midJoint, endJoint)
    }

    /**
     * The draggable handle: the effector bone's joint when the sheet provides one (the standard
     * case), otherwise the lower bone's far end — the artwork's own pivot mirrored across its
     * centre, which is where a wrist/ankle sits for a part drawn elbow-to-wrist.
     */
    private fun endAnchor(
        lowerWorld: Affine,
        lower: RigBone,
        chain: IkChain,
        rig: CharacterRig,
    ): Vec2 {
        rig.bone(chain.effectorId)?.let { effector ->
            return lowerWorld.transform(effector.joint)
        }
        val rect = lower.restRect
        if (rect.isEmpty()) return lowerWorld.transform(lower.joint)
        val pivot = lower.effectivePivot
        val anchor = Vec2(
            rect.left + pivot.x * rect.width,
            rect.top + (1f - pivot.y) * rect.height,
        )
        return lowerWorld.transform(anchor)
    }

    /**
     * Result of one drag step: the pose with the chain retargeted (constraint-clamped) and how
     * close the solved effector actually got to the requested target.
     */
    data class DragResult(
        val pose: Pose,
        val solution: TwoBoneIk.Solution,
        /** Solved effector position after applying the pose — what the user will see. */
        val achieved: Vec2,
    )

    /**
     * Drags [chain]'s effector toward [target] (view units) starting from [pose], returning the
     * edited pose. Only the two chain bones' rotations change; every other bone keeps whatever
     * it had, so the rest of the animation is untouched.
     *
     * Rotations are clamped to the rig's constraint table before being written, which is why a
     * knee can never bend backwards even under a wild drag.
     */
    fun dragChain(
        rig: CharacterRig,
        pose: Pose,
        chain: IkChain,
        target: Vec2,
    ): DragResult? {
        val upper = rig.bone(chain.upperId) ?: return null
        val lower = rig.bone(chain.lowerId) ?: return null
        val joints = chainJoints(rig, pose, chain) ?: return null

        val solution = TwoBoneIk.solve(
            upperFrom = joints.root,
            upperTo = joints.mid,
            lowerTo = joints.end,
            target = target,
            bend = chain.preferredBend,
        )

        // FK applies rotation · rotationSign, so a view-space angle delta converts back to a
        // pose rotation delta by dividing by the same sign.
        val upperSign = if (upper.flipX) -1f else 1f
        val lowerSign = if (lower.flipX) -1f else 1f
        val upperTarget = clampRotation(upper, pose.rotationOf(upper.id) + solution.upperDeltaDeg / upperSign)
        val lowerTarget = clampRotation(lower, pose.rotationOf(lower.id) + solution.lowerDeltaDeg / lowerSign)

        val edited = pose.copy(
            bones = pose.bones + mapOf(
                upper.id to pose.poseOf(upper.id).copy(rotationDeg = upperTarget),
                lower.id to pose.poseOf(lower.id).copy(rotationDeg = lowerTarget),
            ),
        )

        val achieved = chainJoints(rig, edited, chain)?.end ?: target
        return DragResult(edited, solution, achieved)
    }

    /** Nearest chain handle to a view-space point, within [maxDistance] view units. */
    fun nearestChain(
        rig: CharacterRig,
        pose: Pose,
        point: Vec2,
        maxDistance: Float = 0.08f,
    ): Pair<IkChain, Vec2>? {
        var best: Pair<IkChain, Vec2>? = null
        var bestDistance = maxDistance
        for (chain in IkChain.ALL) {
            val end = chainJoints(rig, pose, chain)?.end ?: continue
            val distance = end.minus(point).length()
            if (distance <= bestDistance) {
                bestDistance = distance
                best = chain to end
            }
        }
        return best
    }

    private fun clampRotation(bone: RigBone, degrees: Float): Float {
        val constraint = bone.constraint.takeIf { it.minRotationDeg != 0f || it.maxRotationDeg != 0f }
            ?: BoneConstraints.forBone(bone.id)
        return degrees.coerceIn(constraint.minRotationDeg, constraint.maxRotationDeg)
    }
}
