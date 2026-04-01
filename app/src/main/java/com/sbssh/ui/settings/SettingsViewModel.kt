package com.sbssh.ui.settings

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sbssh.data.crypto.CryptoManager
import com.sbssh.R
import com.sbssh.data.crypto.FieldCryptoManager
import com.sbssh.data.crypto.SessionKeyHolder
import com.sbssh.data.db.AppDatabase
import com.sbssh.data.db.VpsEntity
import com.sbssh.ui.cloud.CloudSyncApi
import com.sbssh.ui.cloud.CloudException
import com.sbssh.util.AppLogger
import com.sbssh.util.BiometricHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsUiState(
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val language: String = "zh",
    val fontSize: String = "medium",
    val showAbout: Boolean = false,
    val showLanguageDialog: Boolean = false,
    val showFontSizeDialog: Boolean = false,
    val showBiometricPasswordDialog: Boolean = false,
    val showChangePasswordDialog: Boolean = false,
    val showCloudSyncDialog: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val shouldRestart: Boolean = false,
    val cloudSyncEnabled: Boolean = false,
    val cloudSyncUrl: String = "",
    val cloudSyncUsername: String = "",
    val cloudSyncLoading: Boolean = false,
    val cloudSyncLoggedIn: Boolean = false,
    val cloudSyncLastSync: String? = null
)

