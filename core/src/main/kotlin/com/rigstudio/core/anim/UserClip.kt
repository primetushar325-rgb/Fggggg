package com.rigstudio.core.anim

import com.rigstudio.core.json.Json
import com.rigstudio.core.json.JsonValue
import com.rigstudio.core.json.arr
import com.rigstudio.core.json.get
import com.rigstudio.core.json.obj
import com.rigstudio.core.json.str
import com.rigstudio.core.model.BoneIds
import com.rigstudio.core.model.Expression
import com.rigstudio.core.model.MouthShape
import com.rigstudio.core.rig.AccessoryState
import com.rigstudio.core.rig.BonePose
import com.rigstudio.core.rig.Pose

/**
 * One user-authored keyframe (V6 §12): the whole pose at a point in time.
 *
 * Rotations are degrees per universal bone id; the face fields are optional step switches
 * (a null leaves the previous keyframe's face running). [easing] shapes the segment that
 * starts at this keyframe — Smooth by default (V6 §11).
 */
data class UserKeyframe(
    val time: Float,
    val rotations: Map<String, Float> = emptyMap(),
    val expression: Expression? = null,
    val mouth: MouthShape? = null,
    val easing: Easing = Easing.SMOOTH,
    /** Accessory transforms at this instant (V6: props with keyframes). */
    val accessories: Map<String, AccessoryState> = emptyMap(),
)

/**
 * A user-created animation: an ordered list of [UserKeyframe]s over a duration.
 *
 * [toAnimationClip] converts it into the exact same [AnimationClip] the built-in library
 * uses, so a custom animation instantly plays in the editor, crossfades, exports to MP4 and
 * saves with the project — one pipeline, no special cases (V6 §48).
 */
