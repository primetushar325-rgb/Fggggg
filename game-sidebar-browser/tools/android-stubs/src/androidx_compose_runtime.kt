@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName", "FunctionName")

package androidx.compose.runtime

import kotlinx.coroutines.CoroutineScope

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.TYPE,
    AnnotationTarget.TYPE_PARAMETER,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.CLASS,
)
@Retention(AnnotationRetention.BINARY)
annotation class Composable

@Composable
fun <T> remember(calculation: () -> T): T = calculation()

@Composable
fun <T> remember(key1: Any?, calculation: () -> T): T = calculation()

@Composable
fun <T> mutableStateOf(value: T): MutableState<T> = object : MutableState<T> {
    override var value: T = value
}

interface State<out T> {
    val value: T
}

interface MutableState<T> : State<T> {
    override var value: T
}

operator fun <T> State<T>.getValue(thisObj: Any?, property: kotlin.reflect.KProperty<*>): T = value
operator fun <T> MutableState<T>.setValue(thisObj: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
    this.value = value
}

@Composable
fun DisposableEffect(key1: Any?, effect: DisposableEffectScope.() -> DisposableEffectResult) {}

interface DisposableEffectScope {
    fun onDispose(onDispose: () -> Unit): DisposableEffectResult
}

class DisposableEffectResult

@Composable
fun LaunchedEffect(key1: Any?, block: suspend CoroutineScope.() -> Unit) {}

@Composable
fun SideEffect(effect: () -> Unit) {}

class Composer
