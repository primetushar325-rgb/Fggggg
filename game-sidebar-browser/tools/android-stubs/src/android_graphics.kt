@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.graphics

class Color {
    companion object {
        const val WHITE = -0x1
        const val BLACK = -0x1000000
        const val TRANSPARENT = 0

        @JvmStatic
        fun parseColor(colorString: String): Int = 0

        @JvmStatic
        fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int = 0
    }
}

class Paint {
    var color: Int = 0
    var alpha: Int = 255
    var style: Style = Style.FILL
    var strokeWidth: Float = 0f
    var isAntiAlias: Boolean = false

    constructor()
    constructor(flags: Int)

    enum class Style { FILL, STROKE, FILL_AND_STROKE }

    companion object {
        const val ANTI_ALIAS_FLAG = 1
    }
}

class Canvas {
    fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) {}
    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {}
    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {}
}

class RectF {
    constructor()
    constructor(left: Float, top: Float, right: Float, bottom: Float)
    constructor(other: RectF)

    var left: Float = 0f
    var top: Float = 0f
    var right: Float = 0f
    var bottom: Float = 0f

    fun set(left: Float, top: Float, right: Float, bottom: Float) {}
    fun inset(dx: Float, dy: Float) {}
}

class Rect {
    var left: Int = 0
    var top: Int = 0
    var right: Int = 0
    var bottom: Int = 0
    fun width(): Int = right - left
    fun height(): Int = bottom - top
}

class Point {
    constructor()
    constructor(x: Int, y: Int)

    var x: Int = 0
    var y: Int = 0
}

class Bitmap {
    val width: Int = 0
    val height: Int = 0
}

object PixelFormat {
    const val TRANSLUCENT = -3
    const val OPAQUE = -1
    const val TRANSPARENT = 0
}

class Typeface {
    companion object {
        const val NORMAL = 0
        const val BOLD = 1

        @JvmStatic
        fun create(family: String?, style: Int): Typeface = Typeface()
    }
}
