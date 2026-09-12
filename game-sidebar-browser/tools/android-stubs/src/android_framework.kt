@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

/**
 * A minimal mirror of the Android framework APIs this app uses.
 *
 * The shipping build is Gradle + the real Android SDK. These stubs exist so `tools/check_app.sh`
 * can compile-check the whole app module (services, overlay, WebView, Room, DataStore, ViewModels)
 * on any machine with a JDK and kotlinc - including CI and sandboxes with no SDK and no network.
 *
 * Signatures deliberately mirror the real ones: a call that compiles here is expected to compile
 * against the platform, and a drift shows up as a compile error rather than a runtime crash.
 */

package android.content

import android.content.res.Resources
import android.os.Bundle
import android.os.IBinder

open class ActivityNotFoundException(message: String? = null) : Exception(message)

class ComponentName

open class Context {
    open fun getSystemService(name: String): Any? = null
    open fun <T> getSystemService(serviceClass: Class<T>): T? = null
    open fun getString(resId: Int): String = ""
    open fun getString(resId: Int, vararg formatArgs: Any): String = ""
    open val resources: Resources = Resources()
    open val packageName: String = "com.gamesidebar.browser"
    open val applicationContext: Context get() = this
    open val packageManager: android.content.pm.PackageManager = android.content.pm.PackageManager()
    open val applicationInfo: android.content.pm.ApplicationInfo = android.content.pm.ApplicationInfo()
    open fun startActivity(intent: Intent) {}
    open fun startService(intent: Intent): ComponentName? = null
    open fun startForegroundService(intent: Intent): ComponentName? = null
    open fun stopService(intent: Intent): Boolean = true
    open fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter): Intent? = null
    open fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter, flags: Int): Intent? = null
    open fun unregisterReceiver(receiver: BroadcastReceiver) {}
    open fun sendBroadcast(intent: Intent) {}

    companion object {
        const val RECEIVER_NOT_EXPORTED = 4
        const val CLIPBOARD_SERVICE = "clipboard"
        const val WINDOW_SERVICE = "window"
    }
}

open class Intent {
    constructor()
    constructor(action: String)
    constructor(action: String, uri: android.net.Uri)
    constructor(context: Context, cls: Class<*>)

    var action: String? = null
    var data: android.net.Uri? = null
    var component: ComponentName? = null
    var type: String? = null
    var flags: Int = 0

    fun setAction(action: String): Intent = this
    fun setData(uri: android.net.Uri): Intent = this
    fun setPackage(packageName: String?): Intent = this
    fun setComponent(component: ComponentName?): Intent = this
    fun addCategory(category: String): Intent = this
    fun addFlags(flags: Int): Intent = this
    fun putExtra(name: String, value: String?): Intent = this
    fun putExtra(name: String, value: Long): Intent = this
    fun putExtra(name: String, value: Boolean): Intent = this
    fun getStringExtra(name: String): String? = null
    fun getLongExtra(name: String, default: Long): Long = default
    val dataString: String? get() = null

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_MAIN = "android.intent.action.MAIN"
        const val ACTION_SETTINGS = "android.settings.SETTINGS"
        const val CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE"
        const val CATEGORY_DEFAULT = "android.intent.category.DEFAULT"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
        const val URI_INTENT_SCHEME = 1
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
        const val FLAG_ACTIVITY_CLEAR_TOP = 0x04000000

        @JvmStatic
        fun parseUri(uri: String, flags: Int): Intent = Intent()

        @JvmStatic
        fun createChooser(target: Intent, title: CharSequence): Intent = Intent()
    }
}

class IntentFilter {
    constructor()
    constructor(action: String)
}

abstract class BroadcastReceiver {
    abstract fun onReceive(context: Context, intent: Intent)
}

class ClipboardManager {
    /**
     * Method pair, not a property: the platform getter is @Nullable and the setter is not, so the
     * types do not match and Kotlin only synthesises a read-only `primaryClip`.
     */
    fun getPrimaryClip(): ClipData? = null
    fun setPrimaryClip(clip: ClipData) {}
}

class ClipData {
    val itemCount: Int = 0
    val description: ClipDescription? = null
    fun getItemAt(index: Int): Item = Item()

    class Item {
        fun coerceToText(context: Context): CharSequence = ""
    }

    companion object {
        @JvmStatic
        fun newPlainText(label: CharSequence?, text: CharSequence): ClipData = ClipData()
    }
}

class ClipDescription {
    val label: CharSequence? = null
    val extras: Bundle? = null

    companion object {
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}

interface DialogInterface

@Suppress("unused")
private fun unusedIbinderReference(): IBinder? = null
