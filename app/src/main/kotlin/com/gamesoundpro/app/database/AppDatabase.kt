package com.gamesoundpro.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gamesoundpro.app.database.dao.SoundDao
import com.gamesoundpro.app.database.dao.SoundPackDao
import com.gamesoundpro.app.database.entity.SoundEntity
import com.gamesoundpro.app.database.entity.SoundPackEntity

@Database(
    entities = [SoundEntity::class, SoundPackEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun soundDao(): SoundDao
    abstract fun packDao(): SoundPackDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "gamesoundpro.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
