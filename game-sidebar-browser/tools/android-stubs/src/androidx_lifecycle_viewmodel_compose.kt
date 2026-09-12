@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package androidx.lifecycle.viewmodel.compose

import androidx.compose.runtime.Composable

@Composable
inline fun <reified VM : androidx.lifecycle.ViewModel> viewModel(
    factory: androidx.lifecycle.viewmodel.ViewModelProviderFactory? = null,
): VM = VM::class.java.getDeclaredConstructor().newInstance()
