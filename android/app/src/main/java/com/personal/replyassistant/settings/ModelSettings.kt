package com.personal.replyassistant.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.personal.replyassistant.generation.ModelConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ModelSettingsState(
    val endpoint: String = "",
    val model: String = "",
    val cloudEnabled: Boolean = false,
    val autoForUnknownQq: Boolean = false,
    val hasKey: Boolean = false,
) {
    fun configOrNull(): ModelConfig? = try { ModelConfig(endpoint.trim(), model.trim()) }
        catch (_: Exception) { null }
}

class ModelSettings(context: Context) {
    private val prefs = context.getSharedPreferences("model_settings", Context.MODE_PRIVATE)
    private val secrets = SecretStore(context)
    private val mutable = MutableStateFlow(load())
    val state = mutable.asStateFlow()

    @Synchronized fun saveConfig(endpoint: String, model: String) {
        val valid = ModelConfig(endpoint.trim(), model.trim())
        prefs.edit().putString("endpoint", valid.endpoint).putString("model", valid.model).apply()
        mutable.value = mutable.value.copy(endpoint = valid.endpoint, model = valid.model)
    }

    @Synchronized fun saveKey(value: String) {
        require(value.isNotBlank() && value.length <= 2048)
        secrets.put(value.trim())
        mutable.value = mutable.value.copy(hasKey = true)
    }

    @Synchronized fun keyOrNull(): String? = secrets.get().also { value ->
        if (value == null && mutable.value.hasKey) mutable.value = mutable.value.copy(hasKey = false)
    }

    @Synchronized fun setCloudEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("cloud", enabled).putBoolean("auto_qq",
            if (enabled) mutable.value.autoForUnknownQq else false).apply()
        mutable.value = mutable.value.copy(cloudEnabled = enabled,
            autoForUnknownQq = if (enabled) mutable.value.autoForUnknownQq else false)
    }

    @Synchronized fun setAutoForUnknownQq(enabled: Boolean) {
        if (enabled && !mutable.value.cloudEnabled) return
        prefs.edit().putBoolean("auto_qq", enabled).apply()
        mutable.value = mutable.value.copy(autoForUnknownQq = enabled)
    }

    @Synchronized fun reserveRequest(): Boolean {
        val day = System.currentTimeMillis() / 86_400_000L
        val count = if (prefs.getLong("request_day", -1L) == day)
            prefs.getInt("request_count", 0) else 0
        if (count >= 100) return false
        return prefs.edit().putLong("request_day", day).putInt("request_count", count + 1).commit()
    }

    @Synchronized fun clearKey() {
        secrets.clear()
        setCloudEnabled(false)
        mutable.value = mutable.value.copy(hasKey = false)
    }

    private fun load() = ModelSettingsState(
        endpoint = prefs.getString("endpoint", "").orEmpty(),
        model = prefs.getString("model", "").orEmpty(),
        cloudEnabled = prefs.getBoolean("cloud", false),
        autoForUnknownQq = prefs.getBoolean("auto_qq", false),
        hasKey = secrets.get() != null,
    )
}

private class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("model_secret", Context.MODE_PRIVATE)
    private val alias = "reply_assistant_personal_key_v1"

    fun put(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP)).commit())
    }

    fun get(): String? {
        val iv = prefs.getString("iv", null) ?: return null
        val ciphertext = prefs.getString("ciphertext", null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun clear() { prefs.edit().clear().commit() }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
}
