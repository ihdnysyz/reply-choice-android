package com.personal.replyassistant.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.replyassistant.generation.ReplyController

@Composable
fun ModelSettingsScreen(controller: ReplyController) {
    val settings by controller.settings.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.settings.setAutoForUnknownQq(true)
        else toast(context, "未授权建议通知，自动生成未开启")
    }
    var endpoint by remember { mutableStateOf(settings.endpoint) }
    var model by remember { mutableStateOf(settings.model) }
    var apiKey by remember { mutableStateOf("") }
    Text("模型设置", style = MaterialTheme.typography.titleLarge)
    Text("填写支持 Chat Completions 请求格式的完整 HTTPS 接口地址。只连接你自己选择的服务商。",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(value = endpoint, onValueChange = { endpoint = it },
        label = { Text("接口地址") }, placeholder = { Text("https://example.com/v1/chat/completions") },
        modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(value = model, onValueChange = { model = it },
        label = { Text("精确模型 ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedButton(onClick = {
        try {
            controller.settings.saveConfig(endpoint, model)
            toast(context, "模型地址与型号已保存")
        } catch (_: Exception) { toast(context, "请输入有效的 HTTPS 接口地址和模型 ID") }
    }) { Text("保存地址与型号") }
    Text("当前接收域名：${settings.configOrNull()?.host ?: "未配置"}",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (settings.configOrNull()?.let { it.host == "api.deepseek.com" && !it.hasKnownDeepSeekPath } == true) {
        Text("已保存的 DeepSeek 地址缺少官方聊天接口路径，可能导致请求失败。",
            color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = {
            endpoint = "https://api.deepseek.com/chat/completions"
            try {
                controller.settings.saveConfig(endpoint, model)
                toast(context, "已改为 DeepSeek 官方聊天接口")
            } catch (_: Exception) { toast(context, "请先填写模型 ID") }
        }) { Text("使用 DeepSeek 官方接口") }
    }
    if (settings.configOrNull()?.host == "api.deepseek.com" &&
        (settings.model != "deepseek-flash" || settings.configOrNull()?.hasKnownDeepSeekPath != true)) {
        OutlinedButton(onClick = {
            endpoint = "https://api.deepseek.com/chat/completions"
            model = "deepseek-flash"
            controller.settings.saveConfig(endpoint, model)
            toast(context, "已切换 DeepSeek Flash，原 API Key 保留")
        }) { Text("切换 DeepSeek Flash（更快）") }
    }
    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
        label = { Text(if (settings.hasKey) "API Key 已保存；输入新值可替换" else "API Key") },
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = {
            try {
                controller.settings.saveKey(apiKey)
                apiKey = ""
                toast(context, "API Key 已加密保存")
            } catch (_: Exception) { toast(context, "保存失败，请检查 API Key") }
        }, enabled = apiKey.isNotBlank()) { Text("保存 Key") }
        TextButton(onClick = { controller.settings.clearKey(); controller.clear(); toast(context, "Key 已删除") },
            enabled = settings.hasKey) { Text("删除 Key") }
    }
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text("同意云端生成", style = MaterialTheme.typography.titleMedium)
            Text("生成时将当前消息、所选关系和你的补充想法发给上述域名；默认关闭。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = settings.cloudEnabled, onCheckedChange = { enabled ->
            if (enabled) controller.settings.setCloudEnabled(true) else controller.pauseCloud()
        }, enabled = !settings.cloudEnabled && settings.hasKey && settings.configOrNull() != null || settings.cloudEnabled)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text("QQ 新通知自动生成", style = MaterialTheme.typography.titleMedium)
            Text("QQ 尚无可靠联系人标识。自动生成只用本条消息和“未知关系”，不带历史；可能包含群聊通知。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = settings.autoForUnknownQq, onCheckedChange = { enabled ->
            if (enabled && Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else controller.settings.setAutoForUnknownQq(enabled)
        },
            enabled = settings.cloudEnabled)
    }
    Text("消息和建议只在本机内存暂存 10 分钟。模型服务商对已收到请求的处理以其条款为准。",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