data class UserClip(
    val id: String,
    val name: String,
    val durationSeconds: Float,
    val loop: Boolean = true,
    val keys: List<UserKeyframe>,
) {
    init {
        require(durationSeconds > 0f) { "UserClip '$id' needs a positive duration" }
        require(keys.isNotEmpty()) { "UserClip '$id' needs at least one keyframe" }
    }

    /** Samples the authored pose at normalised time [t] (step-interpolated face, eased bones). */
    fun sample(t: Float): Pose {
        val time = if (loop) ((t % 1f) + 1f) % 1f else t.coerceIn(0f, 1f)
        val sorted = keys.sortedBy { it.time }
        val idx = sorted.indexOfLast { it.time <= time }
        val a = sorted[maxOf(idx, 0)]
        val b = sorted.getOrNull(idx + 1) ?: (if (loop) sorted.first() else a)

        val span = if (idx + 1 < sorted.size) b.time - a.time
        else (1f - a.time + sorted.first().time).let { if (loop && it > 0f) it else 0f }
        val u = if (span <= 0f) 0f else a.easing.apply(((time - a.time) / span).coerceIn(0f, 1f))

        val bones = HashMap<String, BonePose>(a.rotations.size + b.rotations.size)
        for (boneId in a.rotations.keys + b.rotations.keys) {
            val ra = a.rotations[boneId] ?: 0f
            val rb = b.rotations[boneId] ?: ra
            bones[boneId] = BonePose(rotationDeg = ra + (rb - ra) * u)
        }
        var expression = Expression.NEUTRAL
        var mouth = MouthShape.CLOSED
        for (key in sorted) {
            if (key.time > time) break
            key.expression?.let { expression = it }
            key.mouth?.let { mouth = it }
        }

        // Props with keyframes: lerp each accessory's transform between the surrounding keys.
        val accessoryIds = (a.accessories.keys + b.accessories.keys).distinct()
        val accessoryStates = if (accessoryIds.isEmpty()) {
            emptyMap()
        } else {
            val states = HashMap<String, AccessoryState>(accessoryIds.size)
            for (id in accessoryIds) {
                val sa = a.accessories[id]
                val sb = b.accessories[id]
                states[id] = when {
                    sa == null -> AccessoryState()   // appears mid-clip: rest until authored
                    sb == null -> sa                 // holds its last authored state
                    else -> AccessoryState(
                        rotationDeg = sa.rotationDeg + (sb.rotationDeg - sa.rotationDeg) * u,
                        offsetX = sa.offsetX + (sb.offsetX - sa.offsetX) * u,
                        offsetY = sa.offsetY + (sb.offsetY - sa.offsetY) * u,
                        scale = sa.scale + (sb.scale - sa.scale) * u,
                    )
                }
            }
            states
        }
        return Pose(
            timeSeconds = time * durationSeconds,
            bones = bones,
            expression = expression,
            mouth = mouth,
            accessories = accessoryStates,
        )
    }

    /** Mirrored copy: left↔right bones swap, rotations negate (V6 §56); props flip with them. */
    fun mirrored(): UserClip = copy(
        id = id + "_mirrored",
        name = "$name (mirrored)",
        keys = keys.map { key ->
            key.copy(
                rotations = key.rotations.entries.associate { (boneId, deg) ->
                    BoneIds.mirrorOf(boneId) to -deg
                },
                accessories = key.accessories.entries.associate { (propId, state) ->
                    propId to state.copy(rotationDeg = -state.rotationDeg, offsetX = -state.offsetX)
                },
            )
        },
    )

    /** Converts into a library-grade [AnimationClip] — the bridge into the whole pipeline. */
    fun toAnimationClip(): AnimationClip {
        val sorted = keys.sortedBy { it.time }
        val boneIds = sorted.flatMap { it.rotations.keys }.distinct()
        val tracks = HashMap<String, BoneTrack>(boneIds.size)
        for (boneId in boneIds) {
            val authored = sorted.mapNotNull { key ->
                key.rotations[boneId]?.let { deg -> AnimationKeyframe(key.time, deg, easing = key.easing) }
            }
            if (authored.isNotEmpty()) {
                tracks[boneId] = BoneTrack(boneId, authored)
            }
        }
        val expressions = sorted.mapNotNull { key ->
            key.expression?.let { ExpressionKeyframe(key.time, it) }
        }
        val mouths = sorted.mapNotNull { key -> key.mouth?.let { MouthKeyframe(key.time, it) } }
        val accessoryIds = sorted.flatMap { it.accessories.keys }.distinct()
        val accessoryTracks = HashMap<String, List<AccessoryKeyframe>>(accessoryIds.size)
        for (propId in accessoryIds) {
            accessoryTracks[propId] = sorted.mapNotNull { key ->
                key.accessories[propId]?.let { state ->
                    AccessoryKeyframe(
                        time = key.time,
                        rotationDeg = state.rotationDeg,
                        offsetX = state.offsetX,
                        offsetY = state.offsetY,
                        scale = state.scale,
                        easing = key.easing,
                    )
                }
            }
        }
        return AnimationClip(
            id = id,
            name = name,
            durationSeconds = durationSeconds,
            loop = loop,
            tracks = tracks,
            expressionTrack = expressions,
            mouthTrack = mouths,
            accessoryTracks = accessoryTracks,
            category = ClipCategory.ACTION,
            description = "Custom animation authored in the pose editor.",
        )
    }
}

/** Versioned JSON codec for user animations (V6 §39: schemaVersion on every document). */
object UserClipCodec {

    const val SCHEMA_VERSION = 6

    fun encode(clips: List<UserClip>): JsonValue.Obj = obj(
        "schemaVersion" to JsonValue.Num(SCHEMA_VERSION.toDouble()),
        "clips" to arr(clips.map(::encodeClip)),
    )

    fun encodeJson(clips: List<UserClip>): String = Json.stringify(encode(clips))

    fun decodeJsonOrNull(json: String): List<UserClip>? {
        val root = Json.parseOrNull(json) as? JsonValue.Obj ?: return null
        val clips = root.get("clips") as? JsonValue.Arr ?: return null
        return clips.items.mapNotNull(::decodeClip)
    }

