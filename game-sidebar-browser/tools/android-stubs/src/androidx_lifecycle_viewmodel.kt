@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package androidx.lifecycle.viewmodel

class CreationExtras

class InitializerViewModelFactoryBuilder {
    internal val initializers = mutableListOf<Any>()
}

/** Mirrors the real top-level `initializer` builder function. */
inline fun <reified VM : androidx.lifecycle.ViewModel> InitializerViewModelFactoryBuilder.initializer(
    noinline initializer: CreationExtras.() -> VM,
) {
}

class ViewModelProviderFactory

fun viewModelFactory(
    builder: InitializerViewModelFactoryBuilder.() -> Unit,
): ViewModelProviderFactory = ViewModelProviderFactory()
