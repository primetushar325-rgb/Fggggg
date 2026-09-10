@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.content.res

class Resources {
    val displayMetrics: android.util.DisplayMetrics = android.util.DisplayMetrics()
    fun getIdentifier(name: String, defType: String, defPackage: String): Int = 0
    fun getDimensionPixelSize(id: Int): Int = 0
    fun getString(id: Int): String = ""
}

class Configuration {
    var orientation: Int = 0
    var screenWidthDp: Int = 0
    var screenHeightDp: Int = 0
}
