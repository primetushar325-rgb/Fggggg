package com.rigstudio.core.tests

import com.rigstudio.core.geom.Affine
import com.rigstudio.core.geom.FloatRect
import com.rigstudio.core.geom.Vec2
import com.rigstudio.core.harness.Assert
import com.rigstudio.core.harness.TestCase
import com.rigstudio.core.model.BoneIds
import com.rigstudio.core.render.PuppetComposer
import com.rigstudio.core.rig.AccessoryCodec
import com.rigstudio.core.rig.AccessoryDef
import com.rigstudio.core.rig.BonePose
import com.rigstudio.core.rig.Pose
import com.rigstudio.core.support.Fixtures
import kotlin.math.abs

/**
 * Props & accessories (V6): attachments inherit the host bone's animated transform, the draw
 * list layers them with the body, and the codec round-trips the user's collection.
 */
object AccessoryTests {

    private fun hat() = AccessoryDef(
        id = "acc_hat",
        name = "Hat",
        fileName = "accessories/acc_hat.png",
        widthPx = 128,
        heightPx = 96,
        pivotX = 0.5f,
        pivotY = 1f,          // the hat's brim sits on the anchor
        attachBoneId = BoneIds.HEAD,
        anchorX = 0.5f,
        anchorY = 0f,         // top-centre of the head
        targetHeight = 0.18f,
        z = 80,
    )

    private fun assertClose(expected: Float, actual: Float, label: String, tol: Float = 0.01f) {
        Assert.that(abs(expected - actual) <= tol) { "$label: expected ~$expected got $actual" }
    }

