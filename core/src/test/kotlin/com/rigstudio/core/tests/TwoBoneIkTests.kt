package com.rigstudio.core.tests

import com.rigstudio.core.anim.IkChain
import com.rigstudio.core.anim.TwoBoneIk
import com.rigstudio.core.geom.Vec2
import com.rigstudio.core.harness.Assert
import com.rigstudio.core.harness.TestCase
import com.rigstudio.core.model.BoneIds
import kotlin.math.abs

/**
 * Analytic 2-bone IK (V6 §9): reach accuracy, pole stability (no 180° flips), clamped
 * over-extension and the FK round-trip the gesture layer depends on.
 */
object TwoBoneIkTests {

    private const val TOL = 0.02f

    /** A classic arm: shoulder at origin, elbow below, wrist further below (T-pose-ish hang). */
    private val shoulder = Vec2(0f, 0f)
    private val elbow = Vec2(0f, -0.3f)
    private val wrist = Vec2(0f, -0.55f)

    private fun assertClose(expected: Vec2, actual: Vec2, label: String, tol: Float = TOL) {
        Assert.that(abs(expected.x - actual.x) <= tol && abs(expected.y - actual.y) <= tol) {
            "$label: expected ~(${"%.3f".format(expected.x)}, ${"%.3f".format(expected.y)}) " +
                "but got (${"%.3f".format(actual.x)}, ${"%.3f".format(actual.y)})"
        }
    }

