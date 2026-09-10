@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName", "FunctionName")

package androidx.compose.foundation

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

fun Modifier.background(color: Color, shape: Shape? = null): Modifier = this
fun Modifier.background(brush: androidx.compose.ui.graphics.Brush, shape: Shape? = null): Modifier = this
fun Modifier.border(width: androidx.compose.ui.Dp, color: Color, shape: Shape? = null): Modifier = this
fun Modifier.clickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = this
fun Modifier.horizontalScroll(state: ScrollState): Modifier = this
fun Modifier.verticalScroll(state: ScrollState): Modifier = this

class ScrollState

fun rememberScrollState(): ScrollState = ScrollState()
