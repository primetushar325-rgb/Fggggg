@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.util

class DisplayMetrics {
    var density: Float = 3f
    var widthPixels: Int = 1080
    var heightPixels: Int = 2400
    var scaledDensity: Float = 3f
}

object Log {
    @JvmStatic
    fun d(tag: String, msg: String): Int = 0

    @JvmStatic
    fun w(tag: String, msg: String): Int = 0

    @JvmStatic
    fun e(tag: String, msg: String): Int = 0
}
