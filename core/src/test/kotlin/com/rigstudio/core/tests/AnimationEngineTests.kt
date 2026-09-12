package com.rigstudio.core.tests

import com.rigstudio.core.anim.AnimationEngine
import com.rigstudio.core.anim.AnimationLibrary
import com.rigstudio.core.anim.BlinkScheduler
import com.rigstudio.core.anim.EvaluateOptions
import com.rigstudio.core.harness.Assert
import com.rigstudio.core.harness.TestCase
import com.rigstudio.core.model.BoneIds
import com.rigstudio.core.model.Expression
import com.rigstudio.core.rig.Pose

/**
 * The V5 animation engine: layered evaluation (secondary delay, breathing, procedural blink),
 * pose-space crossfade blending, deterministic blink scheduling and the foot-flat contact
 * invariant of the rebuilt locomotion clips.
 */
object AnimationEngineTests {

    private const val FOOT_FLAT_TOLERANCE_DEG = 8f

    val cases: List<TestCase> = listOf(

        TestCase("blend endpoints return the source poses untouched") {
            val a = AnimationLibrary.TALK.sample(0.3f)
            val b = AnimationLibrary.WAVE.sample(0.4f)
            Assert.equals(a, AnimationEngine.blend(a, b, 0f))
            Assert.equals(b, AnimationEngine.blend(a, b, 1f))
        },

        TestCase("blend midpoint averages every bone over the union") {
            val a = AnimationLibrary.WALK.sample(0.25f)
            val b = AnimationLibrary.RUN.sample(0.25f)
            val mid = AnimationEngine.blend(a, b, 0.5f)
            for (boneId in a.bones.keys + b.bones.keys) {
                val expected = (a.poseOf(boneId).rotationDeg + b.poseOf(boneId).rotationDeg) * 0.5f
                Assert.close(expected, mid.poseOf(boneId).rotationDeg, 1e-3f, "$boneId midpoint")
            }
        },

        TestCase("blend switches discrete face selections at the halfway point") {
            val a = AnimationLibrary.IDLE.sample(0.5f).copy(expression = Expression.NEUTRAL)
            val b = AnimationLibrary.WAVE.sample(0.5f).copy(expression = Expression.HAPPY)
            Assert.equals(Expression.NEUTRAL, AnimationEngine.blend(a, b, 0.49f).expression)
            Assert.equals(Expression.HAPPY, AnimationEngine.blend(a, b, 0.51f).expression)
        },

        TestCase("blink schedule is deterministic, short, and never metronome-even") {
            val start0 = BlinkScheduler.blinkStart(0)
            val start1 = BlinkScheduler.blinkStart(1)
            val start2 = BlinkScheduler.blinkStart(2)
            Assert.that(start0 >= BlinkScheduler.FIRST_BLINK_DELAY_SECONDS) { "first blink too early" }
            Assert.that(BlinkScheduler.isBlinking(start0 + 0.05f)) { "should be mid-blink just after start" }
            Assert.that(!BlinkScheduler.isBlinking(start0 + BlinkScheduler.BLINK_DURATION_SECONDS + 0.05f)) {
                "blink must be over ${BlinkScheduler.BLINK_DURATION_SECONDS}s after start"
            }
            Assert.that(BlinkScheduler.BLINK_DURATION_SECONDS in 0.10f..0.18f) { "V5 §20 blink length" }
            val gap1 = start1 - start0
            val gap2 = start2 - start1
            Assert.that(gap1 != gap2) { "consecutive gaps must vary (V5 §20)" }
            Assert.inRange(gap1, BlinkScheduler.MIN_GAP_SECONDS - 0.01f, BlinkScheduler.MAX_GAP_SECONDS + 0.01f, "gap bounds")
            // Determinism: identical inputs, identical answers.
            for (t in listOf(0.5f, 1.3f, 3.77f, 12.4f)) {
                Assert.equals(BlinkScheduler.isBlinking(t), BlinkScheduler.isBlinking(t), "t=$t")
            }
            Assert.that(!BlinkScheduler.isBlinking(0f)) { "clip start is never mid-blink" }
        },

        TestCase("evaluate layers a blink on a neutral expression only") {
            val blinkAt = BlinkScheduler.blinkStart(0) + 0.05f
            val t = (blinkAt / AnimationLibrary.IDLE.durationSeconds).coerceIn(0f, 0.999f)
            val posed = AnimationEngine.evaluate(
                AnimationLibrary.IDLE, t,
                EvaluateOptions(breathing = false, blink = true, headLagSeconds = 0f, handLagSeconds = 0f),
            )
            Assert.equals(Expression.CLOSED, posed.expression)

            // WAVE pins HAPPY eyes for most of its length — a blink must not stomp that.
            val waveT = 0.5f
            val wavePose = AnimationEngine.evaluate(
                AnimationLibrary.WAVE, waveT,
                EvaluateOptions(breathing = false, blink = true, headLagSeconds = 0f, handLagSeconds = 0f),
            )
            Assert.equals(Expression.HAPPY, wavePose.expression, "blink never overrides a pinned expression")
        },

        TestCase("evaluate adds quiet breathing to idle-class clips only") {
            val still = EvaluateOptions(breathing = false, blink = false, headLagSeconds = 0f, handLagSeconds = 0f)
            val breathing = EvaluateOptions(breathing = true, blink = false, headLagSeconds = 0f, handLagSeconds = 0f)

            var idleDiffers = false
            for (step in 0 until 60) {
                val t = step / 60f
                val a = AnimationEngine.evaluate(AnimationLibrary.IDLE, t, still).poseOf(BoneIds.TORSO)
                val b = AnimationEngine.evaluate(AnimationLibrary.IDLE, t, breathing).poseOf(BoneIds.TORSO)
                if (kotlin.math.abs(a.rotationDeg - b.rotationDeg) > 1e-4f) idleDiffers = true
            }
            Assert.that(idleDiffers) { "idle torso should breathe" }

            // Locomotion must stay exactly as authored (V5 §40: layers only add what they own).
            for (step in 0 until 30) {
                val t = step / 30f
                val a = AnimationEngine.evaluate(AnimationLibrary.WALK, t, still).poseOf(BoneIds.TORSO)
                val b = AnimationEngine.evaluate(AnimationLibrary.WALK, t, breathing).poseOf(BoneIds.TORSO)
                Assert.close(a.rotationDeg, b.rotationDeg, 1e-4f, "walk torso must not breathe")
            }
        },

        TestCase("evaluate delays the head channel a few tens of milliseconds") {
            val noLag = EvaluateOptions(breathing = false, blink = false, headLagSeconds = 0f, handLagSeconds = 0f)
            val lag = EvaluateOptions(breathing = false, blink = false, headLagSeconds = 0.07f, handLagSeconds = 0f)
            var maxDelta = 0f
            var differs = false
            for (step in 0 until 100) {
                val t = step / 100f
                val a = AnimationEngine.evaluate(AnimationLibrary.TALK, t, noLag).rotationOf(BoneIds.HEAD)
                val b = AnimationEngine.evaluate(AnimationLibrary.TALK, t, lag).rotationOf(BoneIds.HEAD)
                val delta = kotlin.math.abs(a - b)
                if (delta > 1e-3f) differs = true
                if (delta > maxDelta) maxDelta = delta
            }
            Assert.that(differs) { "head lag must visibly shift the head channel" }
            Assert.that(maxDelta <= 15f) { "head lag is stabilisation, not re-animation (max $maxDelta)" }
        },

        TestCase("planted feet stay flat on the ground through every contact window") {
            for ((clipId, windows) in AnimationLibrary.FOOT_CONTACT_WINDOWS) {
                val clip = AnimationLibrary.byId(clipId)
                Assert.that(clip != null) { "$clipId missing from the library" }
                for (window in windows) {
                    var steps = 0
                    var t = window.from
                    while (t <= window.to + 1e-4f) {
                        val pose = clip!!.sample(t)
                        val residual = AnimationEngine.footFlatResidual(pose, window.thighId, window.shinId, window.footId)
                        Assert.that(residual <= FOOT_FLAT_TOLERANCE_DEG) {
                            "$clipId ${window.footId} not flat at t=$t: ${residual}deg"
                        }
                        steps++
                        t = window.from + steps * 0.02f
                    }
                    Assert.that(steps >= 5) { "$clipId window ${window.footId} too small to matter" }
                }
            }
        },

        TestCase("every looping clip starts and ends on the same pose (seamless loop)") {
            for (clip in AnimationLibrary.ALL.filter { it.loop }) {
                val start = clip.sample(0f)
                val end = clip.sample(1f)
                for (boneId in start.bones.keys + end.bones.keys) {
                    Assert.close(
                        start.poseOf(boneId).rotationDeg, end.poseOf(boneId).rotationDeg, 1e-3f,
                        "${clip.id}/$boneId loop seam",
                    )
                }
                Assert.close(start.root.rotationDeg, end.root.rotationDeg, 1e-3f, "${clip.id}/root loop seam")
            }
        },

        TestCase("blend honours V5 §41 defaults and stays pose-continuous in alpha") {
            Assert.that(AnimationEngine.DEFAULT_BLEND_SECONDS in 0.15f..0.30f) { "crossfade window" }
            val a = Pose(root = com.rigstudio.core.rig.BonePose(rotationDeg = 0f))
            val b = Pose(root = com.rigstudio.core.rig.BonePose(rotationDeg = 40f))
            for (step in 0..10) {
                val alpha = step / 10f
                val expected = 40f * alpha
                Assert.close(expected, AnimationEngine.blend(a, b, alpha).root.rotationDeg, 1e-3f)
            }
        },
    )
}
