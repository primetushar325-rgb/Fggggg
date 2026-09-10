@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package androidx.core.content

import android.content.Context

object ContextCompat {
    @JvmStatic
    fun checkSelfPermission(context: Context, permission: String): Int = 0

    @JvmStatic
    fun startActivity(context: Context, intent: android.content.Intent, options: android.os.Bundle?) {}
}