// Backup format wrapper (v1)
data class BackupEnvelope(
    val format: String = "sbssh_backup_v1",
    val encrypted: Boolean = false,
    val payload: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class BackupItem(
    val alias: String,
    val host: String,
    val port: Int,
    val username: String,
    val authType: String,
    val encryptedPassword: String?,
    val encryptedKeyContent: String?,
    val encryptedKeyPassphrase: String?,
    val createdAt: Long,
    val updatedAt: Long
)

class SettingsViewModel(
    private val context: Context,
    private val activity: AppCompatActivity? = null
) : ViewModel() {

    private val cryptoManager = CryptoManager(context)
    private val fieldCrypto = FieldCryptoManager()
    private val settingsManager = SettingsManager.getInstance(context)
    private var dao = runCatching { AppDatabase.getInstance(context).vpsDao() }.getOrNull()
    private val gson = Gson()
    private var cloudApi = CloudSyncApi(settingsManager.settings.value.cloudSyncUrl.ifEmpty { "http://localhost:9800" })

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val biometricAvailable = activity?.let { BiometricHelper.isBiometricAvailable(it) } ?: false
        val settings = settingsManager.settings.value
        applyLocale(settings.language)
        _uiState.value = _uiState.value.copy(
            biometricEnabled = cryptoManager.isBiometricEnabled(),
            biometricAvailable = biometricAvailable,
            language = settings.language,
            fontSize = settings.fontSize,
            cloudSyncEnabled = settings.cloudSyncEnabled,
            cloudSyncUrl = settings.cloudSyncUrl,
            cloudSyncUsername = settings.cloudSyncUsername,
            cloudSyncLoggedIn = settingsManager.getCloudToken() != null
        )
    }

    fun getBackupFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "sbssh_backup_${sdf.format(Date())}.enc"
    }

    // ========== Biometric ==========
    fun showBiometricPasswordDialog() {
        _uiState.value = _uiState.value.copy(showBiometricPasswordDialog = true)
    }

    fun dismissBiometricPasswordDialog() {
        _uiState.value = _uiState.value.copy(showBiometricPasswordDialog = false)
    }

    fun toggleBiometric(password: String) {
        AppLogger.log("BIO", "toggleBiometric called, current=${_uiState.value.biometricEnabled}")
        val current = _uiState.value.biometricEnabled
        if (current) {
            cryptoManager.disableBiometric()
            _uiState.value = _uiState.value.copy(biometricEnabled = false, showBiometricPasswordDialog = false, success = context.getString(R.string.biometric_disabled))
            return
        }
        // User is already logged in — use session key directly, no need to re-verify password
        if (activity == null) {
            AppLogger.log("BIO", "Activity is null!")
            _uiState.value = _uiState.value.copy(error = context.getString(R.string.error_activity_context_missing), showBiometricPasswordDialog = false)
            return
        }
        if (!BiometricHelper.isBiometricAvailable(activity)) {
            AppLogger.log("BIO", "Biometric not available on device")
            _uiState.value = _uiState.value.copy(error = context.getString(R.string.biometric_not_available), showBiometricPasswordDialog = false)
            return
        }
        try {
            val keyBytes = SessionKeyHolder.get()
            AppLogger.log("BIO", "Got session key, length=${keyBytes.size}, enabling biometric...")
            cryptoManager.enableBiometric(keyBytes)
            AppLogger.log("BIO", "Biometric enabled successfully")
            _uiState.value = _uiState.value.copy(biometricEnabled = true, showBiometricPasswordDialog = false, success = context.getString(R.string.biometric_enabled))
        } catch (e: Exception) {
            AppLogger.log("BIO", "Enable failed", e)
            _uiState.value = _uiState.value.copy(error = context.getString(R.string.biometric_enable_failed, e.javaClass.simpleName, e.message ?: ""), showBiometricPasswordDialog = false)
        }
    }

    // ========== Language ==========
    fun showLanguageDialog() { _uiState.value = _uiState.value.copy(showLanguageDialog = true) }
    fun dismissLanguageDialog() { _uiState.value = _uiState.value.copy(showLanguageDialog = false) }

    fun setLanguage(lang: String) {
        applyLocale(lang)
        settingsManager.setLanguage(lang)
        _uiState.value = _uiState.value.copy(language = lang, showLanguageDialog = false, shouldRestart = false)
    }

    private fun applyLocale(lang: String) {
        val tag = if (lang == "zh") "zh-CN" else "en"
        val localeList = LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun onRestartConsumed() { _uiState.value = _uiState.value.copy(shouldRestart = false) }

    fun logout() {
        SessionKeyHolder.clear()
    }

    // ========== Font Size ==========
    fun showFontSizeDialog() { _uiState.value = _uiState.value.copy(showFontSizeDialog = true) }
    fun dismissFontSizeDialog() { _uiState.value = _uiState.value.copy(showFontSizeDialog = false) }

    fun setFontSize(size: String) {
        settingsManager.setFontSize(size)
        _uiState.value = _uiState.value.copy(fontSize = size, showFontSizeDialog = false, success = context.getString(R.string.font_size_updated))
    }

    // ========== Backup helpers ==========
    private suspend fun buildBackupEnvelope(): String {
        if (dao == null) throw IllegalStateException(context.getString(R.string.error_database_not_initialized))
        // Step 1: Read VPS data
        val vpsList = try { dao!!.getAllVpsAsList() } catch (e: Exception) {
            AppLogger.log("BACKUP", "getAllVpsAsList failed", e); throw e }
        AppLogger.log("BACKUP", "VPS count: ${vpsList.size}")
        if (vpsList.isEmpty()) throw IllegalStateException(context.getString(R.string.error_no_servers_to_backup))

        // Step 2: Serialize
        val backupList = vpsList.map { v ->
            BackupItem(
                alias = v.alias,
                host = v.host,
                port = v.port,
                username = v.username,
                authType = v.authType,
                encryptedPassword = v.encryptedPassword,
                encryptedKeyContent = v.encryptedKeyContent,
                encryptedKeyPassphrase = v.encryptedKeyPassphrase,
                createdAt = v.createdAt,
                updatedAt = v.updatedAt
            )
        }
        val json = gson.toJson(backupList)
        AppLogger.log("BACKUP", "JSON size: ${json.length}")

        // Step 3: Encrypt (if session key available) and wrap in envelope
        val (encrypted, payload) = try {
            if (SessionKeyHolder.isSet()) {
                val key = SessionKeyHolder.get()
                AppLogger.log("BACKUP", "Session key set, encrypting payload...")
                true to (fieldCrypto.encrypt(json, key) ?: json)
            } else {
                AppLogger.log("BACKUP", "Session key NOT set, writing plain payload")
                false to json
            }
        } catch (e: Exception) {
            AppLogger.log("BACKUP", "Encryption failed, writing plain", e)
            false to json
        }
        val envelope = BackupEnvelope(encrypted = encrypted, payload = payload)
        val dataToWrite = gson.toJson(envelope)
        AppLogger.log("BACKUP", "Envelope size: ${dataToWrite.length}, encrypted=$encrypted")
        return dataToWrite
    }

    // ========== Backup — save directly to Downloads via MediaStore ==========
    fun saveBackupToDownloads() {
        AppLogger.log("BACKUP", "saveBackupToDownloads")
        viewModelScope.launch {
            try {
                val dataToWrite = buildBackupEnvelope()
                val bytes = dataToWrite.toByteArray(Charsets.UTF_8)
                if (bytes.isEmpty()) throw IllegalStateException(context.getString(R.string.error_backup_bytes_empty))

                val fileName = getBackupFileName()
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException(context.getString(R.string.error_backup_create_file_failed))

                resolver.openOutputStream(uri)?.use { it.write(bytes); it.flush() }
                    ?: throw IllegalStateException(context.getString(R.string.error_backup_open_output_failed))

                AppLogger.log("BACKUP", "Saved to Downloads: uri=$uri, bytes=${bytes.size}")
                _uiState.value = _uiState.value.copy(success = context.getString(R.string.success_backup_saved_downloads, bytes.size))
            } catch (e: Exception) {
                AppLogger.log("BACKUP", "Backup FAILED", e)
                _uiState.value = _uiState.value.copy(error = context.getString(R.string.error_backup_failed, "${e.javaClass.simpleName}: ${e.message}"))
            }
        }
    }

    // ========== Backup — legacy (SAF) ==========
    fun saveBackupToUri(uri: Uri) {
        AppLogger.log("BACKUP", "saveBackupToUri: uri=$uri")
        viewModelScope.launch {
            try {
                val dataToWrite = buildBackupEnvelope()
                val tempFile = java.io.File(context.cacheDir, "sbssh_backup_temp.enc")
                tempFile.writeText(dataToWrite, Charsets.UTF_8)
                val bytes = tempFile.readBytes()
                if (bytes.isEmpty()) throw IllegalStateException(context.getString(R.string.error_backup_bytes_empty))
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException(context.getString(R.string.error_backup_open_output_failed))
                output.use { it.write(bytes); it.flush() }
                tempFile.delete()
                AppLogger.log("BACKUP", "Copied ${bytes.size} bytes to URI")
                _uiState.value = _uiState.value.copy(success = context.getString(R.string.success_backup_saved, bytes.size))
            } catch (e: Exception) {
                AppLogger.log("BACKUP", "Backup FAILED", e)
                _uiState.value = _uiState.value.copy(error = context.getString(R.string.error_backup_failed, "${e.javaClass.simpleName}: ${e.message}"))
            }
        }
    }

    // ========== Restore ==========
    fun restoreServers(uri: Uri) {
        AppLogger.log("RESTORE", "restoreServers: uri=$uri")
        viewModelScope.launch {
            try {
                if (dao == null) {
                    dao = runCatching { AppDatabase.getInstance(context).vpsDao() }.getOrNull()
                }
                if (dao == null) {
                    AppLogger.log("RESTORE", "DAO is null")
                    _uiState.value = _uiState.value.copy(error = context.getString(R.string.error_database_not_initialized))
                    return@launch
                }
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    ?: throw Exception(context.getString(R.string.error_failed_read_backup))
                AppLogger.log("RESTORE", "Read ${content.length} chars from file")

                // Try to parse new envelope format first
                val envelope = try { gson.fromJson(content, BackupEnvelope::class.java) } catch (_: Exception) { null }

                val payload: String
                val encrypted: Boolean
                if (envelope != null && envelope.format == "sbssh_backup_v1") {
                    encrypted = envelope.encrypted
                    payload = envelope.payload
                    AppLogger.log("RESTORE", "Envelope detected, encrypted=$encrypted")
                } else {
                    // Legacy format: raw encrypted string or raw JSON
                    encrypted = false
                    payload = content
                    AppLogger.log("RESTORE", "Legacy backup format detected")
                }

                val json = if (encrypted) {
                    if (!SessionKeyHolder.isSet()) {
                        AppLogger.log("RESTORE", "Session key NOT set, cannot decrypt")
                        _uiState.value = _uiState.value.copy(error = context.getString(R.string.error_restore_unlock_required))
                        return@launch
                    }
                    val key = SessionKeyHolder.get()
                    AppLogger.log("RESTORE", "Decrypting payload...")
                    fieldCrypto.decrypt(payload, key) ?: throw Exception("Decrypt failed")
                } else {
                    // If legacy content might be encrypted, try decrypt when key is available
                    if (SessionKeyHolder.isSet()) {
                        val key = SessionKeyHolder.get()
                        try {
                            fieldCrypto.decrypt(payload, key) ?: payload
                        } catch (_: Exception) {
                            payload
                        }
                    } else {
                        payload
                    }
                }

                AppLogger.log("RESTORE", "JSON size: ${json.length}")
                val type = object : TypeToken<List<BackupItem>>() {}.type
                val backupList: List<BackupItem> = gson.fromJson(json, type)
                val now = System.currentTimeMillis()
                suspend fun insertOnce(): Int = withContext(Dispatchers.IO) {
                    var restored = 0
                    for (item in backupList) {
                        dao!!.insertVps(
                            VpsEntity(
                                alias = item.alias.ifBlank { "Unknown" },
                                host = item.host.ifBlank { "0.0.0.0" },
                                port = item.port,
                                username = item.username.ifBlank { "root" },
                                authType = item.authType,
                                encryptedPassword = item.encryptedPassword,
                                encryptedKeyContent = item.encryptedKeyContent,
                                encryptedKeyPassphrase = item.encryptedKeyPassphrase,
                                createdAt = if (item.createdAt > 0) item.createdAt else now,
                                updatedAt = if (item.updatedAt > 0) item.updatedAt else now
                            )
                        )
                        restored++
                    }
                    restored
                }
                val count = insertOnce()
                AppLogger.log("RESTORE", "Restored $count server(s)")
                _uiState.value = _uiState.value.copy(success = context.getString(R.string.success_restored_servers, count))
            } catch (e: Exception) {
                AppLogger.log("RESTORE", "Restore FAILED", e)
                _uiState.value = _uiState.value.copy(error = context.getString(R.string.error_restore_failed, e.message ?: ""))
            }
        }
    }

    // ========== Cloud Sync ==========
    fun showCloudSyncDialog() { _uiState.value = _uiState.value.copy(showCloudSyncDialog = true) }
    fun dismissCloudSyncDialog() { _uiState.value = _uiState.value.copy(showCloudSyncDialog = false) }

    fun cloudLogin(url: String, username: String, password: String) {
        if (url.isBlank() || username.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "All fields are required")
            return
        }
        _uiState.value = _uiState.value.copy(cloudSyncLoading = true)
        viewModelScope.launch {
            try {
                cloudApi.setBaseUrl(url)
                val resp = withContext(Dispatchers.IO) { cloudApi.login(username, password) }
                settingsManager.setCloudSync(true, url, username)
                settingsManager.setCloudToken(resp.token)
                _uiState.value = _uiState.value.copy(
                    cloudSyncEnabled = true,
                    cloudSyncUrl = url,
                    cloudSyncUsername = username,
                    cloudSyncLoggedIn = true,
                    cloudSyncLoading = false,
                    showCloudSyncDialog = false,
                    success = context.getString(R.string.success_cloud_sync_configured)
                )
            } catch (e: Exception) {
                AppLogger.log("CLOUD", "Login failed", e)
                _uiState.value = _uiState.value.copy(
                    cloudSyncLoading = false,
                    error = "Login failed: ${e.message}"
                )
            }
        }
    }

    fun cloudRegister(url: String, username: String, password: String) {
        if (url.isBlank() || username.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "All fields are required")
            return
        }
        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(error = "Password must be at least 6 characters")
            return
        }
        _uiState.value = _uiState.value.copy(cloudSyncLoading = true)
        viewModelScope.launch {
            try {
                cloudApi.setBaseUrl(url)
                // Upload the PBKDF2 salt so other devices can derive the same DEK
                val saltBytes = cryptoManager.getSalt()
                val saltBase64 = android.util.Base64.encodeToString(saltBytes, android.util.Base64.NO_WRAP)
                val resp = withContext(Dispatchers.IO) { cloudApi.register(username, password, saltBase64) }
                settingsManager.setCloudSync(true, url, username)
                settingsManager.setCloudToken(resp.token)
                _uiState.value = _uiState.value.copy(
                    cloudSyncEnabled = true,
                    cloudSyncUrl = url,
                    cloudSyncUsername = username,
                    cloudSyncLoggedIn = true,
                    cloudSyncLoading = false,
                    showCloudSyncDialog = false,
                    success = context.getString(R.string.success_cloud_sync_configured)
                )
            } catch (e: Exception) {
                AppLogger.log("CLOUD", "Register failed", e)
                _uiState.value = _uiState.value.copy(
                    cloudSyncLoading = false,
                    error = "Register failed: ${e.message}"
                )
            }
        }
    }

    fun cloudLogout() {
        settingsManager.setCloudSync(false)
        settingsManager.clearCloudToken()
        _uiState.value = _uiState.value.copy(
            cloudSyncEnabled = false,
            cloudSyncUrl = "",
            cloudSyncUsername = "",
            cloudSyncLoggedIn = false,
            showCloudSyncDialog = false,
            success = "Cloud sync disabled"
        )
    }

    fun cloudUpload() {
        val token = settingsManager.getCloudToken()
        if (token == null) {
            _uiState.value = _uiState.value.copy(error = "Please login first")
            return
        }
        val url = _uiState.value.cloudSyncUrl
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Server URL not configured")
            return
        }
        _uiState.value = _uiState.value.copy(cloudSyncLoading = true)
        viewModelScope.launch {
            try {
                cloudApi.setBaseUrl(url)
                val envelope = buildBackupEnvelope()
                withContext(Dispatchers.IO) { cloudApi.upload(token, envelope, android.os.Build.MODEL) }
                _uiState.value = _uiState.value.copy(
                    cloudSyncLoading = false,
                    cloudSyncLastSync = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    success = "Upload successful"
                )
            } catch (e: Exception) {
                AppLogger.log("CLOUD", "Upload failed", e)
                _uiState.value = _uiState.value.copy(
                    cloudSyncLoading = false,
                    error = "Upload failed: ${e.message}"
                )
            }
        }
    }

    fun cloudDownload() {
        val token = settingsManager.getCloudToken()
        if (token == null) {
            _uiState.value = _uiState.value.copy(error = "Please login first")
            return
        }
        val url = _uiState.value.cloudSyncUrl
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Server URL not configured")
            return
        }
        _uiState.value = _uiState.value.copy(cloudSyncLoading = true)
        viewModelScope.launch {
            try {
                cloudApi.setBaseUrl(url)
                val resp = withContext(Dispatchers.IO) { cloudApi.download(token) }
                if (resp.encryptedData.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(cloudSyncLoading = false, error = "No data on server")
                    return@launch
                }
                // Restore from the downloaded backup envelope
                val content = resp.encryptedData
                val envelope = try { gson.fromJson(content, BackupEnvelope::class.java) } catch (_: Exception) { null }
                val payload: String
                val encrypted: Boolean
                if (envelope != null && envelope.format == "sbssh_backup_v1") {
                    encrypted = envelope.encrypted
                    payload = envelope.payload
                } else {
                    encrypted = false
                    payload = content
                }
                val json = if (encrypted) {
                    if (!SessionKeyHolder.isSet()) {
                        _uiState.value = _uiState.value.copy(cloudSyncLoading = false, error = "Please unlock app first")
                        return@launch
                    }
                    val key = SessionKeyHolder.get()
                    fieldCrypto.decrypt(payload, key) ?: throw Exception("Decrypt failed")
                } else payload
                val type = object : com.google.gson.reflect.TypeToken<List<BackupItem>>() {}.type
                val backupList: List<BackupItem> = gson.fromJson(json, type)
                if (dao == null) dao = runCatching { AppDatabase.getInstance(context).vpsDao() }.getOrNull()
                if (dao == null) {
                    _uiState.value = _uiState.value.copy(cloudSyncLoading = false, error = "Database not initialized")
                    return@launch
                }
                val now = System.currentTimeMillis()
                val count = withContext(Dispatchers.IO) {
                    var restored = 0
                    for (item in backupList) {
                        dao!!.insertVps(
                            VpsEntity(
                                alias = item.alias.ifBlank { "Unknown" },
                                host = item.host.ifBlank { "0.0.0.0" },
                                port = item.port,
                                username = item.username.ifBlank { "root" },
                                authType = item.authType,
                                encryptedPassword = item.encryptedPassword,
                                encryptedKeyContent = item.encryptedKeyContent,
                                encryptedKeyPassphrase = item.encryptedKeyPassphrase,
                                createdAt = if (item.createdAt > 0) item.createdAt else now,
                                updatedAt = if (item.updatedAt > 0) item.updatedAt else now
                            )
                        )
                        restored++
                    }
                    restored
                }
                _uiState.value = _uiState.value.copy(
                    cloudSyncLoading = false,
                    cloudSyncLastSync = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    success = "Download successful: $count server(s) restored"
                )
            } catch (e: Exception) {
                AppLogger.log("CLOUD", "Download failed", e)
                _uiState.value = _uiState.value.copy(
                    cloudSyncLoading = false,
                    error = "Download failed: ${e.message}"
                )
            }
        }
    }

    // ========== Change Password ==========
    fun showChangePasswordDialog() { _uiState.value = _uiState.value.copy(showChangePasswordDialog = true) }
    fun dismissChangePasswordDialog() { _uiState.value = _uiState.value.copy(showChangePasswordDialog = false) }

    fun changePassword(oldPassword: String, newPassword: String, confirmPassword: String) {
        if (newPassword != confirmPassword) { _uiState.value = _uiState.value.copy(error = context.getString(R.string.alert_passwords_do_not_match_msg)); return }
        if (newPassword.length < 6) { _uiState.value = _uiState.value.copy(error = context.getString(R.string.error_new_password_too_short)); return }
        viewModelScope.launch {
            try {
                val newKeyBytes = cryptoManager.changeMasterPassword(oldPassword, newPassword)
                if (dao != null) {
                    val vpsList = dao!!.getAllVpsAsList()
                    val oldKey = SessionKeyHolder.get()
                    for (vps in vpsList) {
                        val pw = fieldCrypto.decrypt(vps.encryptedPassword, oldKey)
                        val kc = fieldCrypto.decrypt(vps.encryptedKeyContent, oldKey)
                        val kp = fieldCrypto.decrypt(vps.encryptedKeyPassphrase, oldKey)
                        dao!!.updateVps(vps.copy(
                            encryptedPassword = fieldCrypto.encrypt(pw, newKeyBytes),
                            encryptedKeyContent = fieldCrypto.encrypt(kc, newKeyBytes),
                            encryptedKeyPassphrase = fieldCrypto.encrypt(kp, newKeyBytes),
                            updatedAt = System.currentTimeMillis()
                        ))
                    }
                }
                SessionKeyHolder.set(newKeyBytes)
                _uiState.value = _uiState.value.copy(showChangePasswordDialog = false, success = context.getString(R.string.success_password_changed))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: context.getString(R.string.error_password_change_failed))
            }
        }
    }

    // ========== About ==========
    fun showAbout() { _uiState.value = _uiState.value.copy(showAbout = true) }
    fun dismissAbout() { _uiState.value = _uiState.value.copy(showAbout = false) }
    fun clearMessages() { _uiState.value = _uiState.value.copy(error = null, success = null) }

    class Factory(private val context: Context, private val activity: AppCompatActivity? = null) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(context, activity) as T
    }
}
