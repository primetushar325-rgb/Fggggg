package com.rigstudio.core.anim

import com.rigstudio.core.geom.Vec2
import com.rigstudio.core.model.BoneIds
import com.rigstudio.core.model.Expression
import com.rigstudio.core.rig.BonePose
import com.rigstudio.core.rig.Pose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Deterministic, seeded blink scheduling (V5 §20).
 *
 * Blinks last ~130 ms and the gaps between them **vary** cycle to cycle (never a metronome),
 * but the schedule is a pure function of absolute time: preview and a 60 fps export sample the
 * same instants and therefore blink identically. No randomness is drawn per frame (V5 §54).
 */
object BlinkScheduler {

    /** One blink lasts 100–180 ms; 130 ms reads as a natural blink. */
    const val BLINK_DURATION_SECONDS = 0.13f

    /** Shortest and longest gap between blink starts, in seconds. */
    const val MIN_GAP_SECONDS = 2.2f
    const val MAX_GAP_SECONDS = 5.4f

    /** Base gap every cycle starts from before per-cycle variation. */
    private const val BASE_GAP_SECONDS = 2.8f

    /** Variation span: hash decides how much of this gets added. */
    private const val VARIATION_SECONDS = 2.6f

    /**
     * Stable pseudo-random 0..1 for an integer cycle index (xorshift-style bit mix). Using a
     * hash instead of a Random instance is what keeps the schedule reproducible everywhere.
     */
    fun unitHash(index: Int): Float {
        var h = (index * 0x9E3779B1.toInt()) xor 0x85EBCA6B.toInt()
        h = h xor (h ushr 13)
        h *= 0xC2B2AE35.toInt()
        h = h xor (h ushr 16)
        return ((h and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF.toFloat()).coerceIn(0f, 1f)
    }

    /** Start time (seconds) of blink cycle [index], i.e. the cumulative sum of the gaps. */
    fun blinkStart(index: Int): Float {
        var t = FIRST_BLINK_DELAY_SECONDS
        for (i in 0 until index) {
            t += gapOf(i)
        }
        return t
    }

    private fun gapOf(index: Int): Float =
        BASE_GAP_SECONDS + VARIATION_SECONDS * unitHash(index)

    /** The schedule begins shortly after zero so a clip start is not always mid-blink. */
    const val FIRST_BLINK_DELAY_SECONDS = 0.9f

    /** True when [seconds] falls inside a blink window. Deterministic and cheap. */
    fun isBlinking(seconds: Float): Boolean {
        if (seconds < FIRST_BLINK_DELAY_SECONDS) return false
        var index = 0
        var guarded = 0
        while (guarded++ < MAX_CYCLES) {
            val start = blinkStart(index)
            if (seconds < start) return false
            if (seconds < start + BLINK_DURATION_SECONDS) return true
            index++
        }
        return false
    }

    private const val MAX_CYCLES = 10_000
}

/** Per-call switches for [AnimationEngine.evaluate]. */
data class EvaluateOptions(
    /** Additive quiet-breathing layer on non-locomotion clips (V5 §64). */
    val breathing: Boolean = true,
    /** Procedural eye blinks when the base expression allows it (V5 §20). */
    val blink: Boolean = true,
    /** Head secondary stabilisation: the head channel is sampled slightly in the past (V5 §13). */
    val headLagSeconds: Float = DEFAULT_HEAD_LAG_SECONDS,
    /** Hands follow the forearm with a smaller delay (overlap, V5 §53). */
    val handLagSeconds: Float = DEFAULT_HAND_LAG_SECONDS,
) {
    companion object {
        const val DEFAULT_HEAD_LAG_SECONDS = 0.07f
        const val DEFAULT_HAND_LAG_SECONDS = 0.05f
    }
}

/**
 * The V5 layering / blending engine.
 *
 * Every stage in the app renders through [evaluate] — preview, thumbnails and MP4 export —
 * so layered motion is identical everywhere (V5 §57). The engine is pure: same clip, same time,
 * same options ⇒ same pose, with zero allocations beyond the result map.
 *
 * Layers, in rising priority order (V5 §39/§40):
 *  1. **Base clip** — the authored keyframes (priority 10 equivalent).
 *  2. **Secondary delay** — head and hand channels re-sampled a few tens of milliseconds in
 *     the past, which turns rigid, lockstep motion into delayed follow-through.
 *  3. **Breathing** — a quiet additive sinusoid on the torso for idle-class clips.
 *  4. **Blink** — the highest-priority facial layer; it only ever touches the eye selection,
 *     never bone transforms, and never overrides a non-neutral expression or Sleep's closed eyes.
 */
object AnimationEngine {

    /** Default crossfade length for clip-to-clip transitions (V5 §41: 150–300 ms). */
    const val DEFAULT_BLEND_SECONDS = 0.22f

