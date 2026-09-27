package com.personal.replyassistant

import android.app.NotificationManager
import android.content.ComponentName
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.personal.replyassistant.capture.ChatNotificationListener
import com.personal.replyassistant.ui.ProbeApp

class MainActivity : ComponentActivity() {
    private val openReplySignal = mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.getBooleanExtra("open_reply", false)) openReplySignal.intValue++
        // Message samples are private; don't expose them in screenshots or the recent-app preview.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { ProbeApp((application as App).diagnostics, openReplySignal.intValue) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_reply", false)) openReplySignal.intValue++
    }

    override fun onResume() {
        super.onResume()
        val store = (application as App).diagnostics
        val granted = getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(this, ChatNotificationListener::class.java))
        if (!granted) {
            store.setConnected(false)
            store.updateOptions(store.state.value.options.copy(running = false))
            store.clear()
        }
        store.refresh(System.currentTimeMillis())
    }
}
