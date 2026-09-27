package com.personal.replyassistant

import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.Manifest
import com.personal.replyassistant.capture.CaptureDiagnostics
import com.personal.replyassistant.generation.ReplyController
import com.personal.replyassistant.settings.ModelSettings
import com.personal.replyassistant.presentation.SuggestionNotifier

class App : Application() {
    val diagnostics by lazy { CaptureDiagnostics(this) }
    val modelSettings by lazy { ModelSettings(this) }
    val suggestionNotifier by lazy { SuggestionNotifier(this) }
    val replies by lazy {
        ReplyController(modelSettings, diagnostics,
            canNotify = {
                val manager = getSystemService(NotificationManager::class.java)
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
                    manager.areNotificationsEnabled() &&
                    manager.getNotificationChannel("reply_suggestions")?.importance != NotificationManager.IMPORTANCE_NONE
            },
            onAutomaticReady = suggestionNotifier::show,
            onSuggestionsCleared = suggestionNotifier::clear).also { controller ->
            diagnostics.onNewRecord = controller::onRecordCaptured
            diagnostics.onCapturePaused = controller::onCapturePaused
        }
    }

    override fun onCreate() {
        super.onCreate()
        replies // Register the capture observer before the listener receives a new notification.
    }
}
