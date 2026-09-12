@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package androidx.activity.result.contract

import android.content.Context

abstract class ActivityResultContract<I, O> {
    abstract fun createIntent(context: Context, input: I): android.content.Intent
    abstract fun parseResult(resultCode: Int, intent: android.content.Intent?): O
}

class ActivityResultContracts {
    class StartActivityForResult : ActivityResultContract<android.content.Intent, android.app.ActivityResult>() {
        override fun createIntent(context: Context, input: android.content.Intent) = input
        override fun parseResult(resultCode: Int, intent: android.content.Intent?) = android.app.ActivityResult()
    }

    class RequestPermission : ActivityResultContract<String, Boolean>() {
        override fun createIntent(context: Context, input: String) = android.content.Intent()
        override fun parseResult(resultCode: Int, intent: android.content.Intent?) = true
    }
}
