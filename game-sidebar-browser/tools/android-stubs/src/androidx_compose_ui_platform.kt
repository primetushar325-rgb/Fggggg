@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName", "FunctionName")

package androidx.compose.ui.platform

import android.content.Context
import androidx.compose.runtime.Composable

object LocalContext {
    val current: Context
        @Composable
        get() = StubContext()
}

private class StubContext : Context()
