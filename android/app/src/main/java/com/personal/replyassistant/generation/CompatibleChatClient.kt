package com.personal.replyassistant.generation

import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ModelConfig(val endpoint: String, val model: String) {
    init {
        val url = URL(endpoint)
        require(url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null &&
            url.query == null && url.ref == null && url.port in -1..65535) { "模型地址必须是 HTTPS 完整接口地址" }
        require(model.isNotBlank() && model.length <= 120)
    }
    val host: String get() = URL(endpoint).host
    val hasKnownDeepSeekPath: Boolean get() = URL(endpoint).path.trimEnd('/') in
        setOf("/chat/completions", "/v1/chat/completions")
}

class ModelFailure(val kind: Kind) : Exception(kind.name) {
    enum class Kind { BAD_REQUEST, PAYMENT_REQUIRED, ENDPOINT, CREDENTIALS, RATE_LIMIT, SERVER,
        CONNECT_TIMEOUT, RESPONSE_TIMEOUT, NETWORK, INVALID_RESPONSE }
    companion object {
        fun fromHttpStatus(status: Int): ModelFailure? {
            val kind = when {
                status in 200..299 -> return null
                status == 401 || status == 403 -> Kind.CREDENTIALS
                status == 402 -> Kind.PAYMENT_REQUIRED
                status == 429 -> Kind.RATE_LIMIT
                status in 300..399 || status == 404 || status == 405 -> Kind.ENDPOINT
                status in 500..599 -> Kind.SERVER
                else -> Kind.BAD_REQUEST
            }
            return ModelFailure(kind)
        }
    }
}

interface ModelClient {
    suspend fun generate(config: ModelConfig, apiKey: String, request: ReplyRequest): List<ReplyChoice>
}

object ModelNetworkPolicy {
    const val CONNECT_TIMEOUT_MILLIS = 10_000
    const val READ_TIMEOUT_MILLIS = 30_000
    const val TOTAL_TIMEOUT_MILLIS = 45_000L
}

object ChatRequestBody {
    fun build(config: ModelConfig, request: ReplyRequest): JSONObject {
        val messages = JSONArray().apply {
            PromptBuilder.messages(request).forEach {
                put(JSONObject().put("role", it.role).put("content", it.content))
            }
        }
        return JSONObject().put("model", config.model).put("messages", messages)
            .put("temperature", 0.7).apply {
                if (config.host == "api.deepseek.com" && config.hasKnownDeepSeekPath) {
                    put("thinking", JSONObject().put("type", "disabled"))
                    put("response_format", JSONObject().put("type", "json_object"))
                    put("max_tokens", 700)
                }
            }
    }
}

class CompatibleChatClient : ModelClient {
    companion object {
        private val network = Executors.newFixedThreadPool(2) { action ->
            Thread(action, "reply-model-network").apply { isDaemon = true }
        }
    }

    override suspend fun generate(config: ModelConfig, apiKey: String, request: ReplyRequest): List<ReplyChoice> {
        require(apiKey.isNotBlank())
        val connection = (URL(config.endpoint).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = ModelNetworkPolicy.CONNECT_TIMEOUT_MILLIS
                readTimeout = ModelNetworkPolicy.READ_TIMEOUT_MILLIS
                instanceFollowRedirects = false
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
        }
        return suspendCancellableCoroutine { continuation ->
            val work = network.submit {
                try { continuation.resume(execute(connection, config, request)) }
                catch (error: Exception) { continuation.resumeWithException(error) }
            }
            continuation.invokeOnCancellation {
                connection.disconnect()
                work.cancel(true)
            }
        }
    }

    private fun execute(connection: HttpsURLConnection, config: ModelConfig,
        request: ReplyRequest): List<ReplyChoice> {
            var waitingForResponse = false
            return try {
                val body = ChatRequestBody.build(config, request).toString().toByteArray(Charsets.UTF_8)
                connection.outputStream.use { it.write(body) }
                waitingForResponse = true
                val code = connection.responseCode
                ModelFailure.fromHttpStatus(code)?.let { throw it }
                val response = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > 65_536) throw ModelFailure(ModelFailure.Kind.INVALID_RESPONSE)
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                }
                val content = try {
                    JSONObject(response).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content")
                } catch (_: RuntimeException) { throw ModelFailure(ModelFailure.Kind.INVALID_RESPONSE) }
                try { ReplyValidator.parse(content) }
                catch (_: RuntimeException) { throw ModelFailure(ModelFailure.Kind.INVALID_RESPONSE) }
            } catch (error: ModelFailure) {
                throw error
            } catch (_: SocketTimeoutException) {
                throw ModelFailure(if (waitingForResponse) ModelFailure.Kind.RESPONSE_TIMEOUT
                    else ModelFailure.Kind.CONNECT_TIMEOUT)
            } catch (_: java.io.IOException) {
                throw ModelFailure(ModelFailure.Kind.NETWORK)
            } finally {
                connection.disconnect()
            }
    }
}
