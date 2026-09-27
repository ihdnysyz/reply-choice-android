package com.personal.replyassistant.capture

class ProbePolicy {
    fun inspect(envelope: NotificationEnvelope, options: ProbeOptions, nowMillis: Long): ProbeResult? {
        if (!options.running) return null
        val source = when (envelope.packageName) {
            "com.tencent.mm" -> if (options.wechatEnabled) "微信" else return null
            "com.tencent.mobileqq" -> if (options.qqEnabled) "QQ" else return null
            else -> return null
        }
        val quality = when {
            envelope.isGroupSummary || envelope.isGroupConversation == true -> ProbeQuality.GROUP
            envelope.text.isBlank() -> ProbeQuality.EMPTY
            envelope.text in setOf("[图片]", "[语音]", "[视频]", "[文件]") -> ProbeQuality.MEDIA
            nowMillis - envelope.postedAt > 60_000 || envelope.postedAt - nowMillis > 5_000 -> ProbeQuality.STALE
            envelope.isGroupConversation == null -> ProbeQuality.UNKNOWN
            else -> ProbeQuality.TEXT
        }
        return ProbeResult(source, quality, envelope)
    }
}

class ProbeHistory {
    private val records = mutableListOf<ProbeRecord>()
    private var nextId = 1L

    @Synchronized
    fun add(result: ProbeResult, nowMillis: Long) {
        prune(nowMillis)
        val incoming = result.envelope
        if (records.any {
            val existing = it.result.envelope
            existing.packageName == incoming.packageName && existing.key == incoming.key &&
                existing.title == incoming.title &&
                existing.text == incoming.text && existing.postedAt == incoming.postedAt
        }) return
        records.add(0, ProbeRecord(nextId++, result, nowMillis))
        if (records.size > 50) records.removeAt(records.lastIndex)
    }

    @Synchronized
    fun snapshot(nowMillis: Long): List<ProbeRecord> {
        prune(nowMillis)
        return records.toList()
    }

    @Synchronized
    fun clear() {
        records.clear()
    }

    private fun prune(nowMillis: Long) {
        records.removeAll { nowMillis - it.receivedAt >= 600_000 }
    }
}
