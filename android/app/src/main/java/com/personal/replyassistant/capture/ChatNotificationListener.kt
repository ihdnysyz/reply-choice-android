package com.personal.replyassistant.capture

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.personal.replyassistant.App

class ChatNotificationListener : NotificationListenerService() {
    private val store get() = (application as App).diagnostics

    override fun onListenerConnected() {
        store.setConnected(true)
        try {
            activeNotifications?.forEach { handleNotification(it, replay = true) }
        } catch (_: SecurityException) {
            store.setConnected(false)
        }
    }

    override fun onListenerDisconnected() { store.setConnected(false) }

    override fun onDestroy() {
        store.setConnected(false)
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        handleNotification(sbn, replay = false)
    }

    private fun handleNotification(sbn: StatusBarNotification?, replay: Boolean) {
        sbn ?: return
        val options = store.state.value.options
        if (!options.running) return
        val enabled = when (sbn.packageName) {
            "com.tencent.mm" -> options.wechatEnabled
            "com.tencent.mobileqq" -> options.qqEnabled
            else -> false
        }
        if (!enabled) return
        store.recordNotificationCallback(sbn.packageName, replay)
        // Restrict access before inspecting notification extras, including third-party Parcelable data.
        try {
            val envelope = NotificationReader.read(sbn.notification, sbn.packageName, sbn.key, sbn.postTime)
            store.capture(envelope, sbn.notification.contentIntent, System.currentTimeMillis(), replay)
        } catch (_: RuntimeException) {
            // Malformed third-party notifications must not crash the listener or leak their content to logs.
            store.recordParseFailure()
        }
    }
}
