package com.personal.replyassistant.capture

import org.junit.Assert.*
import org.junit.Test

class ProbePolicyTest {
    private val now = 1_000_000L
    private val policy = ProbePolicy()
    private val enabled = ProbeOptions(running = true, wechatEnabled = true, qqEnabled = true)
    private fun envelope() = NotificationEnvelope(
        packageName = "com.tencent.mm", key = "notification-1", title = "联系人",
        text = "你好", postedAt = now, isGroupConversation = false,
    )
    private fun result(envelope: NotificationEnvelope = envelope()) =
        requireNotNull(policy.inspect(envelope, enabled, now))

    @Test fun ignoresUnrelatedApps() {
        assertNull(policy.inspect(envelope().copy(packageName = "com.example.other"), enabled, now))
    }

    @Test fun defaultsToPausedAndIgnoresPausedCapture() {
        assertNull(policy.inspect(envelope(), ProbeOptions(), now))
        assertNull(policy.inspect(envelope(), enabled.copy(running = false), now))
    }

    @Test fun sourceTogglesAreIndependent() {
        val wechatOnly = enabled.copy(qqEnabled = false)
        val qqOnly = enabled.copy(wechatEnabled = false)
        val qq = envelope().copy(packageName = "com.tencent.mobileqq")
        assertEquals("微信", policy.inspect(envelope(), wechatOnly, now)?.sourceLabel)
        assertNull(policy.inspect(qq, wechatOnly, now))
        assertNull(policy.inspect(envelope(), qqOnly, now))
        assertEquals("QQ", policy.inspect(qq, qqOnly, now)?.sourceLabel)
    }

    @Test fun readableExplicitNonGroupIsTextAndPreservesEnvelope() {
        val input = envelope().copy(hasShortcutId = true, hasContentIntent = true, hasRemoteInput = true)
        assertEquals(ProbeQuality.TEXT, result(input).quality)
        assertSame(input, result(input).envelope)
    }

    @Test fun blankBodyIsEmptyEvenWhenTitleExists() {
        assertEquals(ProbeQuality.EMPTY, result(envelope().copy(title = "", text = "")).quality)
        assertEquals(ProbeQuality.EMPTY, result(envelope().copy(text = " \n")).quality)
    }

    @Test fun exactMediaPlaceholdersAreMedia() {
        listOf("[图片]", "[语音]", "[视频]", "[文件]").forEach {
            assertEquals(ProbeQuality.MEDIA, result(envelope().copy(text = it)).quality)
        }
        assertEquals(ProbeQuality.TEXT, result(envelope().copy(text = "说明[图片]")).quality)
    }

    @Test fun groupFlagsTakePriorityOverUnreadableContent() {
        assertEquals(ProbeQuality.GROUP, result(envelope().copy(isGroupSummary = true, text = "")).quality)
        assertEquals(ProbeQuality.GROUP, result(envelope().copy(isGroupConversation = true)).quality)
    }

    @Test fun missingGroupMetadataRemainsUnknownDespiteRichFields() {
        assertEquals(ProbeQuality.UNKNOWN, result(envelope().copy(
            isGroupConversation = null, hasMessagingStyle = true, hasShortcutId = true,
            hasContentIntent = true, hasRemoteInput = true,
        )).quality)
    }

    @Test fun oldOrTooFarFutureNotificationsAreStale() {
        assertEquals(ProbeQuality.STALE, result(envelope().copy(postedAt = now - 60_001)).quality)
        assertEquals(ProbeQuality.STALE, result(envelope().copy(postedAt = now + 5_001)).quality)
        assertEquals(ProbeQuality.TEXT, result(envelope().copy(postedAt = now - 60_000)).quality)
        assertEquals(ProbeQuality.TEXT, result(envelope().copy(postedAt = now + 5_000)).quality)
    }

    @Test fun historyExpiresFromReceiptRatherThanPostedTime() {
        val history = ProbeHistory()
        history.add(result(envelope().copy(postedAt = 0)), now)
        assertEquals(1, history.snapshot(now + 599_999).size)
        assertTrue(history.snapshot(now + 600_000).isEmpty())
    }

    @Test fun duplicateCallbacksAreCollapsedButRealRepeatsRemain() {
        val history = ProbeHistory()
        val first = result()
        history.add(first, now)
        history.add(first, now + 1)
        history.add(result(envelope().copy(postedAt = now + 2)), now + 2)
        val records = history.snapshot(now + 2)
        assertEquals(2, records.size)
        assertEquals(now + 2, records.first().receivedAt)
        assertEquals(now, records.last().receivedAt)
        assertNotEquals(records.first().id, records.last().id)
    }

    @Test fun duplicateCallbackDoesNotExtendRetention() {
        val history = ProbeHistory()
        history.add(result(), now)
        history.add(result(envelope().copy(hasRemoteInput = true)), now + 599_999)
        assertTrue(history.snapshot(now + 600_000).isEmpty())
    }

    @Test fun updatedNotificationContentIsRetained() {
        val history = ProbeHistory()
        history.add(result(), now)
        history.add(result(envelope().copy(text = "再见")), now + 1)
        history.add(result(envelope().copy(title = "另一个联系人")), now + 2)
        history.add(result(envelope().copy(key = "notification-2")), now + 3)
        assertEquals(4, history.snapshot(now + 3).size)
    }

    @Test fun historyKeepsOnlyLatestFiftyRecords() {
        val history = ProbeHistory()
        repeat(55) { history.add(result(envelope().copy(postedAt = now + it)), now + it) }
        val snapshot = history.snapshot(now + 55)
        assertEquals(50, snapshot.size)
        assertEquals(now + 54, snapshot.first().receivedAt)
        assertEquals(now + 5, snapshot.last().receivedAt)
    }

    @Test fun addingPrunesExpiredRecordsBeforeDuplicateCheck() {
        val history = ProbeHistory()
        history.add(result(), now)
        history.add(result(), now + 600_000)
        assertEquals(now + 600_000, history.snapshot(now + 600_000).single().receivedAt)
    }

    @Test fun clearRemovesHistoryAndSnapshotsDoNotChangeRetroactively() {
        val history = ProbeHistory()
        history.add(result(), now)
        val priorSnapshot = history.snapshot(now)
        history.clear()
        assertTrue(history.snapshot(now).isEmpty())
        assertEquals(1, priorSnapshot.size)
    }
}
