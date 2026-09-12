@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.app

import android.content.Context

open class Application : Context() {
    open fun onCreate() {}
    open fun onTerminate() {}
}
