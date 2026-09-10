@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName", "FunctionName")

package androidx.compose.foundation.lazy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

class LazyListScope {
    companion object
    fun item(key: Any? = null, content: @Composable () -> Unit) {}
    fun items(count: Int, key: ((index: Int) -> Any)? = null, itemContent: @Composable (Int) -> Unit) {}
    fun <T> items(items: List<T>, key: ((item: T) -> Any)? = null, itemContent: @Composable (T) -> Unit) {}
}

fun <T> LazyListScope.items(
    items: List<T>,
    key: ((item: T) -> Any)? = null,
    contentType: (item: T) -> Any? = { null },
    itemContent: @Composable (item: T) -> Unit,
) {
}

fun LazyListScope.items(
    count: Int,
    key: ((index: Int) -> Any)? = null,
    itemContent: @Composable (index: Int) -> Unit,
) {
}

@Composable
fun LazyColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement = Arrangement.Start,
    content: LazyListScope.() -> Unit,
) {
}

@Composable
fun LazyRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement = Arrangement.Start,
    verticalAlignment: androidx.compose.ui.Alignment.Vertical = androidx.compose.ui.Alignment.CenterVertically,
    content: LazyListScope.() -> Unit,
) {
}
