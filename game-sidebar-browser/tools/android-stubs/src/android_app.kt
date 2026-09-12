@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.app

import android.content.Context
import android.content.Intent
import android.view.View

class Notification

class NotificationChannel(
    val id: String,
    val name: CharSequence,
    val importance: Int,
) {
    var description: String? = null
    fun setShowBadge(show: Boolean) {}
    fun enableLights(enabled: Boolean) {}
    fun enableVibration(enabled: Boolean) {}

    companion object {
        const val IMPORTANCE_LOW = 2
        const val IMPORTANCE_DEFAULT = 3
    }
}

class NotificationManager {
    fun getNotificationChannel(id: String): NotificationChannel? = null
    fun createNotificationChannel(channel: NotificationChannel) {}
    fun notify(id: Int, notification: Notification) {}

    companion object {
        const val IMPORTANCE_LOW = 2
        const val IMPORTANCE_DEFAULT = 3
    }
}

class PendingIntent {
    companion object {
        const val FLAG_UPDATE_CURRENT = 134217728
        const val FLAG_IMMUTABLE = 67108864

        @JvmStatic
        fun getActivity(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent = PendingIntent()

        @JvmStatic
        fun getService(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent = PendingIntent()
    }
}

class AlertDialog {
    val window: android.view.Window? = null
    fun show() {}
    fun dismiss() {}

    class Builder(context: Context) {
        fun setTitle(titleId: Int): Builder = this
        fun setTitle(title: CharSequence): Builder = this
        fun setView(view: View): Builder = this
        fun setView(view: View, left: Int, top: Int, right: Int, bottom: Int): Builder = this
        fun setPositiveButton(textId: Int, listener: OnClickListener?): Builder = this
        fun setNegativeButton(textId: Int, listener: OnClickListener?): Builder = this
        fun create(): AlertDialog = AlertDialog()
        fun show(): AlertDialog = AlertDialog()

        fun interface OnClickListener {
            fun onClick(dialog: android.content.DialogInterface, which: Int)
        }
    }
}

class DownloadManager {
    fun enqueue(request: Request): Long = 0L
    fun query(query: Query): android.database.Cursor = android.database.Cursor()

    class Request(val uri: android.net.Uri) {
        fun setTitle(title: CharSequence): Request = this
        fun setMimeType(mimeType: String): Request = this
        fun setNotificationVisibility(visibility: Int): Request = this
        fun addRequestHeader(header: String, value: String): Request = this
        fun setDestinationInExternalPublicDir(dirType: String, subPath: String): Request = this
        fun setDestinationInExternalFilesDir(
            context: Context,
            dirType: String?,
            subPath: String,
        ): Request = this

        companion object {
            const val VISIBILITY_VISIBLE_NOTIFY_COMPLETED = 1
        }
    }

    class Query {
        fun setFilterById(id: Long): Query = this
    }

    companion object {
        const val ACTION_DOWNLOAD_COMPLETE = "android.intent.action.DOWNLOAD_COMPLETE"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val COLUMN_STATUS = "status"
        const val COLUMN_TITLE = "title"
        const val STATUS_FAILED = 16
    }
}
