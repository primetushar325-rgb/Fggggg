@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.database

open class Cursor : AutoCloseable {
    open fun moveToFirst(): Boolean = false
    open fun getInt(columnIndex: Int): Int = 0
    open fun getString(columnIndex: Int): String? = null
    open fun getColumnIndexOrThrow(columnName: String): Int = 0
    override fun close() {}
}
