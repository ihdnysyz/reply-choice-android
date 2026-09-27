package com.personal.replyassistant.capture

/** Notification fields and presence flags only; none establishes a verified person identity. */
data class NotificationEnvelope(
    val packageName: String,
    val key: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val isGroupSummary: Boolean = false,
    val isGroupConversation: Boolean? = null,
    val hasMessagingStyle: Boolean = false,
    val hasShortcutId: Boolean = false,
    val hasContentIntent: Boolean = false,
    val hasRemoteInput: Boolean = false,
)

data class ProbeOptions(
    val running: Boolean = false,
    val wechatEnabled: Boolean = false,
    val qqEnabled: Boolean = false,
)

enum class ProbeQuality { TEXT, EMPTY, MEDIA, GROUP, UNKNOWN, STALE }

data class ProbeResult(
    val sourceLabel: String,
    val quality: ProbeQuality,
    val envelope: NotificationEnvelope,
)

data class ProbeRecord(val id: Long, val result: ProbeResult, val receivedAt: Long)
