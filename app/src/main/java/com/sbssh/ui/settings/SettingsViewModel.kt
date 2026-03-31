package com.sbssh.ui.settings

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sbssh.data.crypto.CryptoManager
import com.sbssh.data.crypto.FieldCryptoManager
import com.sbssh.data.crypto.SessionKeyHolder
import com.sbssh.data.db.AppDatabase
import com.sbssh.data.db.VpsEntity
import com.sbssh.util.AppLogger
import com.sbssh.util.BiometricHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    val cloudSyncUsername: String = ""
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
    private var dao = runCatching { AppDatabase.getInstance().vpsDao() }.getOrNull()
    private val gson = Gson()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val biometricAvailable = activity?.let { BiometricHelper.isBiometricAvailable(it) } ?: false
        val settings = settingsManager.settings.value
        _uiState.value = _uiState.value.copy(
            biometricEnabled = cryptoManager.isBiometricEnabled(),
            biometricAvailable = biometricAvailable,
            language = settings.language,
            fontSize = settings.fontSize,
            cloudSyncEnabled = settings.cloudSyncEnabled,
            cloudSyncUrl = settings.cloudSyncUrl,
            cloudSyncUsername = settings.cloudSyncUsername
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
            _uiState.value = _uiState.value.copy(biometricEnabled = false, showBiometricPasswordDialog = false, success = "Biometric login disabled")
            return
        }
        // User is already logged in — use session key directly, no need to re-verify password
        if (activity == null) {
            AppLogger.log("BIO", "Activity is null!")
            _uiState.value = _uiState.value.copy(error = "Activity context missing", showBiometricPasswordDialog = false)
            return
        }
        if (!BiometricHelper.isBiometricAvailable(activity)) {
            AppLogger.log("BIO", "Biometric not available on device")
            _uiState.value = _uiState.value.copy(error = "Biometric not available on this device", showBiometricPasswordDialog = false)
            return
        }
        try {
            val keyBytes = SessionKeyHolder.get()
            AppLogger.log("BIO", "Got session key, length=${keyBytes.size}, enabling biometric...")
            cryptoManager.enableBiometric(keyBytes)
            AppLogger.log("BIO", "Biometric enabled successfully")
            _uiState.value = _uiState.value.copy(biometricEnabled = true, showBiometricPasswordDialog = false, success = "Biometric login enabled")
        } catch (e: Exception) {
            AppLogger.log("BIO", "Enable failed", e)
            _uiState.value = _uiState.value.copy(error = "Enable failed: ${e.javaClass.simpleName}: ${e.message}", showBiometricPasswordDialog = false)
        }
    }

    // ========== Language ==========
    fun showLanguageDialog() { _uiState.value = _uiState.value.copy(showLanguageDialog = true) }
    fun dismissLanguageDialog() { _uiState.value = _uiState.value.copy(showLanguageDialog = false) }

    fun setLanguage(lang: String) {
        settingsManager.setLanguage(lang)
        _uiState.value = _uiState.value.copy(language = lang, showLanguageDialog = false, shouldRestart = true)
    }

    fun onRestartConsumed() { _uiState.value = _uiState.value.copy(shouldRestart = false) }

    // ========== Font Size ==========
    fun showFontSizeDialog() { _uiState.value = _uiState.value.copy(showFontSizeDialog = true) }
    fun dismissFontSizeDialog() { _uiState.value = _uiState.value.copy(showFontSizeDialog = false) }

    fun setFontSize(size: String) {
        settingsManager.setFontSize(size)
        _uiState.value = _uiState.value.copy(fontSize = size, showFontSizeDialog = false, success = "Font size updated")
    }

    // ========== Backup helpers ==========
    private suspend fun buildBackupEnvelope(): String {
        if (dao == null) throw IllegalStateException("Database not initialized")
        // Step 1: Read VPS data
        val vpsList = try { dao!!.getAllVpsAsList() } catch (e: Exception) {
            AppLogger.log("BACKUP", "getAllVpsAsList failed", e); throw e }
        AppLogger.log("BACKUP", "VPS count: ${vpsList.size}")
        if (vpsList.isEmpty()) throw IllegalStateException("No servers to backup")

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
                if (bytes.isEmpty()) throw IllegalStateException("Backup bytes empty")

                val fileName = getBackupFileName()
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Failed to create download file")

                resolver.openOutputStream(uri)?.use { it.write(bytes); it.flush() }
                    ?: throw IllegalStateException("openOutputStream returned null")

                AppLogger.log("BACKUP", "Saved to Downloads: uri=$uri, bytes=${bytes.size}")
                _uiState.value = _uiState.value.copy(success = "Backup saved to Downloads (${bytes.size} bytes)")
            } catch (e: Exception) {
                AppLogger.log("BACKUP", "Backup FAILED", e)
                _uiState.value = _uiState.value.copy(error = "Backup failed: ${e.javaClass.simpleName}: ${e.message}")
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
                if (bytes.isEmpty()) throw IllegalStateException("Backup bytes empty")
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("openOutputStream returned null")
                output.use { it.write(bytes); it.flush() }
                tempFile.delete()
                AppLogger.log("BACKUP", "Copied ${bytes.size} bytes to URI")
                _uiState.value = _uiState.value.copy(success = "Backup saved (${bytes.size} bytes)")
            } catch (e: Exception) {
                AppLogger.log("BACKUP", "Backup FAILED", e)
                _uiState.value = _uiState.value.copy(error = "Backup failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    // ========== Restore ==========
    fun restoreServers(uri: Uri) {
        AppLogger.log("RESTORE", "restoreServers: uri=$uri")
        viewModelScope.launch {
            try {
                if (dao == null) {
                    AppLogger.log("RESTORE", "DAO is null")
                    _uiState.value = _uiState.value.copy(error = "Database not initialized")
                    return@launch
                }
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    ?: throw Exception("Failed to read backup file")
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
                        _uiState.value = _uiState.value.copy(error = "Please unlock app before restore")
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
                var count = 0
                for (item in backupList) {
                    dao!!.insertVps(VpsEntity(
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
                    ))
                    count++
                }
                AppLogger.log("RESTORE", "Restored $count server(s)")
                _uiState.value = _uiState.value.copy(success = "Restored $count server(s)")
            } catch (e: Exception) {
                AppLogger.log("RESTORE", "Restore FAILED", e)
                _uiState.value = _uiState.value.copy(error = "Restore failed: ${e.message}")
            }
        }
    }

    // ========== Cloud Sync ==========
    fun showCloudSyncDialog() { _uiState.value = _uiState.value.copy(showCloudSyncDialog = true) }
    fun dismissCloudSyncDialog() { _uiState.value = _uiState.value.copy(showCloudSyncDialog = false) }

    fun saveCloudSync(enabled: Boolean, url: String, username: String) {
        settingsManager.setCloudSync(enabled, url, username)
        _uiState.value = _uiState.value.copy(cloudSyncEnabled = enabled, cloudSyncUrl = url, cloudSyncUsername = username, showCloudSyncDialog = false, success = if (enabled) "Cloud sync configured (coming soon)" else "Cloud sync disabled")
    }

    // ========== Change Password ==========
    fun showChangePasswordDialog() { _uiState.value = _uiState.value.copy(showChangePasswordDialog = true) }
    fun dismissChangePasswordDialog() { _uiState.value = _uiState.value.copy(showChangePasswordDialog = false) }

    fun changePassword(oldPassword: String, newPassword: String, confirmPassword: String) {
        if (newPassword != confirmPassword) { _uiState.value = _uiState.value.copy(error = "Passwords do not match"); return }
        if (newPassword.length < 6) { _uiState.value = _uiState.value.copy(error = "New password must be at least 6 characters"); return }
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
                _uiState.value = _uiState.value.copy(showChangePasswordDialog = false, success = "Password changed successfully")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Password change failed")
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
