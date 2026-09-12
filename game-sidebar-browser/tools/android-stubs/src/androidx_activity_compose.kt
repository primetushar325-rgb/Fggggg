@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName", "FunctionName")

package androidx.activity.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable

fun ComponentActivity.setContent(content: @Composable () -> Unit) {}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {}