    private fun encodeClip(clip: UserClip): JsonValue.Obj = obj(
        "id" to str(clip.id),
        "name" to str(clip.name),
        "durationSeconds" to JsonValue.Num(clip.durationSeconds.toDouble()),
        "loop" to JsonValue.Bool(clip.loop),
        "keys" to arr(clip.keys.map { key ->
            obj(
                "time" to JsonValue.Num(key.time.toDouble()),
                "rotations" to JsonValue.Obj(key.rotations.entries.associate { (bone, deg) ->
                    bone to JsonValue.Num(deg.toDouble())
                }),
                "expression" to key.expression?.let { str(it.name) },
                "mouth" to key.mouth?.let { str(it.name) },
                "easing" to str(key.easing.name),
                "accessories" to JsonValue.Obj(key.accessories.entries.associate { (id, st) ->
                    id to obj(
                        "rotationDeg" to JsonValue.Num(st.rotationDeg.toDouble()),
                        "offsetX" to JsonValue.Num(st.offsetX.toDouble()),
                        "offsetY" to JsonValue.Num(st.offsetY.toDouble()),
                        "scale" to JsonValue.Num(st.scale.toDouble()),
                    )
                }),
            )
        }),
    )

    private fun decodeClip(value: JsonValue): UserClip? {
        if (value !is JsonValue.Obj) return null
        val id = (value.get("id") as? JsonValue.Str)?.value ?: return null
        val name = (value.get("name") as? JsonValue.Str)?.value ?: id
        val duration = (value.get("durationSeconds") as? JsonValue.Num)?.value?.toFloat() ?: return null
        val loop = (value.get("loop") as? JsonValue.Bool)?.value ?: true
        val keysArr = value.get("keys") as? JsonValue.Arr ?: return null
        val keys = keysArr.items.mapNotNull { kv ->
            if (kv !is JsonValue.Obj) return@mapNotNull null
            val time = (kv.get("time") as? JsonValue.Num)?.value?.toFloat() ?: return@mapNotNull null
            val rotations = HashMap<String, Float>()
            ((kv.get("rotations") as? JsonValue.Obj)?.members ?: emptyMap()).forEach { (bone, v) ->
                (v as? JsonValue.Num)?.let { rotations[bone] = it.value.toFloat() }
            }
            val accessoryStates = HashMap<String, AccessoryState>()
            ((kv.get("accessories") as? JsonValue.Obj)?.members ?: emptyMap()).forEach { (id, v) ->
                if (v is JsonValue.Obj) {
                    accessoryStates[id] = AccessoryState(
                        rotationDeg = (v.get("rotationDeg") as? JsonValue.Num)?.value?.toFloat() ?: 0f,
                        offsetX = (v.get("offsetX") as? JsonValue.Num)?.value?.toFloat() ?: 0f,
                        offsetY = (v.get("offsetY") as? JsonValue.Num)?.value?.toFloat() ?: 0f,
                        scale = (v.get("scale") as? JsonValue.Num)?.value?.toFloat() ?: 1f,
                    )
                }
            }
            UserKeyframe(
                time = time,
                rotations = rotations,
                expression = (kv.get("expression") as? JsonValue.Str)?.value
                    ?.let { name0 -> Expression.entries.firstOrNull { it.name == name0 } },
                mouth = (kv.get("mouth") as? JsonValue.Str)?.value
                    ?.let { name0 -> MouthShape.entries.firstOrNull { it.name == name0 } },
                easing = (kv.get("easing") as? JsonValue.Str)?.value
                    ?.let { name0 -> Easing.entries.firstOrNull { it.name == name0 } } ?: Easing.SMOOTH,
                accessories = accessoryStates,
            )
        }
        if (keys.isEmpty()) return null
        return UserClip(id, name, duration, loop, keys)
    }
}
