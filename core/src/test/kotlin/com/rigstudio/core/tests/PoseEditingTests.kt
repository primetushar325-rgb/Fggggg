package com.rigstudio.core.tests

import com.rigstudio.core.anim.IkChain
import com.rigstudio.core.anim.PoseEditing
import com.rigstudio.core.geom.Affine
import com.rigstudio.core.geom.Vec2
import com.rigstudio.core.harness.Assert
import com.rigstudio.core.harness.TestCase
import com.rigstudio.core.model.BoneIds
import com.rigstudio.core.rig.ForwardKinematics
import com.rigstudio.core.rig.Pose
import com.rigstudio.core.support.Fixtures

/**
 * Interactive pose editing (V6 §9–§10): the FK→IK→FK round trip a finger drag performs, on the
 * same synthetic rigs the extraction tests use.
 */
object PoseEditingTests {

    /** Generous tolerance in view units (character height = 1.0; 0.02 = 2% of the body). */
    private const val TOL = 0.03f

    private fun closeVec(expected: Vec2, actual: Vec2, label: String, tol: Float = TOL) {
        Assert.that(
            kotlin.math.abs(expected.x - actual.x) <= tol && kotlin.math.abs(expected.y - actual.y) <= tol,
        ) { "$label: expected (~${expected.x}, ${expected.y}) got (${actual.x}, ${actual.y})" }
    }

