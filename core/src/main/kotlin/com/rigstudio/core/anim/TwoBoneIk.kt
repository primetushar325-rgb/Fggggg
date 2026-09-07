package com.rigstudio.core.anim

import com.rigstudio.core.geom.Vec2
import com.rigstudio.core.model.BoneIds
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2

/**
 * A stable, analytic **2-bone IK chain** (V6 §9): upper bone → lower bone → effector.
 *
 * Chains are expressed in universal bone ids, so the same solver works for arms
 * (shoulder→elbow→wrist) and legs (hip→knee→ankle) in every view, including mirrored
 * side views (the caller applies the rig's rotation sign).
 */
data class IkChain(
    val id: String,
    val upperId: String,
    val lowerId: String,
    /** Bone whose joint is the dragged handle (wrist / ankle). */
    val effectorId: String,
    /** +1 / −1: which way the middle joint prefers to bend (pole, V6 §9). */
    val preferredBend: Float,
) {
    companion object {
        /** The four draggable chains of the standard rig. */
        val HAND_L = IkChain("hand_l", BoneIds.UPPER_ARM_L, BoneIds.FOREARM_L, BoneIds.HAND_L, +1f)
        val HAND_R = IkChain("hand_r", BoneIds.UPPER_ARM_R, BoneIds.FOREARM_R, BoneIds.HAND_R, -1f)
        val FOOT_L = IkChain("foot_l", BoneIds.THIGH_L, BoneIds.SHIN_L, BoneIds.FOOT_L, -1f)
        val FOOT_R = IkChain("foot_r", BoneIds.THIGH_R, BoneIds.SHIN_R, BoneIds.FOOT_R, +1f)

        val ALL = listOf(HAND_L, HAND_R, FOOT_L, FOOT_R)

        fun byId(id: String): IkChain? = ALL.firstOrNull { it.id == id }
    }
}

/**
 * Incremental two-bone IK: given the chain's **current** joint positions and a target for the
 * effector, returns the local rotation deltas that bring the effector as close to the target as
 * the bone lengths allow.
 *
 * Formulation (law of cosines, the classic stable 2D solver):
 *  1. clamp the reach distance `d` to `[|L1−L2|+ε, L1+L2−ε]` — the limb never stretches;
 *  2. the elbow sits at `root + rotate(dir(root→target), bend·α)·L1` where `α` comes from the
 *     law of cosines, so `bend` picks the elbow side and it can never flip 180° mid-drag;
 *  3. deltas are measured against the *current* directions, which makes the solver stable when
 *     dragged continuously from any starting pose.
 *
 * Everything is pure math on points — no rig, no Android — so it is fully unit-testable and
 * identical in preview and export.
 */
object TwoBoneIk {

    /** Result of one solve: local rotation deltas in degrees. */
    data class Solution(
        val upperDeltaDeg: Float,
        val lowerDeltaDeg: Float,
        /** True when the target is inside (or at) the reach circle — no clamping happened. */
        val reached: Boolean,
    )

    private const val EPS = 1e-4f

