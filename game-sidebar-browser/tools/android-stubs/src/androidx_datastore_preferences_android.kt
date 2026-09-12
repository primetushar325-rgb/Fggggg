@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package androidx.datastore.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Mirrors the real `preferencesDataStore` property delegate. */
class PreferenceDataStoreFactory {
    companion object {
        @JvmStatic
        fun create(name: String): DataStore<Preferences> = StubDataStore()
    }
}

private class StubDataStore : DataStore<Preferences> {
    override val data: Flow<Preferences> = flowOf(Preferences())
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = Preferences()
}

/** Stand-in for the generated delegate so `val Context.dataStore by preferencesDataStore(...)` type-checks. */
fun preferencesDataStore(name: String): PreferenceDataStoreProperty = PreferenceDataStoreProperty(name)

class PreferenceDataStoreProperty(private val name: String) {
    operator fun getValue(thisRef: Context, property: kotlin.reflect.KProperty<*>): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(name)
}
