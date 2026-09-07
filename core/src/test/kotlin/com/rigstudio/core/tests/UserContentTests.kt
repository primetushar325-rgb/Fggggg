package com.rigstudio.core.tests

import com.rigstudio.core.anim.Easing
import com.rigstudio.core.anim.PoseLibrary
import com.rigstudio.core.anim.PosePreset
import com.rigstudio.core.anim.PosePresetCodec
import com.rigstudio.core.anim.UserClip
import com.rigstudio.core.anim.UserClipCodec
import com.rigstudio.core.anim.UserKeyframe
import com.rigstudio.core.harness.Assert
import com.rigstudio.core.harness.TestCase
import com.rigstudio.core.model.BoneIds
import com.rigstudio.core.model.Expression
import com.rigstudio.core.model.MouthShape

/**
 * User-authored content (V6 §12, §47): keyframed custom animations, the pose library and
 * their versioned JSON documents — the atoms the pose editor works with.
 */
object UserContentTests {

    private fun waveClip(): UserClip = UserClip(
        id = "my_wave",
        name = "My Wave",
        durationSeconds = 2.0f,
        loop = true,
        keys = listOf(
            UserKeyframe(0.0f, mapOf(BoneIds.UPPER_ARM_R to 0f), expression = Expression.HAPPY, mouth = MouthShape.SMILE),
            UserKeyframe(0.5f, mapOf(BoneIds.UPPER_ARM_R to -120f), easing = Easing.EASE_OUT),
            UserKeyframe(0.75f, mapOf(BoneIds.UPPER_ARM_R to -95f, BoneIds.FOREARM_R to 20f)),
            UserKeyframe(1.0f, mapOf(BoneIds.UPPER_ARM_R to 0f), expression = Expression.NEUTRAL, mouth = MouthShape.CLOSED),
        ),
    )

