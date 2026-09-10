@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.text

interface Editable : CharSequence

interface TextWatcher {
    fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int)
    fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int)
    fun afterTextChanged(s: Editable?)
}

object InputType {
    const val TYPE_CLASS_TEXT = 1
    const val TYPE_TEXT_FLAG_MULTI_LINE = 131072
    const val TYPE_TEXT_FLAG_CAP_SENTENCES = 16384
    const val TYPE_TEXT_VARIATION_URI = 16
}

object TextUtils {
    @JvmStatic
    fun isEmpty(str: CharSequence?): Boolean = str.isNullOrEmpty()
}
