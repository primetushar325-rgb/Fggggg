@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package androidx.room

abstract class RoomDatabase {
    abstract fun close()
    fun clearAllTables() {}
}

class Room {
    companion object {
        @JvmStatic
        fun <T : RoomDatabase> databaseBuilder(
            context: android.content.Context,
            klass: Class<T>,
            name: String,
        ): DatabaseBuilder<T> = DatabaseBuilder()
    }
}

class DatabaseBuilder<T : RoomDatabase> {
    fun fallbackToDestructiveMigration(): DatabaseBuilder<T> = this
    fun fallbackToDestructiveMigrationOnDowngrade(): DatabaseBuilder<T> = this
    fun setQueryCallback(callback: QueryCallback, executor: java.util.concurrent.Executor): DatabaseBuilder<T> = this
    fun build(): T = throw IllegalStateException("Room stub: no schema processor in the offline check")
}

fun interface QueryCallback {
    fun onQuery(query: String, bindArgs: List<Any?>)
}

@Target(AnnotationTarget.CLASS)
annotation class Database(val entities: Array<kotlin.reflect.KClass<*>>, val version: Int, val exportSchema: Boolean = true)

@Target(AnnotationTarget.CLASS)
annotation class Dao

@Target(AnnotationTarget.FUNCTION)
annotation class Query(val value: String)

@Target(AnnotationTarget.FUNCTION)
annotation class Insert(val onConflict: Int = 1)

@Target(AnnotationTarget.FUNCTION)
annotation class Update

@Target(AnnotationTarget.FUNCTION)
annotation class Delete

@Target(AnnotationTarget.CLASS)
annotation class Entity(val tableName: String = "", val indices: Array<Index> = [])

annotation class Index(val value: Array<String>)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class PrimaryKey(val autoGenerate: Boolean = false)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class ColumnInfo(val name: String = "", val defaultValue: String = "")

object OnConflictStrategy {
    const val REPLACE = 1
    const val IGNORE = 5
}
