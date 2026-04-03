package com.boshconnect.ui.settings

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
        val cloudAutoSync: Boolean = false,
        val fontScale: Float = 1.0f,
        val githubBackupEnabled: Boolean = false,
        val githubRepo: String = "",
        val githubToken: String = "",
        val backupPasswordHash: String = ""
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
            cloudAutoSync = prefs.getBoolean("cloud_auto_sync", false),
            fontScale = prefs.getFloat("font_scale", 1.0f),
            githubBackupEnabled = prefs.getBoolean("github_backup_enabled", false),
            githubRepo = prefs.getString("github_repo", "") ?: "",
            githubToken = prefs.getString("github_token", "") ?: "",
            backupPasswordHash = prefs.getString("backup_password_hash", "") ?: ""
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

    fun setAutoSync(enabled: Boolean) {
        prefs.edit().putBoolean("cloud_auto_sync", enabled).apply()
        _settings.value = _settings.value.copy(cloudAutoSync = enabled)
    }

    fun getCloudToken(): String? = prefs.getString("cloud_token", null)
    fun setCloudToken(token: String?) {
        prefs.edit().putString("cloud_token", token).apply()
    }
    fun clearCloudToken() {
        prefs.edit().remove("cloud_token").apply()
    }

    // Track which local VPS IDs were synced to cloud (for detecting deletions)
    fun getLastSyncedVpsIds(): Set<String> {
        val raw = prefs.getString("last_synced_vps_ids", "") ?: ""
        return if (raw.isBlank()) emptySet() else raw.split(",").toSet()
    }
    fun setLastSyncedVpsIds(ids: Set<String>) {
        prefs.edit().putString("last_synced_vps_ids", ids.joinToString(",")).apply()
    }

    // GitHub backup
    fun setGitHubBackup(enabled: Boolean, repo: String = "", token: String = "") {
        prefs.edit()
            .putBoolean("github_backup_enabled", enabled)
            .putString("github_repo", repo)
            .putString("github_token", token)
            .apply()
        _settings.value = _settings.value.copy(
            githubBackupEnabled = enabled,
            githubRepo = repo,
            githubToken = token
        )
    }

    // Backup password (stored as PBKDF2 hash for verification)
    fun setBackupPasswordHash(hash: String) {
        prefs.edit().putString("backup_password_hash", hash).apply()
        _settings.value = _settings.value.copy(backupPasswordHash = hash)
    }

    fun isBackupPasswordSet(): Boolean {
        return prefs.getString("backup_password_hash", null)?.isNotBlank() == true
    }

    fun clearBackupPassword() {
        prefs.edit().remove("backup_password_hash").apply()
        _settings.value = _settings.value.copy(backupPasswordHash = "")
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
