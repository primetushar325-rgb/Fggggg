@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName", "FunctionName")

package androidx.compose.ui.unit

typealias Dp = androidx.compose.ui.Dp
typealias Sp = androidx.compose.ui.Sp

val Int.dp: Dp get() = Dp(toFloat())
val Float.dp: Dp get() = Dp(this)
val Double.dp: Dp get() = Dp(toFloat())
val Int.sp: Sp get() = Sp(toFloat())
val Float.sp: Sp get() = Sp(this)
val Double.sp: Sp get() = Sp(toFloat())
