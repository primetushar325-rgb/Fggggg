@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.view.inputmethod

class EditorInfo {
    companion object {
        const val IME_ACTION_UNSPECIFIED = 0
        const val IME_ACTION_NONE = 1
        const val IME_ACTION_GO = 2
        const val IME_ACTION_SEARCH = 3
        const val IME_ACTION_SEND = 4
        const val IME_ACTION_NEXT = 5
        const val IME_ACTION_DONE = 6
    }
}

class InputMethodManager {
    fun showSoftInput(view: android.view.View, flags: Int): Boolean = true
    fun hideSoftInputFromWindow(windowToken: android.os.IBinder, flags: Int): Boolean = true
}
