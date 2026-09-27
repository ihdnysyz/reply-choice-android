package com.personal.replyassistant.capture

import android.app.Notification
import android.os.Build

object NotificationReader {
    fun read(notification: Notification, packageName: String, key: String, postedAt: Long): NotificationEnvelope {
        val extras = notification.extras
        @Suppress("DEPRECATION")
        val messageBundles = extras?.getParcelableArray(Notification.EXTRA_MESSAGES)
        val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            messageBundles?.let { Notification.MessagingStyle.Message.getMessagesFromBundleArray(it) }
        } else null
        val text = messages?.lastOrNull { !it.text.isNullOrBlank() }?.text?.toString()
            ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.lastOrNull()?.toString()
            ?: ""
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val group = if (extras?.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION) == true) {
            extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
        } else null
        return NotificationEnvelope(
            packageName = packageName,
            key = key,
            title = title.take(200),
            text = text.take(2000),
            postedAt = postedAt,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isGroupConversation = group,
            hasMessagingStyle = messageBundles != null,
            hasShortcutId = !notification.shortcutId.isNullOrBlank(),
            hasContentIntent = notification.contentIntent != null,
            hasRemoteInput = notification.actions?.any { !it.remoteInputs.isNullOrEmpty() } == true,
        )
    }
}