    val cases: List<TestCase> = listOf(

        TestCase("user clips interpolate bones between keyframes") {
            val clip = waveClip()
            val start = clip.sample(0f)
            val mid = clip.sample(0.5f)
            Assert.close(-120f, mid.bones[BoneIds.UPPER_ARM_R]?.rotationDeg ?: 0f, 0.01f, "exact keyframe at t=0.5")
            // A segment is shaped by the easing of the keyframe that STARTS it: key0 is
            // SMOOTH, so halfway (t=0.25) sits exactly at 50% → −60°.
            val quarter = clip.sample(0.25f)
            Assert.close(-60f, quarter.bones[BoneIds.UPPER_ARM_R]?.rotationDeg ?: 999f, 0.01f, "SMOOTH midpoint at t=0.25")
            // The 0.5→0.75 segment is owned by the t=0.5 key (EASE_OUT): at its midpoint the
            // eased value is already 75% of the way from −120 to −95.
            val between = clip.sample(0.625f)
            Assert.close(-101.25f, between.bones[BoneIds.UPPER_ARM_R]?.rotationDeg ?: 999f, 0.01f, "EASE_OUT is fast-start")
            Assert.close(15f, between.bones[BoneIds.FOREARM_R]?.rotationDeg ?: 999f, 0.01f, "forearm eases in from 0")
            Assert.close(0f, start.bones[BoneIds.UPPER_ARM_R]?.rotationDeg ?: 999f, 0.01f, "starts at rest")
        },

        TestCase("user clips step the face at authored keyframes") {
            val clip = waveClip()
            Assert.equals(Expression.HAPPY, clip.sample(0.1f).expression, "happy before the 1.0 key")
            Assert.equals(MouthShape.SMILE, clip.sample(0.5f).mouth, "smile holds until the end key")
            Assert.equals(Expression.HAPPY, clip.sample(0.999f).expression, "the t=1 key applies only at t=1")
            Assert.equals(Expression.HAPPY, clip.sample(1.0f).expression, "t=1 wraps to the first key — seamless loop")
            Assert.equals(MouthShape.SMILE, clip.sample(1.0f).mouth, "mouth wraps to the first key too")
        },

        TestCase("missing bones default to zero rotation, not stale values") {
            val clip = waveClip()
            val sampled = clip.sample(0.6f)
            Assert.close(0f, sampled.bones[BoneIds.HEAD]?.rotationDeg ?: 0f, 1e-4f, "head is not authored anywhere")
            Assert.that(BoneIds.FOREARM_R in sampled.bones) { "forearm authored from t=0.75 interpolates back to 0 before it" }
        },

        TestCase("non-looping clips clamp instead of wrapping") {
            val clip = waveClip().copy(loop = false)
            Assert.close(0f, clip.sample(1.5f).bones[BoneIds.UPPER_ARM_R]?.rotationDeg ?: 999f, 0.01f, "t>1 clamps to the last key")
        },

        TestCase("mirroring swaps left/right bones and negates rotations") {
            val clip = waveClip()
            val mirrored = clip.mirrored()
            val pose = mirrored.sample(0.5f)
            // −120° on the right mirrors to +120° on the left: mirror-symmetric motion.
            Assert.close(120f, pose.bones[BoneIds.UPPER_ARM_L]?.rotationDeg ?: 0f, 0.01f, "right −120° → left +120°")
            Assert.that(BoneIds.UPPER_ARM_R !in pose.bones) {
                "the right arm must not stay authored after mirroring"
            }
        },

        TestCase("user clips convert into playable library clips") {
            val converted = waveClip().toAnimationClip()
            Assert.equals("my_wave", converted.id, "id survives")
            Assert.equals(2.0f, converted.durationSeconds, "duration survives")
            Assert.that(BoneIds.UPPER_ARM_R in converted.tracks) { "arm track converted" }
            Assert.that(converted.expressionTrack.isNotEmpty()) { "expression keys converted" }
            Assert.that(converted.mouthTrack.isNotEmpty()) { "mouth keys converted" }
            // The converted clip samples to the same pose as the user clip.
            Assert.close(
                waveClip().sample(0.5f).bones[BoneIds.UPPER_ARM_R]?.rotationDeg ?: 0f,
                converted.sample(0.5f).bones[BoneIds.UPPER_ARM_R]?.rotationDeg ?: 0f,
                0.01f, "converted clip matches user sampling at t=0.5",
            )
        },

        TestCase("user clips round-trip through versioned JSON") {
            val clips = listOf(waveClip(), waveClip().copy(id = "second", name = "Second"))
            val json = UserClipCodec.encodeJson(clips)
            Assert.that("schemaVersion" in json) { "document must carry schemaVersion" }
            val back = UserClipCodec.decodeJsonOrNull(json)
            Assert.that(back != null) { "valid document must decode" }
            Assert.equals(2, back!!.size, "clip count")
            Assert.equals("my_wave", back[0].id, "id round-trips")
            Assert.close(
                -120f,
                back[0].sample(0.5f).bones[BoneIds.UPPER_ARM_R]?.rotationDeg ?: 0f,
                0.01f, "keyframe values round-trip",
            )
            Assert.equals(Expression.HAPPY, back[0].sample(0.25f).expression, "expression round-trips")
        },

        TestCase("corrupt user-clip JSON is rejected, never crashes") {
            Assert.that(UserClipCodec.decodeJsonOrNull("{not json") == null) { "garbage → null" }
            Assert.that(UserClipCodec.decodeJsonOrNull("{}") == null) { "missing clips array → null" }
            // A clip with no readable keys is skipped gracefully; the rest of the file survives.
            val json = "{\"clips\":[{\"id\":\"x\"},{\"id\":\"ok\",\"durationSeconds\":1,\"keys\":[{\"time\":0}]}]}"
            val back = UserClipCodec.decodeJsonOrNull(json)
            Assert.that(back != null) { "a file with one bad clip still decodes" }
            Assert.equals(1, back!!.size, "only the valid clip survives")
        },

        TestCase("the pose library ships usable built-ins") {
            Assert.that(PoseLibrary.BUILT_INS.isNotEmpty()) { "built-ins exist" }
            Assert.that(PoseLibrary.BUILT_INS.map { it.id }.distinct().size == PoseLibrary.BUILT_INS.size) {
                "preset ids must be unique"
            }
            val neutral = PoseLibrary.byId("neutral")!!
            Assert.that(neutral.rotations.isEmpty()) { "neutral is the empty pose" }
            val walking = PoseLibrary.byId("walking")!!
            Assert.that(walking.rotations.isNotEmpty()) { "walking is sampled from the walk clip" }
            Assert.that(PoseLibrary.byId("does_not_exist") == null) { "unknown id → null" }
        },

        TestCase("pose presets mirror left/right") {
            val preset = PosePreset(
                id = "test",
                name = "Test",
                rotations = mapOf(
                    BoneIds.UPPER_ARM_L to com.rigstudio.core.rig.BonePose(rotationDeg = 30f),
                    BoneIds.HEAD to com.rigstudio.core.rig.BonePose(rotationDeg = 10f),
                ),
            )
            val mirrored = preset.mirrored()
            Assert.close(-30f, mirrored.rotations[BoneIds.UPPER_ARM_R]?.rotationDeg ?: 0f, 1e-4f, "left +30° → right −30°")
            Assert.close(-10f, mirrored.rotations[BoneIds.HEAD]?.rotationDeg ?: 0f, 1e-4f, "horizontal flip negates every rotation (same as Pose.mirrored)")
            // Consistency with the core's own pose mirroring.
            val coreMirrored = preset.toPose().mirrored()
            Assert.close(
                coreMirrored.bones[BoneIds.HEAD]?.rotationDeg ?: 0f,
                mirrored.rotations[BoneIds.HEAD]?.rotationDeg ?: 0f,
                1e-4f, "PosePreset.mirrored agrees with Pose.mirrored",
            )
        },

        TestCase("pose presets round-trip through versioned JSON") {
            val poses = listOf(
                PosePreset("p1", "Pose One", mapOf(BoneIds.HEAD to com.rigstudio.core.rig.BonePose(12f))),
                PosePreset("p2", "Pose Two", mapOf(BoneIds.THIGH_L to com.rigstudio.core.rig.BonePose(-35f))),
            )
            val json = PosePresetCodec.encodeJson(poses)
            Assert.that("schemaVersion" in json) { "document carries schemaVersion" }
            val back = PosePresetCodec.decodeJsonOrNull(json)!!
            Assert.equals(2, back.size, "pose count")
            Assert.close(12f, back[0].rotations[BoneIds.HEAD]?.rotationDeg ?: 0f, 1e-4f, "rotation round-trips")
            Assert.close(-35f, back[1].rotations[BoneIds.THIGH_L]?.rotationDeg ?: 0f, 1e-4f, "negative rotation round-trips")
            Assert.that(PosePresetCodec.decodeJsonOrNull("nope") == null) { "garbage → null" }
        },
    )
}
