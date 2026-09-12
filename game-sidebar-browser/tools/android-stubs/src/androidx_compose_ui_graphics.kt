@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName", "FunctionName")

package androidx.compose.ui.graphics

/** Plain class here; the real type is an inline class over a packed ULong. */
class Color(val argb: Long) {
    constructor(argb: Int) : this(argb.toLong())

    fun copy(alpha: Float = 1f): Color = this

    companion object {
        val White = Color(0xFFFFFFFFL)
        val Black = Color(0xFF000000L)
        val Transparent = Color(0L)
    }
}

class Brush {
    companion object {
        @JvmStatic
        fun linearGradient(colors: List<Color>): Brush = Brush()

        @JvmStatic
        fun verticalGradient(colors: List<Color>): Brush = Brush()

        @JvmStatic
        fun horizontalGradient(colors: List<Color>): Brush = Brush()

        @JvmStatic
        fun radialGradient(colors: List<Color>): Brush = Brush()
    }
}
