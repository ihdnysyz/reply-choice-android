package com.personal.replyassistant.ui

import android.app.PendingIntent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personal.replyassistant.capture.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordsScreen(records: List<ProbeRecord>, store: CaptureDiagnostics, onSelect: (ProbeRecord) -> Unit = {}) {
    val context = LocalContext.current
    Text("通知记录", style = MaterialTheme.typography.titleLarge)
    Text("仅显示所选应用的通知。正文可读不等于身份已确认。",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (records.isEmpty()) {
        HorizontalDivider()
        Text("还没有记录", style = MaterialTheme.typography.titleMedium)
        Text("在接入页开启应用与记录开关，再让测试账号发一条虚构消息。记录最多保留 10 分钟。")
    }
    records.forEach { record ->
        val item = record.result
        val envelope = item.envelope
        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${item.sourceLabel} · ${SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(record.receivedAt))}",
                style = MaterialTheme.typography.labelLarge)
            Text(envelope.title.ifBlank { "未提供通知标题" }, style = MaterialTheme.typography.titleMedium)
            Text(envelope.text.ifBlank { "未读取到文字正文" })
            Text(when(item.quality) {
                ProbeQuality.TEXT -> "文字可读 · 单聊标记存在 · 身份未验证"
                ProbeQuality.EMPTY -> "正文为空或不可见"
                ProbeQuality.MEDIA -> "媒体占位 · 不是实际内容"
                ProbeQuality.GROUP -> "群聊或汇总 · 不作为单聊处理"
                ProbeQuality.UNKNOWN -> "文字可读 · 单聊 / 群聊来源未确认"
                ProbeQuality.STALE -> "超过接入时限 · 不用于即时回复"
            }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            Text("结构化消息：${yes(envelope.hasMessagingStyle)}　会话标识：${yes(envelope.hasShortcutId)}\n" +
                "原聊天入口：${yes(envelope.hasContentIntent)}　回复动作：${yes(envelope.hasRemoteInput)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.quality == ProbeQuality.TEXT || item.quality == ProbeQuality.UNKNOWN) {
                Button(onClick = { onSelect(record) }) { Text("用这条消息生成回复") }
            }
            if (envelope.hasContentIntent) {
                OutlinedButton(onClick = {
                    val action = store.sourceIntent(record.id, System.currentTimeMillis())
                    if (action == null) toast(context, "入口已失效，请自行打开原聊天")
                    else try { action.send() } catch (_: PendingIntent.CanceledException) {
                        toast(context, "入口已失效，请自行打开原聊天")
                    } catch (_: SecurityException) { toast(context, "系统未允许打开，请自行进入原聊天") }
                }) { Text("尝试打开来源通知") }
            }
        }
    }
}

private fun yes(value: Boolean) = if (value) "有" else "无"
