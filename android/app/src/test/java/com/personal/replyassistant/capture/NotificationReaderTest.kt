package com.personal.replyassistant.capture

import android.app.Notification
import android.app.Person
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationReaderTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun readsTextWithoutSplittingColonsIntoIdentity() {
        val notification = Notification.Builder(context, "test")
            .setContentTitle("项目:讨论").setContentText("时间:明天确认").build()
        val result = NotificationReader.read(notification, "com.tencent.mm", "key", 100L)
        assertEquals("项目:讨论", result.title)
        assertEquals("时间:明天确认", result.text)
        assertNull(result.isGroupConversation)
    }

    @Test fun preservesGroupAndMessagingFlagsWithoutGuessingAnAccount() {
        val person = Person.Builder().setName("小王").build()
        val style = Notification.MessagingStyle(Person.Builder().setName("我").build())
            .setConversationTitle("项目组").setGroupConversation(true)
            .addMessage("明天再看", 100L, person)
        val notification = Notification.Builder(context, "test").setStyle(style)
            .setShortcutId("opaque-id").build()
        val result = NotificationReader.read(notification, "com.tencent.mobileqq", "key", 100L)
        assertTrue(result.hasMessagingStyle)
        assertEquals(true, result.isGroupConversation)
        assertTrue(result.hasShortcutId)
        assertTrue(result.text.contains("明天再看"))
    }

    @Test fun capsContentRetainedFromUntrustedNotifications() {
        val notification = Notification.Builder(context, "test")
            .setContentTitle("a".repeat(500)).setContentText("x".repeat(10000)).build()
        val result = NotificationReader.read(notification, "com.tencent.mm", "key", 100L)
        assertTrue(result.title.length <= 200)
        assertTrue(result.text.length <= 2000)
    }

    @Test @Config(sdk = [29]) fun readsBasicTextOnAndroid29() {
        val notification = Notification.Builder(context, "test")
            .setContentTitle("朋友").setContentText("下班了吗").build()
        val result = NotificationReader.read(notification, "com.tencent.mm", "key", 100L)
        assertEquals("下班了吗", result.text)
    }
}
