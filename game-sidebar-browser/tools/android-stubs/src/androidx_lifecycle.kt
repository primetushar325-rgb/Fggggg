@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package androidx.lifecycle

import android.app.Application
import kotlinx.coroutines.CoroutineScope

class Lifecycle

open class ViewModel {
    open fun onCleared() {}
}

open class AndroidViewModel(private val application: Application) : ViewModel() {
    fun <T : Application> getApplication(): T = application as T
}

val ViewModel.viewModelScope: CoroutineScope get() = kotlinx.coroutines.GlobalScope

val androidx.activity.ComponentActivity.lifecycleScope: CoroutineScope get() = kotlinx.coroutines.GlobalScope
