@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName", "FunctionName")

package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.Dp

class Arrangement {
    companion object {
        val SpaceBetween = Arrangement()
        val SpaceEvenly = Arrangement()
        val Center = Arrangement()
        val Start = Arrangement()
        val End = Arrangement()

        @JvmStatic
        fun spacedBy(space: Dp): Arrangement = Arrangement()
    }
}

class RowScope {
    fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier = this
    fun Modifier.align(alignment: Alignment.Vertical): Modifier = this
}

class ColumnScope {
    fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier = this
    fun Modifier.align(alignment: Alignment.Horizontal): Modifier = this
}

class BoxScope {
    fun Modifier.align(alignment: Alignment): Modifier = this
}

@Composable
fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
}

@Composable
fun Column(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement = Arrangement.Start,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
}

@Composable
fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: androidx.compose.ui.ContentAlignment = androidx.compose.ui.ContentAlignment.TopCenter,
    content: @Composable BoxScope.() -> Unit,
) {
}

@Composable
fun Spacer(modifier: Modifier) {}

fun Modifier.fillMaxSize(fraction: Float = 1f): Modifier = this
fun Modifier.fillMaxWidth(fraction: Float = 1f): Modifier = this
fun Modifier.fillMaxHeight(fraction: Float = 1f): Modifier = this
fun Modifier.width(width: Dp): Modifier = this
fun Modifier.height(height: Dp): Modifier = this
fun Modifier.size(size: Dp): Modifier = this
fun Modifier.size(width: Dp, height: Dp): Modifier = this
fun Modifier.padding(all: Dp): Modifier = this
fun Modifier.padding(horizontal: Dp = Dp(0f), vertical: Dp = Dp(0f)): Modifier = this
fun Modifier.padding(start: Dp = Dp(0f), top: Dp = Dp(0f), end: Dp = Dp(0f), bottom: Dp = Dp(0f)): Modifier = this
fun Modifier.defaultMinSize(minWidth: Dp, minHeight: Dp): Modifier = this
