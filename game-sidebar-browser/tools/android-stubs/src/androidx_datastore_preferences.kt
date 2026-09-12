@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package androidx.datastore.preferences.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

open class Preferences {
    operator fun <T> get(key: Key<T>): T? = null

    data class Key<T>(val name: String)
}

class MutablePreferences : Preferences() {
    operator fun <T> set(key: Key<T>, value: T) {}
    fun <T> remove(key: Key<T>): T? = null
    fun clear() {}
}

fun emptyPreferences(): Preferences = Preferences()

fun stringPreferencesKey(name: String): Preferences.Key<String> = Preferences.Key(name)
fun intPreferencesKey(name: String): Preferences.Key<Int> = Preferences.Key(name)
fun booleanPreferencesKey(name: String): Preferences.Key<Boolean> = Preferences.Key(name)
fun longPreferencesKey(name: String): Preferences.Key<Long> = Preferences.Key(name)
fun floatPreferencesKey(name: String): Preferences.Key<Float> = Preferences.Key(name)

/**
 * Mirrors the real signature exactly: `transform` is a PARAMETER lambda over MutablePreferences
 * (`Function2<MutablePreferences, Continuation<Unit>, Object?>` in the published API), not a
 * receiver lambda - so `edit { this[k] = v }` must not compile here either.
 */
suspend fun androidx.datastore.core.DataStore<Preferences>.edit(
    transform: suspend (t: MutablePreferences) -> Unit,
): Preferences = updateData { prefs -> MutablePreferences().also { transform(it) } }
