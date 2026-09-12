package com.gamesidebar.browser.data

import android.content.Context
import com.gamesidebar.browser.data.db.AppDatabase
import com.gamesidebar.browser.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency wiring.
 *
 * A DI framework would be dead weight for four singletons; what matters is that the overlay service
 * and the activities share one instance of each, so the database is opened once and settings changes
 * are observed from a single DataStore.
 */
class ServiceLocator private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.get(appContext) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val browserDataRepository: BrowserDataRepository by lazy {
        BrowserDataRepository(database, settingsRepository)
    }

    /** Scope for work that outlives any single screen (history writes, cache clearing). */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        @Volatile
        private var instance: ServiceLocator? = null

        fun get(context: Context): ServiceLocator = instance ?: synchronized(this) {
            instance ?: ServiceLocator(context).also { instance = it }
        }
    }
}