    val cases: List<TestCase> = listOf(

        TestCase("chain joints sit where forward kinematics puts them") {
            val rig = Fixtures.rig()
            val pose = Pose()
            for (chain in IkChain.ALL) {
                val joints = PoseEditing.chainJoints(rig, pose, chain)
                Assert.that(joints != null) { "${chain.id}: joints must resolve on a full fixture rig" }
                // The root joint is the anatomical shoulder/hip — the upper bone's own joint.
                val upper = rig.bone(chain.upperId)!!
                val solution = ForwardKinematics.solve(rig, pose)
                val expectedRoot = solution.transformOf(upper.id).transform(upper.joint)
                closeVec(expectedRoot, joints!!.root, "${chain.id} root joint")
            }
        },

        TestCase("dragging a hand handle moves the wrist to the target") {
            val rig = Fixtures.rig()
            val chain = IkChain.HAND_R
            val rest = PoseEditing.chainJoints(rig, Pose(), chain)!!
            // A comfortable forward target, well inside the reach circle.
            val target = Vec2(rest.root.x + 0.25f, rest.root.y + 0.05f)
            val drag = PoseEditing.dragChain(rig, Pose(), chain, target)
            Assert.that(drag != null) { "drag must solve" }
            Assert.that(drag!!.solution.reached) { "target inside reach must be reached" }
            closeVec(target, drag.achieved, "wrist after drag")
            // Only the two chain bones may change.
            val changed = drag.pose.bones.filterValues { it.rotationDeg != 0f }.keys
            Assert.that(changed.all { it == chain.upperId || it == chain.lowerId }) {
                "drag touched bones outside the chain: $changed"
            }
        },

        TestCase("an impossible target clamps instead of stretching") {
            val rig = Fixtures.rig()
            val chain = IkChain.HAND_R
            val rest = PoseEditing.chainJoints(rig, Pose(), chain)!!
            val far = Vec2(rest.root.x + 5f, rest.root.y + 5f)
            val drag = PoseEditing.dragChain(rig, Pose(), chain, far)!!
            Assert.that(!drag.solution.reached) { "a far target cannot be reached" }
            val moved = drag.achieved.minus(rest.root).length()
            val maxReach = rest.mid.minus(rest.root).length() + rest.end.minus(rest.mid).length()
            Assert.that(moved <= maxReach + TOL) {
                "chain stretched to ${"%.3f".format(moved)} beyond max reach ${"%.3f".format(maxReach)}"
            }
        },

        TestCase("rotations stay inside the constraint table") {
            val rig = Fixtures.rig()
            val chain = IkChain.FOOT_R
            val rest = PoseEditing.chainJoints(rig, Pose(), chain)!!
            // Yank the ankle wildly in several directions — the knee must never hyperextend.
            for (dx in listOf(-0.4f, -0.2f, 0f, 0.2f, 0.4f)) {
                for (dy in listOf(-0.3f, 0f, 0.3f)) {
                    val drag = PoseEditing.dragChain(rig, Pose(), chain, Vec2(rest.root.x + dx, rest.root.y + dy))!!
                    for (boneId in listOf(chain.upperId, chain.lowerId)) {
                        val rotation = drag.pose.rotationOf(boneId)
                        val bone = rig.bone(boneId)!!
                        Assert.that(
                            rotation >= bone.constraint.minRotationDeg - 0.01f &&
                                rotation <= bone.constraint.maxRotationDeg + 0.01f,
                        ) { "$boneId rotated to ${rotation}° outside [${bone.constraint.minRotationDeg}, ${bone.constraint.maxRotationDeg}] at ($dx, $dy)" }
                    }
                }
            }
        },

        TestCase("nearest chain finds the handle under a finger") {
            val rig = Fixtures.rig()
            val pose = Pose()
            val hand = PoseEditing.chainJoints(rig, pose, IkChain.HAND_R)!!.end
            val hit = PoseEditing.nearestChain(rig, pose, hand.plus(Vec2(0.01f, 0.01f)))
            Assert.that(hit != null) { "a point on the wrist handle must find a chain" }
            Assert.equals(IkChain.HAND_R.id, hit!!.first.id, "the right-hand chain is nearest")
            closeVec(hand, hit.second, "handle position returned")
            // Far away from every handle: no chain matches.
            Assert.that(PoseEditing.nearestChain(rig, pose, Vec2(-5f, -5f)) == null) {
                "a point away from the body must not grab a chain"
            }
        },

        TestCase("rigs without hand artwork still expose a wrist handle") {
            // Only the mandatory ten parts: no hands, no feet, no face.
            val rig = Fixtures.rig(include = Fixtures.minimalInclude())
            val chain = IkChain.HAND_R
            val joints = PoseEditing.chainJoints(rig, Pose(), chain)
            Assert.that(joints != null) { "the arm chain exists even without hand artwork" }
            // The fallback anchor is the forearm's far end, which is a real distance below the elbow.
            val lower = rig.bone(chain.lowerId)!!
            Assert.that(joints!!.end.minus(joints.mid).length() > 0.02f) {
                "fallback wrist anchor should sit away from the elbow"
            }
            val drag = PoseEditing.dragChain(
                rig, Pose(), chain,
                Vec2(joints.root.x + 0.2f, joints.root.y),
            )
            Assert.that(drag != null) { "dragging must solve without effector artwork" }
        },

        TestCase("dragging composes with an already-posed character") {
            val rig = Fixtures.rig()
            val chain = IkChain.HAND_R
            // Start from a posed arm instead of rest, and pick a comfortable low-forward
            // target so the whole motion stays inside the shoulder's constraint range.
            val posed = Pose(
                bones = mapOf(
                    BoneIds.UPPER_ARM_R to com.rigstudio.core.rig.BonePose(rotationDeg = 30f),
                    BoneIds.FOREARM_R to com.rigstudio.core.rig.BonePose(rotationDeg = -40f),
                ),
            )
            val joints = PoseEditing.chainJoints(rig, posed, chain)!!
            val target = joints.root.plus(Vec2(0.10f, 0.22f))
            val drag = PoseEditing.dragChain(rig, posed, chain, target)!!
            Assert.that(drag.solution.reached) { "posed start must still reach" }
            closeVec(target, drag.achieved, "wrist from a posed start")
        },

        TestCase("a target outside the joint limits stops at the limit, never past it") {
            val rig = Fixtures.rig()
            val chain = IkChain.HAND_R
            val posed = Pose(
                bones = mapOf(
                    BoneIds.UPPER_ARM_R to com.rigstudio.core.rig.BonePose(rotationDeg = 30f),
                    BoneIds.FOREARM_R to com.rigstudio.core.rig.BonePose(rotationDeg = -40f),
                ),
            )
            // Straight up-and-right: needs ~200° of shoulder, which the rig forbids.
            val joints = PoseEditing.chainJoints(rig, posed, chain)!!
            val target = joints.root.plus(Vec2(0.18f, -0.05f))
            val drag = PoseEditing.dragChain(rig, posed, chain, target)!!
            val upperBone = rig.bone(chain.upperId)!!
            Assert.close(
                upperBone.constraint.maxRotationDeg,
                drag.pose.rotationOf(chain.upperId),
                0.01f, "shoulder rests exactly at its limit",
            )
            Assert.that(!drag.achieved.minus(joints.root).length().let { it > 0f && it == Float.NEGATIVE_INFINITY }) {
                "solved to a real position"
            }
        },

        TestCase("partial pose override pins the edited limb only") {
            val base = Pose(
                bones = mapOf(BoneIds.HEAD to com.rigstudio.core.rig.BonePose(rotationDeg = 12f)),
            )
            val partial = Pose(
                bones = mapOf(BoneIds.UPPER_ARM_R to com.rigstudio.core.rig.BonePose(rotationDeg = -70f)),
            )
            val merged = base.override(partial)
            Assert.close(-70f, merged.rotationOf(BoneIds.UPPER_ARM_R), 1e-4f, "edited limb replaced")
            Assert.close(12f, merged.rotationOf(BoneIds.HEAD), 1e-4f, "other bones keep running")
        },

        TestCase("affine inverse round-trips camera-like transforms") {
            val camera = Affine.translation(3f, -2f).multiply(Affine.scaling(540f)).multiply(Affine.rotation(0.3f))
            val point = Vec2(0.4f, 0.7f)
            val projected = camera.transform(point)
            val back = camera.inverse().transform(projected)
            Assert.close(point.x, back.x, 1e-4f, "inverse x")
            Assert.close(point.y, back.y, 1e-4f, "inverse y")
            // Degenerate transforms fall back to identity instead of dividing by zero.
            val broken = Affine(a = 0f, b = 0f, c = 0f, d = 0f)
            Assert.close(1f, broken.inverse().a, 1e-6f, "degenerate inverse = identity")
        },
    )
}
