@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName", "FunctionName")

package androidx.compose.ui

import android.content.Context

/** Mirrors the real `Modifier` contract closely enough for compile checks. */
interface Modifier {
    fun <R> fold(initial: R, operation: (R, Any) -> R): R

    companion object : Modifier {
        override fun <R> fold(initial: R, operation: (R, Any) -> R): R = initial
    }
}

inline class Dp(val value: Float) : Comparable<Dp> {
    override fun compareTo(other: Dp): Int = value.compareTo(other.value)
    operator fun times(other: Float): Dp = Dp(value * other)
    operator fun div(other: Float): Dp = Dp(value / other)
    operator fun plus(other: Dp): Dp = Dp(value + other.value)
}

inline class Sp(val value: Float)

enum class ContentAlignment { TopStart, TopCenter, TopEnd, CenterStart, Center, CenterEnd, BottomStart, BottomCenter, BottomEnd }

object Alignment {
    val Center: ContentAlignment get() = ContentAlignment.Center
    val CenterStart: ContentAlignment get() = ContentAlignment.CenterStart
    val CenterEnd: ContentAlignment get() = ContentAlignment.CenterEnd
    val TopStart: ContentAlignment get() = ContentAlignment.TopStart
    val TopCenter: ContentAlignment get() = ContentAlignment.TopCenter
    val TopEnd: ContentAlignment get() = ContentAlignment.TopEnd
    val BottomStart: ContentAlignment get() = ContentAlignment.BottomStart
    val BottomCenter: ContentAlignment get() = ContentAlignment.BottomCenter
    val BottomEnd: ContentAlignment get() = ContentAlignment.BottomEnd
    val CenterHorizontally: Horizontal get() = HorizontalCenter
    val CenterVertically: Vertical get() = VerticalCenter
    val Start: Horizontal get() = HorizontalStart
    val End: Horizontal get() = HorizontalEnd

    object HorizontalCenter : Horizontal
    object HorizontalStart : Horizontal
    object HorizontalEnd : Horizontal
    object VerticalCenter : Vertical

    interface Horizontal
    interface Vertical
}
