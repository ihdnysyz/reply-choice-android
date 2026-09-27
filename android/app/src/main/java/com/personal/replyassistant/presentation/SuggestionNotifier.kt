package com.personal.replyassistant.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.personal.replyassistant.MainActivity

class SuggestionNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val channelId = "reply_suggestions"

    fun show() {
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel(channelId, "回复建议", NotificationManager.IMPORTANCE_DEFAULT))
        val activity = Intent(context, MainActivity::class.java).apply {
            putExtra("open_reply", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, 1, activity,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("已有三条回复建议")
            .setContentText("点按查看并选择")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .build()
        try { manager.notify(11, notification) } catch (_: SecurityException) { /* Permission may be revoked mid-request. */ }
    }

    fun clear() { manager.cancel(11) }
}
