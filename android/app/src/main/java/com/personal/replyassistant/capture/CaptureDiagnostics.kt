package com.personal.replyassistant.capture

import android.content.Context
import android.app.PendingIntent
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProbeState(
    val options: ProbeOptions = ProbeOptions(),
    val connected: Boolean = false,
    val records: List<ProbeRecord> = emptyList(),
    val parseFailures: Int = 0,
    val wechatCallbacks: Int = 0,
    val qqCallbacks: Int = 0,
    val wechatReadable: Int = 0,
    val qqReadable: Int = 0,
    val activeReplays: Int = 0,
)

class CaptureDiagnostics(context: Context, private val nowMillis: () -> Long = System::currentTimeMillis) {
    var onNewRecord: ((ProbeRecord) -> Unit)? = null
    var onCapturePaused: (() -> Unit)? = null
    private val policy = ProbePolicy()
    private val history = ProbeHistory()
    private val sourceIntents = mutableMapOf<Long, PendingIntent>()
    private val handler = Handler(Looper.getMainLooper())
    private var expiryRunnable: Runnable? = null
    private val mutable = MutableStateFlow(ProbeState())
    val state = mutable.asStateFlow()

    @Synchronized fun updateOptions(options: ProbeOptions) {
        val before = mutable.value.options
        if (before.running && (!options.running ||
            (before.qqEnabled && !options.qqEnabled) ||
            (before.wechatEnabled && !options.wechatEnabled))) onCapturePaused?.invoke()
        if ((before.wechatEnabled && !options.wechatEnabled) || (before.qqEnabled && !options.qqEnabled)) {
            history.clear()
            sourceIntents.clear()
            cancelExpiry()
        }
        mutable.value = mutable.value.copy(options = options, records = history.snapshot(nowMillis()))
    }

    @Synchronized fun capture(envelope: NotificationEnvelope, pendingIntent: PendingIntent?, nowMillis: Long, replay: Boolean = false) {
        val result = policy.inspect(envelope, mutable.value.options, nowMillis) ?: return
        history.add(result, nowMillis)
        val records = history.snapshot(nowMillis)
        val record = records.firstOrNull { it.result === result }
        if (record != null && pendingIntent != null) sourceIntents[record.id] = pendingIntent
        sourceIntents.keys.retainAll(records.map { it.id }.toSet())
        var next = mutable.value.copy(records = records)
        if (!replay && envelope.text.isNotBlank() && result.quality != ProbeQuality.MEDIA &&
            result.quality != ProbeQuality.GROUP) {
            next = when (envelope.packageName) {
                "com.tencent.mm" -> next.copy(wechatReadable = next.wechatReadable + 1)
                "com.tencent.mobileqq" -> next.copy(qqReadable = next.qqReadable + 1)
                else -> next
            }
        }
        mutable.value = next
        scheduleExpiry(nowMillis)
        if (!replay && record != null) onNewRecord?.invoke(record)
    }

    @Synchronized fun recordNotificationCallback(packageName: String, replay: Boolean) {
        val options = mutable.value.options
        if (!options.running) return
        if (replay) {
            mutable.value = mutable.value.copy(activeReplays = mutable.value.activeReplays + 1)
        } else when (packageName) {
            "com.tencent.mm" -> if (options.wechatEnabled) {
                mutable.value = mutable.value.copy(wechatCallbacks = mutable.value.wechatCallbacks + 1)
            }
            "com.tencent.mobileqq" -> if (options.qqEnabled) {
                mutable.value = mutable.value.copy(qqCallbacks = mutable.value.qqCallbacks + 1)
            }
        }
    }

    @Synchronized fun clear() {
        onCapturePaused?.invoke()
        history.clear()
        sourceIntents.clear()
        cancelExpiry()
        mutable.value = mutable.value.copy(records = emptyList(), parseFailures = 0,
            wechatCallbacks = 0, qqCallbacks = 0, wechatReadable = 0, qqReadable = 0, activeReplays = 0)
    }

    @Synchronized fun refresh(nowMillis: Long) {
        val records = history.snapshot(nowMillis)
        sourceIntents.keys.retainAll(records.map { it.id }.toSet())
        if (records != mutable.value.records) {
            mutable.value = mutable.value.copy(records = records)
            scheduleExpiry(nowMillis)
        }
    }

    @Synchronized fun setConnected(connected: Boolean) {
        mutable.value = mutable.value.copy(connected = connected)
    }

    @Synchronized fun recordParseFailure() {
        mutable.value = mutable.value.copy(parseFailures = mutable.value.parseFailures + 1)
    }

    @Synchronized fun sourceIntent(recordId: Long, nowMillis: Long): PendingIntent? {
        refresh(nowMillis)
        return sourceIntents[recordId]
    }

    @Synchronized fun diagnosticReport(): String {
        refresh(nowMillis())
        val state = mutable.value
        val bySource = state.records.groupingBy { it.result.sourceLabel }.eachCount()
        val byQuality = state.records.groupingBy { it.result.quality.name }.eachCount()
        val fields = state.records.map { it.result.envelope }
        return buildString {
            appendLine("接入：${if (state.options.running) "开启" else "暂停"}，监听：${if (state.connected) "已连接" else "未连接"}")
            appendLine("微信：${bySource["微信"] ?: 0}，QQ：${bySource["QQ"] ?: 0}，解析异常：${state.parseFailures}")
            appendLine("微信回调=${state.wechatCallbacks}，微信可读=${state.wechatReadable}，" +
                "QQ回调=${state.qqCallbacks}，QQ可读=${state.qqReadable}，历史重放=${state.activeReplays}")
            appendLine("状态计数：${byQuality.toSortedMap().entries.joinToString { "${it.key}=${it.value}" }}")
            append("字段存在：结构化=${fields.count { it.hasMessagingStyle }}，会话标识=${fields.count { it.hasShortcutId }}，")
            appendLine("原聊天入口=${fields.count { it.hasContentIntent }}，回复动作=${fields.count { it.hasRemoteInput }}")
            for (source in listOf("微信", "QQ")) {
                val envelopes = state.records.filter { it.result.sourceLabel == source }.map { it.result.envelope }
                val sameTitleManyKeys = envelopes.groupBy { it.title }
                    .count { (_, group) -> group.map { it.key }.distinct().size > 1 }
                val sameKeyManyTitles = envelopes.groupBy { it.key }
                    .count { (_, group) -> group.map { it.title }.distinct().size > 1 }
                appendLine("${source}通知键种类=${envelopes.map { it.key }.distinct().size}，" +
                    "${source}标题种类=${envelopes.map { it.title }.distinct().size}，" +
                    "同标题多键=$sameTitleManyKeys，单键跨标题=$sameKeyManyTitles")
            }
        }
    }

    private fun cancelExpiry() {
        expiryRunnable?.let(handler::removeCallbacks)
        expiryRunnable = null
    }

    private fun scheduleExpiry(currentMillis: Long) {
        cancelExpiry()
        val firstExpiry = mutable.value.records.minOfOrNull { it.receivedAt + 600_000L } ?: return
        val action = Runnable {
            synchronized(this) {
                expiryRunnable = null
                refresh(nowMillis())
            }
        }
        expiryRunnable = action
        handler.postDelayed(action, (firstExpiry - currentMillis).coerceAtLeast(0L))
    }
}
