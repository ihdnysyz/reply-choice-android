package com.personal.replyassistant.generation

import com.personal.replyassistant.capture.CaptureDiagnostics
import com.personal.replyassistant.capture.ProbeQuality
import com.personal.replyassistant.capture.ProbeRecord
import com.personal.replyassistant.settings.ModelSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class ReplyUiState(
    val message: String = "",
    val source: String = "手动粘贴",
    val sourceRecordId: Long? = null,
    val relationship: String = "未知",
    val userIntent: String = "",
    val loading: Boolean = false,
    val choices: List<ReplyChoice> = emptyList(),
    val error: String = "",
    val generatedAt: Long = 0L,
)

class ReplyController(
    val settings: ModelSettings,
    private val diagnostics: CaptureDiagnostics,
    private val client: ModelClient = CompatibleChatClient(),
    private val canNotify: () -> Boolean = { false },
    private val onAutomaticReady: () -> Unit = {},
    private val onSuggestionsCleared: () -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(ReplyUiState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var revision = 0L

    fun selectRecord(record: ProbeRecord) = scope.launch {
        if (record.result.quality !in setOf(ProbeQuality.TEXT, ProbeQuality.UNKNOWN)) return@launch
        invalidate()
        mutable.value = ReplyUiState(message = record.result.envelope.text.take(2000),
            source = "${record.result.sourceLabel} · 身份未确认", sourceRecordId = record.id)
    }

    fun onRecordCaptured(record: ProbeRecord) {
        val enabled = settings.state.value
        if (record.result.sourceLabel != "QQ" ||
            record.result.quality !in setOf(ProbeQuality.TEXT, ProbeQuality.UNKNOWN) ||
            !enabled.cloudEnabled || !enabled.autoForUnknownQq || !enabled.hasKey ||
            enabled.configOrNull() == null || !canNotify()) return
        scope.launch {
            selectRecord(record).join()
            generate(automatic = true)
        }
    }

    fun editMessage(value: String) {
        invalidate()
        mutable.value = mutable.value.copy(message = value.take(2000), source = "手动粘贴",
            sourceRecordId = null)
    }

    fun chooseRelationship(value: String) {
        invalidate()
        mutable.value = mutable.value.copy(relationship = value)
    }

    fun editIntent(value: String) {
        invalidate()
        mutable.value = mutable.value.copy(userIntent = value.take(300))
    }

    fun generate(automatic: Boolean = false) {
        val configState = settings.state.value
        if (!configState.cloudEnabled) return fail("请先在设置中同意云端生成")
        val config = configState.configOrNull() ?: return fail("请先设置 HTTPS 模型地址和型号")
        val key = settings.keyOrNull() ?: return fail("请先保存 API Key")
        val request = try {
            val current = mutable.value
            ReplyRequest(current.message.trim(), current.relationship, current.userIntent.trim())
        } catch (_: IllegalArgumentException) { return fail("请填写不超过 2000 字的文字消息") }
        if (!settings.reserveRequest()) return fail("今天已达到 100 次请求上限或本地计数保存失败")
        invalidate()
        val currentRevision = revision
        mutable.value = mutable.value.copy(loading = true, error = "")
        job = scope.launch {
            try {
                val replies = withTimeout(ModelNetworkPolicy.TOTAL_TIMEOUT_MILLIS) {
                    client.generate(config, key, request)
                }
                if (revision == currentRevision && settings.state.value.cloudEnabled) {
                    mutable.value = mutable.value.copy(loading = false, choices = replies,
                        generatedAt = System.currentTimeMillis())
                    if (automatic) onAutomaticReady()
                }
            } catch (_: TimeoutCancellationException) {
                if (revision == currentRevision) fail("模型响应超过 45 秒，请改用 DeepSeek Flash 或稍后重试")
            } catch (_: kotlinx.coroutines.CancellationException) {
                // A new message or user action invalidated this result.
            } catch (error: ModelFailure) {
                if (revision == currentRevision) fail(when (error.kind) {
                    ModelFailure.Kind.BAD_REQUEST -> "请求参数或模型名称不被接受，请核对模型设置"
                    ModelFailure.Kind.PAYMENT_REQUIRED -> "模型账户余额不足，请检查服务商账户"
                    ModelFailure.Kind.ENDPOINT -> "接口地址或路径不正确，请检查完整接口地址"
                    ModelFailure.Kind.CREDENTIALS -> "API Key 或访问权限有误"
                    ModelFailure.Kind.RATE_LIMIT -> "请求较多，请稍后再试"
                    ModelFailure.Kind.SERVER -> "模型服务暂不可用"
                    ModelFailure.Kind.CONNECT_TIMEOUT -> "连接模型服务超时，请检查手机网络"
                    ModelFailure.Kind.RESPONSE_TIMEOUT -> "模型等待响应超过 30 秒，请改用 DeepSeek Flash 或稍后重试"
                    ModelFailure.Kind.NETWORK -> "网络连接失败，请检查手机网络与接口地址"
                    ModelFailure.Kind.INVALID_RESPONSE -> "模型没有返回合格的三条回复"
                })
            } catch (_: Exception) {
                if (revision == currentRevision) fail("生成失败，请检查设置后重试")
            }
        }
    }

    fun pauseCloud() {
        settings.setCloudEnabled(false)
        invalidate()
        mutable.value = mutable.value.copy(error = "云端生成已关闭")
    }

    fun onCapturePaused() = scope.launch {
        if (mutable.value.sourceRecordId != null) clear()
    }

    fun clear() {
        invalidate()
        mutable.value = ReplyUiState()
    }

    fun refresh(nowMillis: Long) {
        val recordId = mutable.value.sourceRecordId
        if (recordId != null && diagnostics.state.value.records.none { it.id == recordId }) {
            clear()
            return
        }
        val generatedAt = mutable.value.generatedAt
        if (generatedAt != 0L && nowMillis - generatedAt >= 600_000L) {
            invalidate()
            mutable.value = mutable.value.copy(error = "建议已过期，请重新生成")
        }
    }

    fun sourceIntent() = mutable.value.sourceRecordId?.let {
        diagnostics.sourceIntent(it, System.currentTimeMillis())
    }

    private fun fail(message: String) {
        mutable.value = mutable.value.copy(loading = false, choices = emptyList(), error = message)
    }

    private fun invalidate() {
        revision++
        job?.cancel()
        job = null
        onSuggestionsCleared()
        mutable.value = mutable.value.copy(loading = false, choices = emptyList(), error = "", generatedAt = 0L)
    }
}
