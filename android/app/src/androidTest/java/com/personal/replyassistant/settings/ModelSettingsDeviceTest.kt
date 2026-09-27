package com.personal.replyassistant.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelSettingsDeviceTest {
    @Test fun keyIsEncryptedAndConsentStartsOff() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val isolated = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                target.getSharedPreferences("device_test_$name", mode)
        }
        val settingsPrefs = isolated.getSharedPreferences("model_settings", Context.MODE_PRIVATE)
        val secretPrefs = isolated.getSharedPreferences("model_secret", Context.MODE_PRIVATE)
        settingsPrefs.edit().clear().commit()
        secretPrefs.edit().clear().commit()
        try {
            val settings = ModelSettings(isolated)
            assertFalse(settings.state.value.cloudEnabled)
            assertFalse(settings.state.value.autoForUnknownQq)
            assertFalse(settings.state.value.hasKey)
            settings.saveKey("synthetic_device_test_key")
            assertEquals("synthetic_device_test_key", settings.keyOrNull())
            assertFalse(secretPrefs.all.values.joinToString().contains("synthetic_device_test_key"))
            settings.setCloudEnabled(true)
            settings.setAutoForUnknownQq(true)
            settings.setCloudEnabled(false)
            assertFalse(settings.state.value.autoForUnknownQq)
            settings.clearKey()
            assertNull(settings.keyOrNull())
        } finally {
            settingsPrefs.edit().clear().commit()
            secretPrefs.edit().clear().commit()
        }
    }
}
