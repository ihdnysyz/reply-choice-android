package com.personal.replyassistant.capture

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows
import android.os.Looper
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaptureDiagnosticsTest {
    private fun store() = CaptureDiagnostics(RuntimeEnvironment.getApplication()) { 100L }
    private val notification = NotificationEnvelope("com.tencent.mm", "secret-key", "私人姓名", "保密消息", 100L)

    @Test fun captureRequiresExplicitRunAndAppConsent() {
        val store = store()
        store.capture(notification, null, 100L)
        assertTrue(store.state.value.records.isEmpty())
        store.updateOptions(ProbeOptions(running = true, wechatEnabled = true))
        store.capture(notification, null, 100L)
        assertEquals(1, store.state.value.records.size)
    }

    @Test fun pausingPreventsNewRecordsAndDisablingAppClearsRetainedContent() {
        val store = store()
        store.updateOptions(ProbeOptions(running = true, wechatEnabled = true))
        store.capture(notification, null, 100L)
        store.updateOptions(ProbeOptions(running = false, wechatEnabled = true))
        store.capture(notification.copy(key = "another"), null, 101L)
        assertEquals(1, store.state.value.records.size)
        store.updateOptions(ProbeOptions())
        assertTrue(store.state.value.records.isEmpty())
    }

    @Test fun reportCannotContainMessageContentOrNotificationIdentifiers() {
        val store = store()
        store.updateOptions(ProbeOptions(running = true, wechatEnabled = true))
        store.capture(notification, null, 100L)
        val report = store.diagnosticReport()
        assertTrue(report.contains("微信"))
        assertFalse(report.contains("私人姓名"))
        assertFalse(report.contains("保密消息"))
        assertFalse(report.contains("secret-key"))
    }

    @Test fun expiryAndClearRemoveRecords() {
        val store = store()
        store.updateOptions(ProbeOptions(running = true, wechatEnabled = true))
        store.capture(notification, null, 100L)
        store.refresh(600100L)
        assertTrue(store.state.value.records.isEmpty())
        store.capture(notification.copy(postedAt = 600100L), null, 600100L)
        assertEquals(1, store.state.value.records.size)
        store.clear()
        assertTrue(store.state.value.records.isEmpty())
    }

    @Test fun retainedRecordsExpireWithoutUiRefreshOrAnotherMessage() {
        var time = 100L
        val store = CaptureDiagnostics(RuntimeEnvironment.getApplication()) { time }
        store.updateOptions(ProbeOptions(running = true, wechatEnabled = true))
        store.capture(notification, null, time)
        assertEquals(1, store.state.value.records.size)
        time += 600_000L
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600_000))
        assertTrue(store.state.value.records.isEmpty())
    }

    @Test fun callbackMetricsCountDuplicatesButExcludeReplayedActiveNotifications() {
        val store = store()
        store.updateOptions(ProbeOptions(running = true, wechatEnabled = true, qqEnabled = true))
        store.recordNotificationCallback("com.tencent.mm", replay = false)
        store.capture(notification, null, 100L, replay = false)
        store.recordNotificationCallback("com.tencent.mm", replay = false)
        store.capture(notification, null, 100L, replay = false)
        store.recordNotificationCallback("com.tencent.mobileqq", replay = true)
        store.capture(notification.copy(packageName = "com.tencent.mobileqq"), null, 100L, replay = true)
        assertEquals(2, store.state.value.records.size)
        val report = store.diagnosticReport()
        assertTrue(report.contains("微信回调=2"))
        assertTrue(report.contains("微信可读=2"))
        assertTrue(report.contains("QQ回调=0"))
        assertTrue(report.contains("历史重放=1"))
    }

    @Test fun reportSummarizesNotificationKeyStabilityWithoutExposingKeysOrNames() {
        val store = store()
        store.updateOptions(ProbeOptions(running = true, qqEnabled = true))
        val first = notification.copy(packageName = "com.tencent.mobileqq", key = "private-key-a", title = "私人甲")
        store.capture(first, null, 100L)
        store.capture(first.copy(key = "private-key-b", postedAt = 101L), null, 101L)
        store.capture(first.copy(title = "私人乙", postedAt = 102L), null, 102L)
        val report = store.diagnosticReport()
        assertTrue(report.contains("QQ通知键种类=2"))
        assertTrue(report.contains("QQ标题种类=2"))
        assertTrue(report.contains("同标题多键=1"))
        assertTrue(report.contains("单键跨标题=1"))
        assertFalse(report.contains("private-key"))
        assertFalse(report.contains("私人甲"))
        assertFalse(report.contains("私人乙"))
    }
}
