@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName", "FunctionName")

package androidx.compose.ui.viewinterop

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun <T : View> AndroidView(
    factory: (android.content.Context) -> T,
    modifier: Modifier = Modifier,
    update: (T) -> Unit = {},
) {
}
