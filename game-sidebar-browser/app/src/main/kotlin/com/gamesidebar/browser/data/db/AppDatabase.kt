package com.gamesidebar.browser.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local database: bookmarks, browsing history and notes. Nothing here is uploaded anywhere.
 *
 * Single instance per process, created on the background dispatcher by [get]; the overlay service
 * and the activities share it, which is what stops two write paths racing on the same file.
 */
@Database(
    entities = [BookmarkEntity::class, HistoryEntity::class, NoteEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun noteDao(): NoteDao

    companion object {
        private const val NAME = "gamesidebar.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                // A future schema change should ship a migration; falling back to destruction would
                // delete the user's bookmarks.
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { instance = it }
        }
    }
}
