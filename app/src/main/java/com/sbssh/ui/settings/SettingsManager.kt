package com.sbssh.ui.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sbssh_settings", Context.MODE_PRIVATE)

    data class Settings(
        val language: String = "zh",
        val fontSize: String = "medium",
        val cloudSyncEnabled: Boolean = false,
        val cloudSyncUrl: String = "",
        val cloudSyncUsername: String = "",
        val fontScale: Float = 1.0f
    )

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun loadSettings(): Settings {
        return Settings(
            language = prefs.getString("language", "zh") ?: "zh",
            fontSize = prefs.getString("font_size", "medium") ?: "medium",
            cloudSyncEnabled = prefs.getBoolean("cloud_sync_enabled", false),
            cloudSyncUrl = prefs.getString("cloud_sync_url", "") ?: "",
            cloudSyncUsername = prefs.getString("cloud_sync_username", "") ?: "",
            fontScale = prefs.getFloat("font_scale", 1.0f)
        )
    }

    fun setLanguage(lang: String) {
        prefs.edit().putString("language", lang).apply()
        _settings.value = _settings.value.copy(language = lang)
    }

    fun setFontSize(size: String) {
        val scale = when (size) {
            "small" -> 0.85f
            "medium" -> 1.0f
            "large" -> 1.15f
            else -> 1.0f
        }
        prefs.edit().putString("font_size", size).putFloat("font_scale", scale).apply()
        _settings.value = _settings.value.copy(fontSize = size, fontScale = scale)
    }

    fun setCloudSync(enabled: Boolean, url: String = "", username: String = "") {
        prefs.edit()
            .putBoolean("cloud_sync_enabled", enabled)
            .putString("cloud_sync_url", url)
            .putString("cloud_sync_username", username)
            .apply()
        _settings.value = _settings.value.copy(
            cloudSyncEnabled = enabled,
            cloudSyncUrl = url,
            cloudSyncUsername = username
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SettingsManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
