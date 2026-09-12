@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package androidx.core.app

import android.app.Notification
import android.app.PendingIntent
import android.content.Context

class NotificationCompat {
    class Builder(context: Context, channelId: String) {
        fun setSmallIcon(icon: Int): Builder = this
        fun setContentTitle(title: CharSequence): Builder = this
        fun setContentText(text: CharSequence): Builder = this
        fun setStyle(style: Style?): Builder = this
        fun setContentIntent(intent: PendingIntent): Builder = this
        fun setOngoing(ongoing: Boolean): Builder = this
        fun setForegroundServiceBehavior(behavior: Int): Builder = this
        fun setSilent(silent: Boolean): Builder = this
        fun setShowWhen(showWhen: Boolean): Builder = this
        fun setTicker(ticker: CharSequence): Builder = this
        fun setGroup(groupKey: String): Builder = this
        fun setColorized(colorized: Boolean): Builder = this
        fun setPriority(priority: Int): Builder = this
        fun setCategory(category: String): Builder = this
        fun setOnlyAlertOnce(onlyAlertOnce: Boolean): Builder = this
        fun addAction(icon: Int, title: CharSequence, intent: PendingIntent?): Builder = this
        fun build(): Notification = Notification()
    }

    abstract class Style

    class BigTextStyle : Style() {
        fun bigText(text: CharSequence): BigTextStyle = this
    }

    companion object {
        const val FOREGROUND_SERVICE_IMMEDIATE = 1
        const val PRIORITY_LOW = -1
        const val CATEGORY_SERVICE = "service"
        const val CATEGORY_ERROR = "err"
        const val CATEGORY_TRANSPORT = "transport"
    }
}