    val cases: List<TestCase> = listOf(

        TestCase("the four standard chains target the universal bones") {
            for (chain in IkChain.ALL) {
                Assert.that(BoneIds.isKnown(chain.upperId)) { "${chain.id}: unknown upper '${chain.upperId}'" }
                Assert.that(BoneIds.isKnown(chain.lowerId)) { "${chain.id}: unknown lower '${chain.lowerId}'" }
                Assert.that(BoneIds.isKnown(chain.effectorId)) { "${chain.id}: unknown effector '${chain.effectorId}'" }
                Assert.that(abs(chain.preferredBend) == 1f) { "${chain.id}: pole must be ±1" }
            }
            Assert.equals(4, IkChain.ALL.size, "hand/foot chains")
            Assert.that(IkChain.ALL.map { it.id }.distinct().size == 4) { "chain ids must be unique" }
        },

        TestCase("a reachable target is reached exactly") {
            // 45° forward reach, comfortably inside the 0.85 reach circle.
            val target = Vec2(0.4f, -0.35f)
            val sol = TwoBoneIk.solve(shoulder, elbow, wrist, target, bend = 1f)
            Assert.that(sol.reached) { "inside the reach circle the solver must report reached" }
            val end = TwoBoneIk.endPositionAfter(shoulder, elbow, wrist, sol)
            assertClose(target, end, "reachable target round-trip")
        },

        TestCase("an unreachable target is clamped to max reach along the same direction") {
            val target = Vec2(0f, -5f) // far beyond l1 + l2 = 0.55
            val sol = TwoBoneIk.solve(shoulder, elbow, wrist, target, bend = 1f)
            Assert.that(!sol.reached) { "a target 5 units away cannot be reached" }
            val end = TwoBoneIk.endPositionAfter(shoulder, elbow, wrist, sol)
            val maxReach = TwoBoneIk.maxReach(shoulder, elbow, wrist)
            Assert.that(abs(end.length() - maxReach) <= TOL) {
                "clamped end must sit on the reach circle: |end|=${"%.3f".format(end.length())} max=$maxReach"
            }
            // Same direction as the request: straight down.
            assertClose(Vec2(0f, -maxReach), end, "clamped direction preserved")
        },

        TestCase("the pole keeps the elbow on the chosen side of the target line") {
            val target = Vec2(0.35f, -0.3f)
            val dirX = 0.35f / 0.4609772f
            val dirY = -0.3f / 0.4609772f
            val solutions = HashMap<Float, Pair<Vec2, Vec2>>()
            for (bend in listOf(1f, -1f)) {
                val sol = TwoBoneIk.solve(shoulder, elbow, wrist, target, bend)
                val end = TwoBoneIk.endPositionAfter(shoulder, elbow, wrist, sol)
                // Elbow for this bend, derived from the solved upper delta.
                val upperAngle = kotlin.math.atan2(elbow.y - shoulder.y, elbow.x - shoulder.x)
                val a1 = upperAngle + Math.toRadians(sol.upperDeltaDeg.toDouble())
                val mid = Vec2(
                    shoulder.x + kotlin.math.cos(a1).toFloat() * 0.3f,
                    shoulder.y + kotlin.math.sin(a1).toFloat() * 0.3f,
                )
                solutions[bend] = mid to end
                // Pole = which side of the root→target line the elbow sits on: the sign of
                // cross(dir, elbowDir) must match the requested bend.
                val elbowDirX = (mid.x - shoulder.x) / 0.3f
                val elbowDirY = (mid.y - shoulder.y) / 0.3f
                val cross = dirX * elbowDirY - dirY * elbowDirX
                val pole = if (bend >= 0f) 1f else -1f
                Assert.that(cross * pole > 0f) {
                    "bend $bend: elbow must sit on the pole side of the target line (cross=${"%.3f".format(cross)})"
                }
                assertClose(target, end, "bend $bend still reaches the target")
            }
            // The two poles produce genuinely different elbow positions (no silent ignore).
            Assert.that((solutions[1f]!!.first - solutions[-1f]!!.first).length() > 0.1f) {
                "bend +1 and −1 must place the elbow at different spots"
            }
        },

        TestCase("dragging continuously never flips the elbow 180 degrees") {
            // Simulated drag: 40 small steps around an arc that stays well inside the reach
            // annulus (|d| ∈ [0.1, 0.5] vs min 0.05 / max 0.55), always solving from the
            // CURRENT pose — exactly what a finger drag does.
            var mid = elbow
            var end = wrist
            var lastSide = 0f
            for (step in 0..40) {
                val angle = Math.toRadians((step * 3.0) - 30.0)
                val target = Vec2(
                    0.2f + 0.2f * kotlin.math.cos(angle).toFloat(),
                    -0.3f + 0.2f * kotlin.math.sin(angle).toFloat(),
                )
                val sol = TwoBoneIk.solve(shoulder, mid, end, target, bend = 1f)
                Assert.that(sol.reached) { "arc targets stay reachable (step $step)" }
                // Step 0 repositions from rest (a large but legitimate move); afterwards every
                // step is a small drag and any jump near 180° would mean a pole flip.
                val limit = if (step == 0) 120f else 45f
                Assert.that(abs(sol.upperDeltaDeg) < limit) {
                    "step $step: suspicious jump of ${"%.1f".format(sol.upperDeltaDeg)}° — a pole flip"
                }
                val next = TwoBoneIk.endPositionAfter(shoulder, mid, end, sol)
                // Recompute the mid joint for the next iteration from the new upper angle.
                val upperAngle = kotlin.math.atan2(mid.y - shoulder.y, mid.x - shoulder.x)
                val a1 = upperAngle + Math.toRadians(sol.upperDeltaDeg.toDouble())
                mid = Vec2(shoulder.x + kotlin.math.cos(a1).toFloat() * 0.3f, shoulder.y + kotlin.math.sin(a1).toFloat() * 0.3f)
                end = next
                val side = if (mid.x > 0f) 1f else -1f
                if (lastSide != 0f) {
                    Assert.that(side == lastSide) { "elbow flipped sides at step $step" }
                }
                lastSide = side
            }
        },

        TestCase("deltas are incremental and small for small target moves") {
            val t1 = Vec2(0.30f, -0.35f)
            val t2 = Vec2(0.32f, -0.35f)
            val sol1 = TwoBoneIk.solve(shoulder, elbow, wrist, t1, bend = 1f)
            // Apply sol1 to get the new pose, then chase a tiny move from there.
            val upperAngle = kotlin.math.atan2(elbow.y - shoulder.y, elbow.x - shoulder.x)
            val a1 = upperAngle + Math.toRadians(sol1.upperDeltaDeg.toDouble())
            val newMid = Vec2(shoulder.x + kotlin.math.cos(a1).toFloat() * 0.3f, shoulder.y + kotlin.math.sin(a1).toFloat() * 0.3f)
            val newEnd = TwoBoneIk.endPositionAfter(shoulder, elbow, wrist, sol1)
            val sol2 = TwoBoneIk.solve(shoulder, newMid, newEnd, t2, bend = 1f)
            Assert.that(abs(sol2.upperDeltaDeg) < 8f && abs(sol2.lowerDeltaDeg) < 12f) {
                "a 0.02 move should need small deltas, got ${sol2.upperDeltaDeg}° / ${sol2.lowerDeltaDeg}°"
            }
            assertClose(t2, TwoBoneIk.endPositionAfter(shoulder, newMid, newEnd, sol2), "second chase reaches")
        },

        TestCase("degenerate inputs are rejected safely") {
            // Zero-length bones: solver no-ops instead of dividing by zero.
            val sol = TwoBoneIk.solve(Vec2.ZERO, Vec2.ZERO, Vec2.ZERO, Vec2(1f, 1f), bend = 1f)
            Assert.equals(0f, sol.upperDeltaDeg, "degenerate upper delta")
            Assert.equals(0f, sol.lowerDeltaDeg, "degenerate lower delta")
            // Target exactly on the root: no-op, reached.
            val sol2 = TwoBoneIk.solve(shoulder, elbow, wrist, shoulder, bend = 1f)
            Assert.equals(0f, sol2.upperDeltaDeg, "target-on-root upper delta")
            Assert.equals(0f, sol2.lowerDeltaDeg, "target-on-root lower delta")
        },

        TestCase("max reach equals the fully extended chain") {
            val maxReach = TwoBoneIk.maxReach(shoulder, elbow, wrist)
            Assert.that(abs(maxReach - 0.55f) <= TOL) { "0.3 + 0.25 = 0.55, got $maxReach" }
            Assert.that(TwoBoneIk.currentReach(shoulder, wrist) <= maxReach + TOL) {
                "current reach can never exceed max reach"
            }
        },
    )
}
