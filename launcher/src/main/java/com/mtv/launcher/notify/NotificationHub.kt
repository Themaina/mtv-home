package com.mtv.launcher.notify

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification

/** A notification as shown on the home screen. */
data class Note(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val icon: Drawable?,
    val contentIntent: PendingIntent?,
    val autoCancel: Boolean,
)

/** Shares the listener service's notifications with the home screen (same process). */
object NotificationHub {

    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<(List<Note>) -> Unit>()

    @Volatile var notes: List<Note> = emptyList()
        private set

    /** Null until the user (or adb) grants notification access. */
    @Volatile var service: NotificationService? = null
        internal set

    val connected: Boolean get() = service != null

    fun observe(listener: (List<Note>) -> Unit) {
        listeners += listener
        listener(notes)
    }

    fun stopObserving(listener: (List<Note>) -> Unit) {
        listeners -= listener
    }

    fun dismiss(note: Note) {
        runCatching { service?.cancelNotification(note.key) }
    }

    internal fun publish(context: Context, active: Array<StatusBarNotification>?) {
        val pm = context.packageManager
        val list = active.orEmpty()
            .filter { sbn ->
                val n = sbn.notification
                !sbn.isOngoing &&
                    n.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
                    sbn.packageName != context.packageName
            }
            .mapNotNull { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                    ?.toString().orEmpty()
                if (title.isBlank() && text.isBlank()) return@mapNotNull null
                val appInfo = runCatching { pm.getApplicationInfo(sbn.packageName, 0) }.getOrNull()
                Note(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appLabel = appInfo?.loadLabel(pm)?.toString() ?: sbn.packageName,
                    title = title.ifBlank { text },
                    text = if (title.isBlank()) "" else text,
                    postedAt = sbn.postTime,
                    icon = appInfo?.loadIcon(pm),
                    contentIntent = sbn.notification.contentIntent,
                    autoCancel = sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0,
                )
            }
            .sortedByDescending { it.postedAt }
            .take(20)
        main.post {
            notes = list
            listeners.toList().forEach { it(list) }
        }
    }

    internal fun clear() {
        main.post {
            notes = emptyList()
            listeners.toList().forEach { it(emptyList()) }
        }
    }
}
