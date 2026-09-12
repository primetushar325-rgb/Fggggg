@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.content.pm

class PackageManager {
    fun getPackageInfo(packageName: String, flags: Int): PackageInfo = PackageInfo()
    fun getLaunchIntentForPackage(packageName: String): android.content.Intent? = null

    companion object {
        const val PERMISSION_GRANTED = 0
        const val PERMISSION_DENIED = -1
    }
}

class PackageInfo {
    var versionName: String? = null
    var versionCode: Int = 0
}

class ApplicationInfo {
    var flags: Int = 0

    companion object {
        const val FLAG_DEBUGGABLE = 0x00000002
    }
}
