@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package androidx.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Window

open class ComponentActivity : Context() {
    val intent: Intent? = null
    val window: Window = Window()
    val lifecycle: androidx.lifecycle.Lifecycle = androidx.lifecycle.Lifecycle()

    open fun onCreate(savedInstanceState: Bundle?) {}
    open fun onResume() {}
    open fun onPause() {}
    open fun onDestroy() {}
    fun <I, O> registerForActivityResult(
        contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
        callback: androidx.activity.result.ActivityResultCallback<O>,
    ): androidx.activity.result.ActivityResultLauncher<I> = androidx.activity.result.ActivityResultLauncher()

}
