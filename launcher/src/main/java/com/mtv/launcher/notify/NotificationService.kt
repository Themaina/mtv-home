package com.mtv.launcher.notify

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Grant access once over adb (the TV has no settings screen for it):
 *   adb shell cmd notification allow_listener com.mtv.launcher/com.mtv.launcher.notify.NotificationService
 */
class NotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationHub.service = this
        refresh()
    }

    override fun onListenerDisconnected() {
        NotificationHub.service = null
        NotificationHub.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val active = runCatching { activeNotifications }.getOrNull()
        NotificationHub.publish(this, active)
    }
}