    /**
     * Crossfades two poses. Rotations, offsets and scale lerp per bone over the union of bone
     * ids; discrete selections (expression, mouth) switch at the halfway point. Higher-blend
     * weights never re-author transforms that [b] does not mention: bones only present in [a]
     * fade toward their rest pose through [Pose.poseOf]'s REST default.
     */
    fun blend(a: Pose, b: Pose, alpha: Float): Pose {
        val w = alpha.coerceIn(0f, 1f)
        if (w <= 0f) return a
        if (w >= 1f) return b

        val bones = HashMap<String, BonePose>((a.bones.size + b.bones.size) * 2)
        for (boneId in a.bones.keys + b.bones.keys) {
            val pa = a.poseOf(boneId)
            val pb = b.poseOf(boneId)
            bones[boneId] = BonePose(
                rotationDeg = pa.rotationDeg + (pb.rotationDeg - pa.rotationDeg) * w,
                offset = Vec2(
                    pa.offset.x + (pb.offset.x - pa.offset.x) * w,
                    pa.offset.y + (pb.offset.y - pa.offset.y) * w,
                ),
                scale = pa.scale + (pb.scale - pa.scale) * w,
            )
        }
        return Pose(
            timeSeconds = b.timeSeconds,
            root = blendBone(a.root, b.root, w),
            bones = bones,
            expression = if (w < 0.5f) a.expression else b.expression,
            mouth = if (w < 0.5f) a.mouth else b.mouth,
        )
    }

    private fun blendBone(a: BonePose, b: BonePose, w: Float) = BonePose(
        rotationDeg = a.rotationDeg + (b.rotationDeg - a.rotationDeg) * w,
        offset = Vec2(
            a.offset.x + (b.offset.x - a.offset.x) * w,
            a.offset.y + (b.offset.y - a.offset.y) * w,
        ),
        scale = a.scale + (b.scale - a.scale) * w,
    )

    /**
     * Samples [clip] at normalised time [t] with the V5 procedural layers applied.
     *
     * Lag options are expressed in **wall seconds**; divide by the playback speed before
     * calling when the user's speed slider is not 1×, so lag scales with playback like the
     * clip data does.
     */
    fun evaluate(
        clip: AnimationClip,
        t: Float,
        options: EvaluateOptions = EvaluateOptions(),
    ): Pose {
        val base = clip.sample(t)
        var pose = base

        // --- layer 2: delayed follow for head and hands -------------------------------------
        if (clip.tracks.containsKey(BoneIds.HEAD) && options.headLagSeconds > 0f) {
            pose = pose.copy(bones = pose.bones + delayedChannel(clip, t, BoneIds.HEAD, options.headLagSeconds))
        }
        for (handId in listOf(BoneIds.HAND_L, BoneIds.HAND_R)) {
            if (clip.tracks.containsKey(handId) && options.handLagSeconds > 0f) {
                pose = pose.copy(bones = pose.bones + delayedChannel(clip, t, handId, options.handLagSeconds))
            }
        }

        // --- layer 3: quiet breathing on idle-class clips ------------------------------------
        if (options.breathing && clip.category != ClipCategory.LOCOMOTION) {
            val phase = base.timeSeconds
            val breath = sin(phase * 2f * PI.toFloat() / BREATH_PERIOD_SECONDS)
            val torso = pose.poseOf(BoneIds.TORSO)
            pose = pose.copy(
                bones = pose.bones + (BoneIds.TORSO to torso.copy(
                    rotationDeg = torso.rotationDeg + 0.45f * breath,
                )),
                root = pose.root.copy(
                    offset = Vec2(pose.root.offset.x, pose.root.offset.y + 0.0022f * breath),
                ),
            )
        }

        // --- layer 4: procedural blink --------------------------------------------------------
        if (options.blink && pose.expression == Expression.NEUTRAL) {
            if (BlinkScheduler.isBlinking(base.timeSeconds)) {
                pose = pose.copy(expression = Expression.CLOSED)
            }
        }

        return pose
    }

    /** Samples one bone channel at a delayed time, wrapped like the clip loops. */
    private fun delayedChannel(
        clip: AnimationClip,
        t: Float,
        boneId: String,
        lagSeconds: Float,
    ): Pair<String, BonePose> {
        val duration = clip.durationSeconds
        val delay = if (duration <= 0f) 0f else (lagSeconds / duration).coerceAtMost(MAX_LAG_FRACTION)
        val delayedT = if (clip.loop) wrap01(t - delay) else (t - delay).coerceIn(0f, 1f)
        val delayedPose = clip.sample(delayedT).poseOf(boneId)
        return boneId to delayedPose
    }

    private fun wrap01(t: Float): Float {
        val wrapped = t % 1f
        return if (wrapped < 0f) wrapped + 1f else wrapped
    }

    /** Breathing completes one full inhale/exhale every ~3.4 s (V5 §7: subtle, slow). */
    const val BREATH_PERIOD_SECONDS = 3.4f

    /** Lag is capped at this fraction of the clip so short clips stay recognisable. */
    const val MAX_LAG_FRACTION = 0.12f

    /**
     * Foot-flat residual of a planted leg: how far the foot's world rotation is from flat
     * (0° = perfectly planted). In a 2D cutout chain the foot's world rotation is
     * thigh + shin + foot, so a planted foot needs `foot ≈ -(thigh + shin)`. Animators use this
     * constant with [footFlatResidual] when authoring contact windows (V5 §14).
     */
    fun footFlatResidual(pose: Pose, thighId: String, shinId: String, footId: String): Float {
        val world = pose.rotationOf(thighId) + pose.rotationOf(shinId) + pose.rotationOf(footId)
        return abs(world)
    }
}
