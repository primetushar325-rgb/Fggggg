@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package androidx.browser.customtabs

import android.content.Context
import android.net.Uri

class CustomTabsIntent private constructor(private val urlBarHides: Boolean) {
    val intent: android.content.Intent = android.content.Intent()

    fun launchUrl(context: Context, url: Uri) {}

    class Builder {
        fun setShowTitle(showTitle: Boolean): Builder = this
        fun setUrlBarHidingEnabled(enabled: Boolean): Builder = this
        fun setShareState(state: Int): Builder = this
        fun build(): CustomTabsIntent = CustomTabsIntent(false)
    }

    companion object {
        const val SHARE_STATE_OFF = 0
        const val SHARE_STATE_ON = 1
    }
}