    val cases: List<TestCase> = listOf(

        TestCase("an accessory attaches to a bone and follows its rotation") {
            val rig = Fixtures.rig()
            val rest = PuppetComposer.compose(rig, Pose(), accessories = listOf(hat()))
            val hatRest = rest.first { it.slotId == "acc_hat" }
            val restCentre = hatRest.centre

            val posed = PuppetComposer.compose(
                rig,
                Pose(bones = mapOf(BoneIds.HEAD to BonePose(rotationDeg = 30f))),
                accessories = listOf(hat()),
            )
            val hatPosed = posed.first { it.slotId == "acc_hat" }
            val posedCentre = hatPosed.centre

            Assert.that(restCentre != posedCentre) { "the hat must move when the head rotates" }
            // The hat's own world transform = head world · local, so its rotation changes by the
            // head's rotation (30°) exactly — it rides the head, it does not slide.
            val restAngle = angleOf(hatRest.world)
            val posedAngle = angleOf(hatPosed.world)
            assertClose(restAngle + 30f, posedAngle, "hat rotation follows head rotation", 0.5f)
        },

        TestCase("the accessory rect is placed by its pivot at the anchor") {
            val rig = Fixtures.rig()
            val draws = PuppetComposer.compose(rig, Pose(), accessories = listOf(hat()))
            val draw = draws.first { it.slotId == "acc_hat" }
            // Pivot (0.5, 1.0) + anchor (0.5, 0.0): the rect's bottom-centre sits on the head's
            // top-centre — a hat resting on the crown.
            val head = draws.first { it.slotId == "front_head" }
            val headTopCentre = Vec2(head.restRect.centerX, head.restRect.top)
            // Evaluate in head rest space: the anchor transformed by the hat's world.
            val anchorOnHead = draw.world.transform(headTopCentre)
            assertClose(headTopCentre.x, anchorOnHead.x, "anchor x maps to itself at rest", 0.01f)
            assertClose(headTopCentre.y, anchorOnHead.y, "anchor y maps to itself at rest", 0.01f)
            // Rect geometry: bottom edge at the anchor, width = aspect * targetHeight.
            assertClose(0.18f, draw.restRect.height, "rect height = targetHeight")
            assertClose(0.18f * 128f / 96f, draw.restRect.width, "rect width keeps the art aspect", 0.01f)
        },

        TestCase("accessories layer with the body by z") {
            val rig = Fixtures.rig()
            val inFront = PuppetComposer.compose(rig, Pose(), accessories = listOf(hat().copy(z = 90)))
            val behind = PuppetComposer.compose(rig, Pose(), accessories = listOf(hat().copy(z = 1)))
            Assert.that(inFront.last() == inFront.first { it.slotId == "acc_hat" } || inFront.last().z >= 90) {
                "a z=90 accessory must draw after every body part"
            }
            Assert.that(
                behind.indexOfFirst { it.slotId == "acc_hat" } <
                    behind.indexOfFirst { it.slotId != "acc_hat" },
            ) { "a z=1 accessory must draw before the body parts" }
        },

        TestCase("offset and rotation adjust the accessory without breaking the attachment") {
            val rig = Fixtures.rig()
            val adjusted = hat().copy(rotationDeg = 20f, offsetX = 0.05f, scale = 1.5f)
            val draws = PuppetComposer.compose(rig, Pose(), accessories = listOf(adjusted))
            val draw = draws.first { it.slotId == "acc_hat" }
            assertClose(20f, angleOf(draw.world), "user rotation applied", 0.5f)
            assertClose(1.5f, draw.world.scaleMagnitude(), "user scale applied to the world transform", 0.01f)
            assertClose(0.18f, draw.restRect.height, "rest rect stays at target height (bones scale the same way)")
            // Head rotation still rides through: rotate the head and the accessory follows.
            val posed = PuppetComposer.compose(
                rig,
                Pose(bones = mapOf(BoneIds.HEAD to BonePose(rotationDeg = 45f))),
                accessories = listOf(adjusted),
            ).first { it.slotId == "acc_hat" }
            assertClose(65f, angleOf(posed.world), "head 45° + own 20° = 65°", 0.5f)
        },

        TestCase("accessories attach to hands and follow IK drags") {
            val rig = Fixtures.rig()
            val sword = hat().copy(
                id = "acc_sword",
                attachBoneId = BoneIds.HAND_R,
                anchorX = 0.5f,
                anchorY = 0.5f,
                pivotX = 0.5f,
                pivotY = 0.5f,
            )
            val rest = PuppetComposer.compose(rig, Pose(), accessories = listOf(sword))
                .first { it.slotId == "acc_sword" }
            val swung = PuppetComposer.compose(
                rig,
                Pose(
                    bones = mapOf(
                        BoneIds.UPPER_ARM_R to BonePose(rotationDeg = -60f),
                        BoneIds.FOREARM_R to BonePose(rotationDeg = -30f),
                    ),
                ),
                accessories = listOf(sword),
            ).first { it.slotId == "acc_sword" }
            Assert.that(rest.centre != swung.centre) { "the sword moves with the arm chain" }
            assertClose(-90f, angleOf(swung.world), "sword inherits the arm rotations", 0.5f)
        },

        TestCase("z-order overrides also move accessories") {
            val rig = Fixtures.rig()
            val draws = PuppetComposer.compose(rig, Pose(), accessories = listOf(hat()))
            val overridden = PuppetComposer.applyZOverrides(draws, mapOf("acc_hat" to 1))
            Assert.equals(1, overridden.first { it.slotId == "acc_hat" }.z, "accessory z overridden")
        },

        TestCase("pose accessory state overrides the definition's transform") {
            val rig = Fixtures.rig()
            val state = com.rigstudio.core.rig.AccessoryState(rotationDeg = 33f, offsetX = 0.04f, scale = 1.2f)
            val draws = PuppetComposer.compose(
                rig,
                Pose(accessories = mapOf("acc_hat" to state)),
                accessories = listOf(hat().copy(rotationDeg = 0f)),
            )
            val draw = draws.first { it.slotId == "acc_hat" }
            assertClose(33f, angleOf(draw.world), "animated rotation wins over the definition", 0.5f)
            assertClose(1.2f, draw.world.scaleMagnitude(), "animated scale wins", 0.01f)
            // Without a pose state the definition's own transform applies.
            val plain = PuppetComposer.compose(rig, Pose(), accessories = listOf(hat().copy(rotationDeg = 10f)))
                .first { it.slotId == "acc_hat" }
            assertClose(10f, angleOf(plain.world), "definition rotation without animation", 0.5f)
        },

        TestCase("keyframed accessories sample through the clip pipeline") {
            val clip = com.rigstudio.core.anim.AnimationClip(
                id = "prop_test",
                name = "Prop test",
                durationSeconds = 2f,
                loop = true,
                tracks = mapOf(
                    BoneIds.HEAD to com.rigstudio.core.anim.BoneTrack(
                        boneId = BoneIds.HEAD,
                        keys = listOf(
                            com.rigstudio.core.anim.AnimationKeyframe(0f, 0f),
                            com.rigstudio.core.anim.AnimationKeyframe(1f, 0f),
                        ),
                    ),
                ),
                accessoryTracks = mapOf(
                    "acc_hat" to listOf(
                        com.rigstudio.core.anim.AccessoryKeyframe(0.0f, rotationDeg = 0f),
                        com.rigstudio.core.anim.AccessoryKeyframe(0.5f, rotationDeg = 90f),
                        com.rigstudio.core.anim.AccessoryKeyframe(1.0f, rotationDeg = 0f),
                    ),
                ),
            )
            val at0 = clip.sample(0f).accessories["acc_hat"]!!
            val mid = clip.sample(0.25f).accessories["acc_hat"]!!
            val atHalf = clip.sample(0.5f).accessories["acc_hat"]!!
            assertClose(0f, at0.rotationDeg, "starts at rest")
            assertClose(90f, atHalf.rotationDeg, "exact keyframe at t=0.5")
            assertClose(45f, mid.rotationDeg, "SMOOTH midpoint at t=0.25", 0.01f)
            // The hat actually draws at the animated angle.
            val rig = Fixtures.rig()
            val drawn = PuppetComposer.compose(rig, clip.sample(0.5f), accessories = listOf(hat()))
                .first { it.slotId == "acc_hat" }
            assertClose(90f, angleOf(drawn.world), "composed world rotation follows the track", 0.5f)
        },

        TestCase("accessories round-trip through versioned JSON") {
            val list = listOf(
                hat(),
                hat().copy(id = "acc_cape", name = "Cape", attachBoneId = BoneIds.TORSO, z = 2),
            )
            val json = AccessoryCodec.encodeJson(list)
            Assert.that("schemaVersion" in json) { "document carries schemaVersion" }
            val back = AccessoryCodec.decodeJsonOrNull(json)!!
            Assert.equals(2, back.size, "count round-trips")
            Assert.equals("acc_hat", back[0].id)
            Assert.equals(BoneIds.HEAD, back[0].attachBoneId, "attach bone round-trips")
            assertClose(0.18f, back[0].targetHeight, "target height round-trips")
            Assert.equals(2, back[1].z, "z round-trips")
        },

        TestCase("corrupt accessory files degrade instead of crashing") {
            Assert.that(AccessoryCodec.decodeJsonOrNull("{bad") == null) { "garbage → null" }
            Assert.that(AccessoryCodec.decodeJsonOrNull("{}") == null) { "no list → null" }
            val mixed = AccessoryCodec.encodeJson(listOf(hat()))
                .replace("\"widthPx\": 128", "\"widthPx\": 0")
            Assert.that(AccessoryCodec.decodeJsonOrNull(mixed) != null) { "one broken entry is skipped" }
            Assert.equals(0, AccessoryCodec.decodeJsonOrNull(mixed)!!.size, "the broken entry is gone")
        },
    )

    /** Rotation of an affine in degrees (atan2 of the linear part). */
    private fun angleOf(t: Affine): Float =
        Math.toDegrees(kotlin.math.atan2(t.b.toDouble(), t.a.toDouble())).toFloat()
}
