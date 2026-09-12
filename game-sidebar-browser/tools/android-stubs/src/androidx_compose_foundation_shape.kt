@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName", "FunctionName")

package androidx.compose.foundation.shape

import androidx.compose.ui.Dp
import androidx.compose.ui.graphics.Shape

class RoundedCornerShape(val corner: Dp) : Shape {
    constructor(corner: Int) : this(Dp(corner.toFloat()))
    constructor(topStart: Dp, topEnd: Dp, bottomStart: Dp, bottomEnd: Dp) : this(topStart)
}

object CircleShape : Shape