    /**
     * @param upperFrom current position of the chain root joint (shoulder / hip)
     * @param upperTo current position of the middle joint (elbow / knee); also the lower bone's
     *   current `from`
     * @param lowerTo current position of the effector joint (wrist / ankle)
     * @param target where the user wants the effector, in the same space
     * @param bend +1 / −1 pole for the middle joint
     */
    fun solve(
        upperFrom: Vec2,
        upperTo: Vec2,
        lowerTo: Vec2,
        target: Vec2,
        bend: Float,
    ): Solution {
        val l1 = upperFrom.minus(upperTo).length()
        val l2 = upperTo.minus(lowerTo).length()
        if (l1 <= EPS || l2 <= EPS) return Solution(0f, 0f, reached = true)

        val toTarget = target.minus(upperFrom)
        val requested = toTarget.length()
        if (requested <= EPS) return Solution(0f, 0f, reached = true)

        val maxReach = l1 + l2 - EPS
        val minReach = abs(l1 - l2) + EPS
        val d = requested.coerceIn(minReach, maxReach)
        val reached = requested <= maxReach + 1e-3f

        val dirX = toTarget.x / requested
        val dirY = toTarget.y / requested

        // Law of cosines: angle at the root between (root→target) and (root→elbow).
        val cosAlpha = ((l1 * l1 + d * d - l2 * l2) / (2f * l1 * d)).coerceIn(-1f, 1f)
        val alpha = acos(cosAlpha)
        val pole = if (bend >= 0f) 1f else -1f

        // Elbow position for the clamped target: dir(root→target) rotated by ±alpha.
        val rotAngle = pole * alpha
        val c = kotlin.math.cos(rotAngle)
        val s = kotlin.math.sin(rotAngle)
        val elbowX = upperFrom.x + (dirX * c - dirY * s) * l1
        val elbowY = upperFrom.y + (dirX * s + dirY * c) * l1
        // Clamped wrist position.
        val wristX = upperFrom.x + dirX * d
        val wristY = upperFrom.y + dirY * d

        val newUpperAngle = atan2(elbowY - upperFrom.y, elbowX - upperFrom.x)
        val newLowerAngle = atan2(wristY - elbowY, wristX - elbowX)
        val curUpperAngle = atan2(upperTo.y - upperFrom.y, upperTo.x - upperFrom.x)
        val curLowerAngle = atan2(lowerTo.y - upperTo.y, lowerTo.x - upperTo.x)

        val upperDelta = wrapDeg(toDeg(newUpperAngle - curUpperAngle))
        // The lower bone's local rotation is measured relative to its parent's direction.
        val lowerDelta = wrapDeg(
            toDeg((newLowerAngle - newUpperAngle) - (curLowerAngle - curUpperAngle)),
        )
        return Solution(upperDelta, lowerDelta, reached)
    }

    /**
     * Convenience for chains still at rest (all joints on one axis, e.g. a limb hanging
     * straight down): solves directly for absolute local rotations measured from the rest
     * direction, which is what a pose *preset* wants.
     */
    fun solveRest(
        root: Vec2,
        mid: Vec2,
        end: Vec2,
        target: Vec2,
        bend: Float,
    ): Solution = solve(root, mid, end, target, bend)

    /** FK round-trip helper used by tests and by callers that want to verify the result. */
    fun endPositionAfter(
        root: Vec2,
        mid: Vec2,
        end: Vec2,
        solution: Solution,
    ): Vec2 {
        val upperAngle = atan2(mid.y - root.y, mid.x - root.x)
        val lowerAngle = atan2(end.y - mid.y, end.x - mid.x)
        val l1 = root.minus(mid).length()
        val l2 = mid.minus(end).length()
        val a1 = upperAngle + toRad(solution.upperDeltaDeg)
        // world lower angle = parent world angle + local (rest-relative + delta)
        val a2 = lowerAngle + toRad(solution.upperDeltaDeg + solution.lowerDeltaDeg)
        return Vec2(
            root.x + kotlin.math.cos(a1) * l1 + kotlin.math.cos(a2) * l2,
            root.y + kotlin.math.sin(a1) * l1 + kotlin.math.sin(a2) * l2,
        )
    }

    private fun toDeg(rad: Float): Float = rad * 180f / PI.toFloat()

    private fun toRad(deg: Float): Float = deg * PI.toFloat() / 180f

    private fun wrapDeg(deg: Float): Float {
        var d = deg
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    /** Furthest the effector can reach from the root: the fully extended chain (L1+L2). */
    fun maxReach(root: Vec2, mid: Vec2, end: Vec2): Float =
        root.minus(mid).length() + mid.minus(end).length()

    /** Straight-line root→effector length of the *current* pose (≤ [maxReach]). */
    fun currentReach(root: Vec2, end: Vec2): Float = root.minus(end).length()
}
