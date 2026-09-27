package com.personal.replyassistant.ui

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.replyassistant.generation.ReplyController

private val relationships = listOf("未知", "家人", "伴侣", "朋友", "同事", "上级", "客户", "其他")

@Composable
fun ReplyScreen(controller: ReplyController, onSettings: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val modelSettings by controller.settings.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var relationshipMenu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("三条回复，选一条再发送", style = MaterialTheme.typography.titleLarge)
        Text("来源：${state.source}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.sourceRecordId != null) {
            Text("通知没有可靠联系人标识，请自行确认这是一条单聊消息。关系只用于本次生成。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(value = state.message, onValueChange = controller::editMessage,
            label = { Text("对方发来的文字") }, modifier = Modifier.fillMaxWidth(), minLines = 3,
            supportingText = { Text("可从记录页选一条通知，或在这里粘贴；最多 2000 字。") })
        Box {
            OutlinedButton(onClick = { relationshipMenu = true }) { Text("本次关系：${state.relationship}") }
            DropdownMenu(expanded = relationshipMenu, onDismissRequest = { relationshipMenu = false }) {
                relationships.forEach { value ->
                    DropdownMenuItem(text = { Text(value) }, onClick = {
                        controller.chooseRelationship(value)
                        relationshipMenu = false
                    })
                }
            }
        }
        OutlinedTextField(value = state.userIntent, onValueChange = controller::editIntent,
            label = { Text("我的想法（可选）") }, modifier = Modifier.fillMaxWidth(), maxLines = 3,
            placeholder = { Text("例如：不想去，但想礼貌回复") })
        if (!modelSettings.cloudEnabled) {
            Text("云端生成未开启。配置模型并明确同意后，才会发送消息给模型。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onSettings) { Text("去模型设置") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { controller.generate() }, enabled = !state.loading && state.message.isNotBlank()) {
                Text(if (state.loading) "生成中…" else "生成三条回复")
            }
            OutlinedButton(onClick = controller::clear) { Text("清空") }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.error.isNotBlank()) Text(state.error, color = MaterialTheme.colorScheme.error)
        if (state.choices.isNotEmpty()) {
            Text("按你的实际情况选择；复制不代表已发送。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.choices.forEachIndexed { index, choice ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${index + 1} · ${choice.intent}", style = MaterialTheme.typography.titleMedium)
                        Text(choice.text, style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { copyReply(context, choice.text) }) { Text("复制这条") }
                    }
                }
            }
        }
        if (state.sourceRecordId != null) {
            OutlinedButton(onClick = {
                val action = controller.sourceIntent()
                if (action == null) toast(context, "通知入口已失效，请手动打开原聊天")
                else try { action.send() }
                catch (_: PendingIntent.CanceledException) { toast(context, "通知入口已失效，请手动打开原聊天") }
                catch (_: SecurityException) { toast(context, "系统未允许打开，请手动打开原聊天") }
            }) { Text("尝试打开原聊天") }
        }
    }
}

private fun copyReply(context: Context, value: String) {
    val clip = ClipData.newPlainText("回复草稿", value)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    toast(context, "已复制，请到原聊天核对后发送")
}
