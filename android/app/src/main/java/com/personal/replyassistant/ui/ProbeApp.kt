package com.personal.replyassistant.ui

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.replyassistant.BuildConfig
import com.personal.replyassistant.App
import com.personal.replyassistant.capture.CaptureDiagnostics
import com.personal.replyassistant.capture.ChatNotificationListener
import kotlinx.coroutines.delay

@Composable
fun ProbeApp(store: CaptureDiagnostics, openReplySignal: Int = 0) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(primary = Color(0xFFA5D694)) else lightColorScheme(
        primary = Color(0xFF315F2F), onPrimary = Color.White,
        background = Color(0xFFFAFCF9), surface = Color(0xFFFAFCF9),
        onSurface = Color(0xFF182118), onSurfaceVariant = Color(0xFF485247),
    )
    MaterialTheme(colorScheme = colors) {
        val state by store.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val replies = remember { (context.applicationContext as App).replies }
        var selected by rememberSaveable { mutableIntStateOf(0) }
        LaunchedEffect(openReplySignal) { if (openReplySignal > 0) selected = 2 }
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(store) {
            while (true) {
                store.refresh(System.currentTimeMillis())
                replies.refresh(System.currentTimeMillis())
                tick++
                delay(1000)
            }
        }
        val access = remember(tick) {
            context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(
                ComponentName(context, ChatNotificationListener::class.java)
            )
        }
        Scaffold(bottomBar = {
            NavigationBar {
                listOf("接入", "记录", "生成", "设置").forEachIndexed { index, label ->
                    NavigationBarItem(selected = selected == index, onClick = { selected = index },
                        icon = { Text(listOf("◎", "≡", "✦", "⚙")[index]) }, label = { Text(label) })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("回话", style = MaterialTheme.typography.headlineMedium)
                Text("个人回复助手 · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (selected) {
                    0 -> {
                        Text("先确认，消息能被看见。", style = MaterialTheme.typography.titleLarge)
                        Text("QQ 通知正文已在本机初步验证。微信尚未测试；建议先只开启 QQ。")
                        StatusRow("通知使用权", if (access) "已授权" else "未授权")
                        StatusRow("监听服务", if (state.connected) "已连接" else "未连接")
                        StatusRow("接入状态", when {
                            !access -> "等待系统授权"
                            !state.options.running -> "已暂停"
                            !state.connected -> "等待服务连接"
                            else -> "正在记录所选应用"
                        })
                        Button(onClick = {
                            try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                            catch (_: android.content.ActivityNotFoundException) { toast(context, "请在系统设置中搜索通知使用权") }
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (access) "管理通知使用权" else "授权通知使用权") }
                        if (access && !state.connected) {
                            OutlinedButton(onClick = {
                                NotificationListenerService.requestRebind(ComponentName(context, ChatNotificationListener::class.java))
                                toast(context, "已请求系统重新连接")
                            }, modifier = Modifier.fillMaxWidth()) { Text("重新连接监听服务") }
                        }
                        HorizontalDivider()
                        Text("记录哪些应用", style = MaterialTheme.typography.titleMedium)
                        ToggleRow("微信", "只读取 com.tencent.mm 的通知", state.options.wechatEnabled) {
                            store.updateOptions(state.options.copy(wechatEnabled = it))
                        }
                        ToggleRow("QQ", "只读取 com.tencent.mobileqq 的通知", state.options.qqEnabled) {
                            store.updateOptions(state.options.copy(qqEnabled = it))
                        }
                        ToggleRow("开始接入记录", "开启即同意在本机临时展示所选应用的通知正文", state.options.running,
                            enabled = access && (state.options.wechatEnabled || state.options.qqEnabled)) {
                            store.updateOptions(state.options.copy(running = it))
                        }
                        Text("通知记录仅在本机内存保存，最多 50 条，10 分钟后清除。只有明确开启云端生成才会向所设模型地址发送选中的消息。",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    1 -> RecordsScreen(state.records, store) { record ->
                        replies.selectRecord(record)
                        selected = 2
                    }
                    2 -> ReplyScreen(replies, onSettings = { selected = 3 })
                    3 -> {
                        ModelSettingsScreen(replies)
                        HorizontalDivider()
                        Text("验证与数据", style = MaterialTheme.typography.titleLarge)
                        Text("诊断报告只有数量与字段存在情况，不包含消息正文、联系人姓名或实际会话标识。")
                        StatusRow("本地保留", "${state.records.size} / 50 条")
                        StatusRow("微信通知回调 / 可读", "${state.wechatCallbacks} / ${state.wechatReadable}")
                        StatusRow("QQ 通知回调 / 可读", "${state.qqCallbacks} / ${state.qqReadable}")
                        StatusRow("连接时历史通知", "${state.activeReplays} 条，单独统计")
                        StatusRow("解析异常", "${state.parseFailures} 次")
                        StatusRow("安卓版本", "${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
                        StatusRow("微信版本", installedVersion(context, "com.tencent.mm"))
                        StatusRow("QQ 版本", installedVersion(context, "com.tencent.mobileqq"))
                        OutlinedButton(onClick = {
                            val report = "设备：${Build.MANUFACTURER} ${Build.MODEL}\n" +
                                "Android：${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                                "微信：${installedVersion(context, "com.tencent.mm")}\n" +
                                "QQ：${installedVersion(context, "com.tencent.mobileqq")}\n" + store.diagnosticReport()
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("接入诊断", report))
                            toast(context, "已复制不含正文的诊断报告")
                        }, modifier = Modifier.fillMaxWidth()) { Text("复制诊断报告") }
                        OutlinedButton(onClick = { store.clear(); toast(context, "本地记录已清空") },
                            modifier = Modifier.fillMaxWidth()) { Text("清空记录") }
                        Text("退出进程后记录不会恢复。关闭任一应用开关会清空记录和原聊天入口。系统通知使用权可随时撤销。")
                        HorizontalDivider()
                        Text("下一步", style = MaterialTheme.typography.titleMedium)
                        Text("请分别测试后台单聊、前台聊天、锁屏、连续消息、同名联系人、群聊和图片语音。每个场景至少 5 次。")
                        Text("看见消息不代表已经识别联系人；通知中的会话标识还需要验证稳定性。",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable internal fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f))
    }
}

@Composable private fun ToggleRow(label: String, description: String, checked: Boolean,
    enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

private fun installedVersion(context: Context, packageName: String): String = try {
    @Suppress("DEPRECATION")
    context.packageManager.getPackageInfo(packageName, 0).versionName ?: "版本未知"
} catch (_: android.content.pm.PackageManager.NameNotFoundException) { "未安装或不可见" }

internal fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
