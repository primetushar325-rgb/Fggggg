@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package androidx.activity.result

import android.content.Context
import android.content.Intent

fun interface ActivityResultCallback<O> {
    fun onActivityResult(result: O)
}

class ActivityResultLauncher<I> {
    fun launch(input: I) {}
}
