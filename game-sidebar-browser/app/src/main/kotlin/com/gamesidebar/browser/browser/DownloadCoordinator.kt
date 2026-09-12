package com.gamesidebar.browser.browser

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import com.gamesidebar.core.download.DownloadRequest
import com.gamesidebar.core.download.Downloads

/**
 * Web downloads.
 *
 * Always through [DownloadManager], always with the user confirming first, and always into a place
 * the user can reach: the public Downloads collection on Android 10+, the app's own external files
 * directory below that (which needs no storage permission, so the app asks for none).
 */
class DownloadCoordinator(private val context: Context) {

    interface Listener {
        fun onDownloadStarted(fileName: String, location: String)
        fun onDownloadFailed(fileName: String, reason: String)
    }

    private var listener: Listener? = null

    private val completionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id == -1L) return
            query(id)
        }
    }

    fun attach(listener: Listener) {
        this.listener = listener
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(completionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(completionReceiver, filter)
        }
    }

    fun detach() {
        listener = null
        runCatching { context.unregisterReceiver(completionReceiver) }
    }

    /** Turns a WebView download callback into a validated request the UI can show to the user. */
    fun requestFrom(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
    ): DownloadRequest {
        val fileName = Downloads.resolveFileName(
            contentDisposition = contentDisposition,
            url = url,
            mimeType = mimeType,
            urlGuess = URLUtil.guessFileName(url, contentDisposition, mimeType),
        )
        val resolvedMime = mimeType?.takeIf { it.isNotBlank() } ?: Downloads.guessMimeType(fileName)
        return DownloadRequest(
            url = url,
            fileName = fileName,
            mimeType = resolvedMime,
            contentLengthBytes = contentLength,
            userAgent = userAgent,
            referer = null,
        )
    }

    fun enqueue(request: DownloadRequest): Boolean {
        val manager = context.getSystemService(DownloadManager::class.java) ?: return false
        return try {
            val download = DownloadManager.Request(Uri.parse(request.url)).apply {
                setTitle(request.fileName)
                setMimeType(request.mimeType)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.userAgent?.let { addRequestHeader("User-Agent", it) }
                CookieManager.getInstance().getCookie(request.url)?.let { addRequestHeader("Cookie", it) }
                destinationFor(request)
            }
            manager.enqueue(download)
            listener?.onDownloadStarted(request.fileName, request.targetSubdir)
            true
        } catch (notFound: IllegalArgumentException) {
            listener?.onDownloadFailed(request.fileName, "error_download_failed")
            false
        } catch (security: SecurityException) {
            listener?.onDownloadFailed(request.fileName, "error_download_failed")
            false
        }
    }

    private fun DownloadManager.Request.destinationFor(request: DownloadRequest) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Scoped storage: DownloadManager writes straight into the public collection.
            setDestinationInExternalPublicDir(publicDirFor(request.targetSubdir), request.fileName)
        } else {
            // No WRITE_EXTERNAL_STORAGE anywhere in this app, so below Q the app-private external
            // directory is used. It is a real file the user can open from the notification.
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, request.fileName)
        }
    }

    private fun publicDirFor(subdir: String): String = when (subdir) {
        "Pictures" -> Environment.DIRECTORY_PICTURES
        "Movies" -> Environment.DIRECTORY_MOVIES
        "Music" -> Environment.DIRECTORY_MUSIC
        else -> Environment.DIRECTORY_DOWNLOADS
    }

    private fun query(downloadId: Long) {
        val manager = context.getSystemService(DownloadManager::class.java) ?: return
        runCatching {
            manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                    if (status == DownloadManager.STATUS_FAILED) {
                        listener?.onDownloadFailed(name ?: "download", "error_download_failed")
                    }
                }
            }
        }
    }
}
