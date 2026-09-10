@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName", "FunctionName")

package androidx.compose.foundation.text

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

class KeyboardOptions(
    val keyboardType: KeyboardType = KeyboardType.Text,
    val imeAction: ImeAction = ImeAction.Default,
)

class KeyboardActions(
    val onGo: (() -> Unit)? = null,
    val onDone: (() -> Unit)? = null,
    val onNext: (() -> Unit)? = null,
    val onSearch: (() -> Unit)? = null,
)
