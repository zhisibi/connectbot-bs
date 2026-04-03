package com.boshconnect.ui.settings

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.boshconnect.data.crypto.CryptoManager
import com.boshconnect.R
import com.boshconnect.data.crypto.FieldCryptoManager
import com.boshconnect.data.crypto.SessionKeyHolder
import com.boshconnect.data.db.AppDatabase
import com.boshconnect.data.db.VpsEntity
import com.boshconnect.ui.cloud.CloudSyncApi
import com.boshconnect.ui.cloud.CloudException
import com.boshconnect.ui.cloud.GitHubApi
import com.boshconnect.util.AppLogger
import com.boshconnect.util.BiometricHelper
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
    val cloudSyncLastSync: String? = null,
    val cloudAutoSync: Boolean = false,
    val showRestorePasswordDialog: Boolean = false,
    // GitHub backup
    val githubBackupEnabled: Boolean = false,
    val githubRepo: String = "",
    val githubToken: String = "",
    val githubLoading: Boolean = false,
    val showGithubDialog: Boolean = false,
    val showLocalBackupDialog: Boolean = false,
    // Backup password
    val backupPasswordSet: Boolean = false,
    val showBackupPasswordDialog: Boolean = false
)

// Backup format wrapper (v1)
data class BackupEnvelope(
    val format: String = "sbssh_backup_v1",
    val encrypted: Boolean = false,
    val payload: String = "",
    val salt: String? = null,  // base64-encoded PBKDF2 salt for cross-device restore
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
            cloudSyncLoggedIn = settingsManager.getCloudToken() != null,
            cloudAutoSync = settings.cloudAutoSync,
            githubBackupEnabled = settings.githubBackupEnabled,
            githubRepo = settings.githubRepo,
            githubToken = settings.githubToken,
            backupPasswordSet = settingsManager.isBackupPasswordSet()
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
    /**
     * Build envelope for cloud sync - uses device key (portable for same device).
     * Fields in BackupItem are kept as-is (already encrypted with device key).
     */
    private suspend fun buildCloudEnvelope(): String {
        if (dao == null) throw IllegalStateException("Database not initialized")
        val vpsList = try { dao!!.getAllVpsAsList() } catch (e: Exception) { throw e }
        if (vpsList.isEmpty()) throw IllegalStateException("No servers to sync")

        val backupList = vpsList.map { v ->
            BackupItem(
                alias = v.alias, host = v.host, port = v.port, username = v.username,
                authType = v.authType, encryptedPassword = v.encryptedPassword,
                encryptedKeyContent = v.encryptedKeyContent,
                encryptedKeyPassphrase = v.encryptedKeyPassphrase,
                createdAt = v.createdAt, updatedAt = v.updatedAt
            )
        }
        val json = gson.toJson(backupList)
        val deviceKey = SessionKeyHolder.get()
        val payload = fieldCrypto.encrypt(json, deviceKey) ?: throw Exception("Encrypt failed")
        val saltBase64 = try { Base64.encodeToString(cryptoManager.getSalt(), Base64.NO_WRAP) } catch (_: Exception) { null }
        return gson.toJson(BackupEnvelope(encrypted = true, payload = payload, salt = saltBase64))
    }

    /**
     * Build envelope for local/GitHub backup - uses backup password + random salt (portable across devices).
     * Fields in BackupItem are decrypted to plaintext first.
     */
    private suspend fun buildBackupEnvelope(): String {
        if (dao == null) throw IllegalStateException(context.getString(R.string.error_database_not_initialized))
        // Step 1: Read VPS data
        val vpsList = try { dao!!.getAllVpsAsList() } catch (e: Exception) {
            AppLogger.log("BACKUP", "getAllVpsAsList failed", e); throw e }
        AppLogger.log("BACKUP", "VPS count: ${vpsList.size}")
        if (vpsList.isEmpty()) throw IllegalStateException(context.getString(R.string.error_no_servers_to_backup))

        // Step 2: Serialize - decrypt fields with device key first so backup is portable
        val deviceKey = if (SessionKeyHolder.isSet()) SessionKeyHolder.get() else null
        val backupList = vpsList.map { v ->
            BackupItem(
                alias = v.alias,
                host = v.host,
                port = v.port,
                username = v.username,
                authType = v.authType,
                encryptedPassword = deviceKey?.let { fieldCrypto.decrypt(v.encryptedPassword, it) } ?: v.encryptedPassword,
                encryptedKeyContent = deviceKey?.let { fieldCrypto.decrypt(v.encryptedKeyContent, it) } ?: v.encryptedKeyContent,
                encryptedKeyPassphrase = deviceKey?.let { fieldCrypto.decrypt(v.encryptedKeyPassphrase, it) } ?: v.encryptedKeyPassphrase,
                createdAt = v.createdAt,
                updatedAt = v.updatedAt
            )
        }
        val json = gson.toJson(backupList)
        AppLogger.log("BACKUP", "JSON size: ${json.length}")

        // Step 3: Generate backup salt and derive backup key
        val backupSalt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val backupKey = if (settingsManager.isBackupPasswordSet() && deviceKey != null) {
            // Decrypt stored backup password
            val encryptedPwd = settingsManager.getBackupPasswordEncrypted()
            val backupPassword = encryptedPwd?.let { fieldCrypto.decrypt(it, deviceKey) }
            if (backupPassword != null) {
                AppLogger.log("BACKUP", "Using backup password for encryption")
                deriveBackupKey(backupPassword, backupSalt)
            } else {
                AppLogger.log("BACKUP", "Cannot decrypt backup password, using device key")
                deviceKey
            }
        } else {
            // No backup password set - use device key
            deviceKey ?: throw IllegalStateException("No encryption key available")
        }

        val encrypted = true
        val payload = fieldCrypto.encrypt(json, backupKey) ?: throw Exception("Encryption failed")
        val saltBase64 = android.util.Base64.encodeToString(backupSalt, android.util.Base64.NO_WRAP)

        val envelope = BackupEnvelope(encrypted = encrypted, payload = payload, salt = saltBase64)
        val dataToWrite = gson.toJson(envelope)
        AppLogger.log("BACKUP", "Envelope size: ${dataToWrite.length}, encrypted=$encrypted")
        return dataToWrite
    }

    // ========== Backup — save directly to Downloads via MediaStore ==========
    fun saveBackupToDownloads() {
        if (!settingsManager.isBackupPasswordSet()) {
            _uiState.value = _uiState.value.copy(error = "请先设置备份密码")
            showBackupPasswordDialog()
            return
        }
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
    private var pendingRestoreUri: Uri? = null
    private var pendingRestoreSalt: ByteArray? = null

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

                // Try to parse envelope format
                val envelope = try { gson.fromJson(content, BackupEnvelope::class.java) } catch (_: Exception) { null }

                val payload: String
                val encrypted: Boolean
                val backupSalt: ByteArray? = if (envelope?.salt != null) {
                    try { Base64.decode(envelope.salt, Base64.NO_WRAP) } catch (_: Exception) { null }
                } else null

                if (envelope != null && envelope.format == "sbssh_backup_v1") {
                    encrypted = envelope.encrypted
                    payload = envelope.payload
                    AppLogger.log("RESTORE", "Envelope detected, encrypted=$encrypted, backupSalt=${backupSalt?.size ?: 0}b")
                } else {
                    encrypted = false
                    payload = content
                    AppLogger.log("RESTORE", "Legacy backup format detected")
                }

                if (!encrypted) {
                    // Unencrypted - just parse and restore
                    restoreFromJson(payload)
                    return@launch
                }

                // Encrypted - need session key
                if (!SessionKeyHolder.isSet()) {
                    AppLogger.log("RESTORE", "Session key NOT set, cannot decrypt")
                    _uiState.value = _uiState.value.copy(error = context.getString(R.string.error_restore_unlock_required))
                    return@launch
                }

                // Check if backup salt matches local salt
                val localSalt = try { cryptoManager.getSalt() } catch (_: Exception) { null }
                val saltsMatch = backupSalt != null && localSalt != null && backupSalt.contentEquals(localSalt)

                if (backupSalt != null && !saltsMatch) {
                    AppLogger.log("RESTORE", "Backup salt differs from local salt - need password to re-derive key")
                    // Store pending state and ask for password
                    pendingRestoreUri = uri
                    pendingRestoreSalt = backupSalt
                    _uiState.value = _uiState.value.copy(
                        showCloudSyncDialog = false,
                        error = null
                    )
                    // Show a custom dialog for password entry
                    _uiState.value = _uiState.value.copy(
                        showBiometricPasswordDialog = false,
                    )
                    // Use a simple approach: prompt via error message with retry
                    decryptAndRestoreWithBackupSalt(payload, backupSalt)
                    return@launch
                }

                // Same salt - use current session key
                val key = SessionKeyHolder.get()
                AppLogger.log("RESTORE", "Decrypting payload with current key...")
                try {
                    val json = fieldCrypto.decrypt(payload, key) ?: throw Exception("Decrypt failed")
                    restoreFromJson(json)
                } catch (e: Exception) {
                    AppLogger.log("RESTORE", "Decrypt with current key failed, trying backup salt if available", e)
                    if (backupSalt != null) {
                        decryptAndRestoreWithBackupSalt(payload, backupSalt)
                    } else {
                        throw e
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("RESTORE", "Restore FAILED", e)
                _uiState.value = _uiState.value.copy(error = context.getString(R.string.error_restore_failed, e.message ?: ""))
            }
        }
    }

    /**
     * When backup salt differs from local salt, we need the user's password to re-derive the key.
     * We'll prompt the user for their master password.
     */
    private var _pendingPayload: String? = null
    private var _pendingBackupSalt: ByteArray? = null

    fun decryptAndRestoreWithBackupSalt(payload: String, backupSalt: ByteArray) {
        // We need the password. Prompt user.
        _pendingPayload = payload
        _pendingBackupSalt = backupSalt
        _uiState.value = _uiState.value.copy(
            showRestorePasswordDialog = true
        )
    }

    fun restoreWithPassword(password: String) {
        val payload = _pendingPayload ?: return
        val backupSalt = _pendingBackupSalt ?: return
        _uiState.value = _uiState.value.copy(showRestorePasswordDialog = false, cloudSyncLoading = true)
        viewModelScope.launch {
            try {
                val keyBytes = deriveBackupKey(password, backupSalt)
                AppLogger.log("RESTORE", "Re-derived backup key from password + salt, decrypting...")
                val json = withContext(Dispatchers.IO) {
                    fieldCrypto.decrypt(payload, keyBytes) ?: throw Exception("解密失败 - 密码错误？")
                }
                val count = restoreFromJson(json)
                _uiState.value = _uiState.value.copy(
                    cloudSyncLoading = false,
                    success = "恢复了 $count 台服务器"
                )
            } catch (e: Exception) {
                AppLogger.log("RESTORE", "Restore with backup password failed", e)
                _uiState.value = _uiState.value.copy(
                    cloudSyncLoading = false,
                    error = "恢复失败: ${e.message}"
                )
            } finally {
                _pendingPayload = null
                _pendingBackupSalt = null
            }
        }
    }

    private suspend fun restoreFromJson(json: String): Int {
        val type = object : TypeToken<List<BackupItem>>() {}.type
        val backupList: List<BackupItem> = gson.fromJson(json, type)
        val now = System.currentTimeMillis()
        if (dao == null) dao = runCatching { AppDatabase.getInstance(context).vpsDao() }.getOrNull()

        // Get device key for re-encrypting fields
        val deviceKey = if (SessionKeyHolder.isSet()) SessionKeyHolder.get() else null

        return withContext(Dispatchers.IO) {
            val allLocal = dao!!.getAllVpsAsList()

            // Deduplicate local entries
            val groups = allLocal.groupBy { "${it.host}:${it.port}:${it.username}" }
            for ((_, entries) in groups) {
                if (entries.size > 1) {
                    val sorted = entries.sortedByDescending { it.updatedAt }
                    for (dup in sorted.drop(1)) {
                        dao!!.deleteVps(dup)
                        AppLogger.log("RESTORE", "Deleted duplicate: ${dup.alias} (id=${dup.id})")
                    }
                }
            }

            val existing = dao!!.getAllVpsAsList()
                .associateBy { "${it.host}:${it.port}:${it.username}" }

            var restored = 0
            for (item in backupList) {
                // Re-encrypt fields with device key (backup contains plaintext)
                val encPw = deviceKey?.let { fieldCrypto.encrypt(item.encryptedPassword, it) } ?: item.encryptedPassword
                val encKc = deviceKey?.let { fieldCrypto.encrypt(item.encryptedKeyContent, it) } ?: item.encryptedKeyContent
                val encKp = deviceKey?.let { fieldCrypto.encrypt(item.encryptedKeyPassphrase, it) } ?: item.encryptedKeyPassphrase

                val key = "${item.host.ifBlank { "0.0.0.0" }}:${item.port}:${item.username.ifBlank { "root" }}"
                val existingVps = existing[key]
                if (existingVps != null) {
                    dao!!.updateVps(existingVps.copy(
                        alias = item.alias.ifBlank { "Unknown" },
                        authType = item.authType,
                        encryptedPassword = encPw,
                        encryptedKeyContent = encKc,
                        encryptedKeyPassphrase = encKp,
                        updatedAt = now
                    ))
                } else {
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
                }
                restored++
            }
            AppLogger.log("RESTORE", "Restored $restored server(s)")
            restored
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

                // Fetch server salt and derive key from it (critical for multi-device sync)
                try {
                    val saltResp = withContext(Dispatchers.IO) { cloudApi.getSalt(username, resp.token) }
                    if (!saltResp.encryptedSalt.isNullOrBlank()) {
                        val serverSalt = Base64.decode(saltResp.encryptedSalt, Base64.NO_WRAP)
                        AppLogger.log("CLOUD", "Fetched server salt, len=${serverSalt.size}")
                        // Save server salt locally (overwrites any local salt)
                        cryptoManager.saveSalt(serverSalt)
                        // Derive key using server salt
                        val keyBytes = cryptoManager.deriveKey(password, serverSalt)
                        cryptoManager.setPasswordVerification(password, serverSalt)
                        SessionKeyHolder.set(keyBytes)
                        AppLogger.log("CLOUD", "Key derived from server salt")
                    }
                } catch (e: Exception) {
                    AppLogger.log("CLOUD", "Failed to fetch server salt, using local", e)
                }

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
        settingsManager.setAutoSync(false)
        settingsManager.clearCloudToken()
        _uiState.value = _uiState.value.copy(
            cloudSyncEnabled = false,
            cloudSyncUrl = "",
            cloudSyncUsername = "",
            cloudSyncLoggedIn = false,
            cloudAutoSync = false,
            showCloudSyncDialog = false,
            success = context.getString(R.string.success_cloud_sync_disabled)
        )
    }

    fun toggleAutoSync(enabled: Boolean) {
        settingsManager.setAutoSync(enabled)
        _uiState.value = _uiState.value.copy(cloudAutoSync = enabled)
    }

    /**
     * Smart sync: upload local data to cloud, handling cloud-only servers.
     * @param onDeleteCloudExtra callback to ask user whether to delete cloud-only servers.
     *   Returns true = delete, false = keep. If null, always keep cloud-only.
     */
    fun cloudSmartSync(onDeleteCloudExtra: ((Int) -> Boolean)? = null) {
        val token = settingsManager.getCloudToken() ?: return
        val url = _uiState.value.cloudSyncUrl.ifBlank { return }
        _uiState.value = _uiState.value.copy(cloudSyncLoading = true)
        viewModelScope.launch {
            try {
                cloudApi.setBaseUrl(url)
                // 1. Download current cloud data to compare
                val cloudResp = withContext(Dispatchers.IO) { cloudApi.download(token) }
                val cloudList: List<BackupItem> = if (!cloudResp.encryptedData.isNullOrBlank() && SessionKeyHolder.isSet()) {
                    try {
                        val envelope = gson.fromJson(cloudResp.encryptedData, BackupEnvelope::class.java)
                        val json = if (envelope != null && envelope.format == "sbssh_backup_v1" && envelope.encrypted) {
                            fieldCrypto.decrypt(envelope.payload, SessionKeyHolder.get())
                        } else cloudResp.encryptedData
                        if (json != null) {
                            val type = object : com.google.gson.reflect.TypeToken<List<BackupItem>>() {}.type
                            gson.fromJson(json, type) ?: emptyList()
                        } else emptyList()
                    } catch (_: Exception) { emptyList() }
                } else emptyList()

                // 2. Get local servers
                if (dao == null) dao = runCatching { AppDatabase.getInstance(context).vpsDao() }.getOrNull()
                val localList = dao?.getAllVpsAsList() ?: emptyList()
                val localHosts = localList.map { "${it.host}:${it.port}:${it.username}" }.toSet()

                // 3. Check cloud-only servers (in cloud but not in local)
                val cloudOnlyCount = cloudList.count { item ->
                    val key = "${item.host}:${item.port}:${item.username}"
                    !localHosts.contains(key)
                }

                // 4. If cloud has extras, ask user
                if (cloudOnlyCount > 0 && onDeleteCloudExtra != null) {
                    val shouldDelete = onDeleteCloudExtra(cloudOnlyCount)
                    if (!shouldDelete) {
                        // User wants to keep cloud extras — do incremental: upload local, append to cloud
                        val mergedList = mutableListOf<BackupItem>()
                        mergedList.addAll(cloudList)
                        for (item in localList) {
                            val key = "${item.host}:${item.port}:${item.username}"
                            if (cloudList.none { "${it.host}:${it.port}:${it.username}" == key }) {
                                mergedList.add(BackupItem(
                                    alias = item.alias, host = item.host, port = item.port,
                                    username = item.username, authType = item.authType,
                                    encryptedPassword = item.encryptedPassword,
                                    encryptedKeyContent = item.encryptedKeyContent,
                                    encryptedKeyPassphrase = item.encryptedKeyPassphrase,
                                    createdAt = item.createdAt, updatedAt = item.updatedAt
                                ))
                            }
                        }
                        val envelope = BackupEnvelope(encrypted = true,
                            payload = fieldCrypto.encrypt(gson.toJson(mergedList), SessionKeyHolder.get()) ?: "",
                            salt = Base64.encodeToString(cryptoManager.getSalt(), Base64.NO_WRAP))
                        withContext(Dispatchers.IO) { cloudApi.upload(token, gson.toJson(envelope), android.os.Build.MODEL) }
                        settingsManager.setLastSyncedVpsIds(localList.map { it.id.toString() }.toSet())
                        _uiState.value = _uiState.value.copy(
                            cloudSyncLoading = false,
                            cloudSyncLastSync = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                            success = context.getString(R.string.cloud_sync_synced, localList.size)
                        )
                        return@launch
                    }
                }

                // 5. Normal upload (replace cloud with local)
                val envelope = buildCloudEnvelope()
                withContext(Dispatchers.IO) { cloudApi.upload(token, envelope, android.os.Build.MODEL) }
                settingsManager.setLastSyncedVpsIds(localList.map { it.id.toString() }.toSet())
                _uiState.value = _uiState.value.copy(
                    cloudSyncLoading = false,
                    cloudSyncLastSync = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    success = context.getString(R.string.cloud_sync_synced, localList.size)
                )
            } catch (e: Exception) {
                AppLogger.log("CLOUD", "Smart sync failed", e)
                _uiState.value = _uiState.value.copy(
                    cloudSyncLoading = false,
                    error = context.getString(R.string.cloud_upload_failed, e.message ?: "")
                )
            }
        }
    }

    /**
     * Called when a server is deleted locally. If auto-sync is on, prompt whether to delete from cloud.
     * @param onDeleteCloud true = delete from cloud, false = keep cloud copy
     */
    fun onServerDeletedLocally(onDeleteCloud: Boolean) {
        if (!settingsManager.getCloudToken().isNullOrBlank() && _uiState.value.cloudAutoSync) {
            if (onDeleteCloud) {
                cloudSmartSync()
            }
            // If not deleting from cloud, just leave it — next sync will be incremental
        }
    }

    fun cloudUpload() {
        val token = settingsManager.getCloudToken()
        if (token == null) {
            _uiState.value = _uiState.value.copy(error = context.getString(R.string.please_login_first))
            return
        }
        val url = _uiState.value.cloudSyncUrl
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(error = context.getString(R.string.server_url_not_configured))
            return
        }
        _uiState.value = _uiState.value.copy(cloudSyncLoading = true)
        viewModelScope.launch {
            try {
                cloudApi.setBaseUrl(url)
                // Cloud sync uses device key (not backup password)
                val envelope = buildCloudEnvelope()
                withContext(Dispatchers.IO) { cloudApi.upload(token, envelope, android.os.Build.MODEL) }
                settingsManager.setLastSyncedVpsIds((dao?.getAllVpsAsList() ?: emptyList()).map { it.id.toString() }.toSet())
                _uiState.value = _uiState.value.copy(
                    cloudSyncLoading = false,
                    cloudSyncLastSync = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    success = context.getString(R.string.cloud_upload_success)
                )
            } catch (e: Exception) {
                AppLogger.log("CLOUD", "Upload failed", e)
                _uiState.value = _uiState.value.copy(
                    cloudSyncLoading = false,
                    error = context.getString(R.string.cloud_upload_failed, e.message ?: "")
                )
            }
        }
    }

    fun cloudDownload() {
        val token = settingsManager.getCloudToken()
        if (token == null) {
            _uiState.value = _uiState.value.copy(error = context.getString(R.string.please_login_first))
            return
        }
        val url = _uiState.value.cloudSyncUrl
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(error = context.getString(R.string.server_url_not_configured))
            return
        }
        _uiState.value = _uiState.value.copy(cloudSyncLoading = true)
        viewModelScope.launch {
            try {
                cloudApi.setBaseUrl(url)
                val resp = withContext(Dispatchers.IO) { cloudApi.download(token) }
                if (resp.encryptedData.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(cloudSyncLoading = false, error = context.getString(R.string.cloud_no_data_on_server))
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
                        _uiState.value = _uiState.value.copy(cloudSyncLoading = false, error = context.getString(R.string.please_unlock_first))
                        return@launch
                    }
                    // Cloud sync uses device key
                    val key = SessionKeyHolder.get()
                    try {
                        fieldCrypto.decrypt(payload, key) ?: throw Exception("Decrypt failed")
                    } catch (e: Exception) {
                        // If device key fails, try backup password if available
                        val backupSalt = if (envelope?.salt != null) try { Base64.decode(envelope.salt, Base64.NO_WRAP) } catch (_: Exception) { null } else null
                        if (backupSalt != null && settingsManager.isBackupPasswordSet()) {
                            val encPwd = settingsManager.getBackupPasswordEncrypted()
                            val backupPwd = encPwd?.let { fieldCrypto.decrypt(it, key) }
                            if (backupPwd != null) {
                                val backupKey = deriveBackupKey(backupPwd, backupSalt)
                                fieldCrypto.decrypt(payload, backupKey) ?: throw Exception("Decrypt failed")
                            } else throw e
                        } else throw e
                    }
                } else payload
                val type = object : com.google.gson.reflect.TypeToken<List<BackupItem>>() {}.type
                val backupList: List<BackupItem> = gson.fromJson(json, type)
                if (dao == null) dao = runCatching { AppDatabase.getInstance(context).vpsDao() }.getOrNull()
                if (dao == null) {
                    _uiState.value = _uiState.value.copy(cloudSyncLoading = false, error = context.getString(R.string.database_not_initialized))
                    return@launch
                }
                val now = System.currentTimeMillis()
                val count = withContext(Dispatchers.IO) {
                    val allLocal = dao!!.getAllVpsAsList()

                    // Step 1: Deduplicate local entries — keep newest per host:port:username, delete rest
                    val groups = allLocal.groupBy { "${it.host}:${it.port}:${it.username}" }
                    for ((_, entries) in groups) {
                        if (entries.size > 1) {
                            val sorted = entries.sortedByDescending { it.updatedAt }
                            for (dup in sorted.drop(1)) {
                                dao!!.deleteVps(dup)
                                AppLogger.log("CLOUD", "Deleted duplicate: ${dup.alias} (id=${dup.id})")
                            }
                        }
                    }

                    // Step 2: Rebuild map after dedup
                    val existing = dao!!.getAllVpsAsList()
                        .associateBy { "${it.host}:${it.port}:${it.username}" }

                    // Step 3: Upsert backup items
                    var restored = 0
                    for (item in backupList) {
                        val key = "${item.host.ifBlank { "0.0.0.0" }}:${item.port}:${item.username.ifBlank { "root" }}"
                        val existingVps = existing[key]
                        if (existingVps != null) {
                            dao!!.updateVps(existingVps.copy(
                                alias = item.alias.ifBlank { "Unknown" },
                                authType = item.authType,
                                encryptedPassword = item.encryptedPassword,
                                encryptedKeyContent = item.encryptedKeyContent,
                                encryptedKeyPassphrase = item.encryptedKeyPassphrase,
                                updatedAt = now
                            ))
                        } else {
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
                        }
                        restored++
                    }
                    restored
                }
                _uiState.value = _uiState.value.copy(
                    cloudSyncLoading = false,
                    cloudSyncLastSync = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    success = context.getString(R.string.cloud_download_success, count)
                )
            } catch (e: Exception) {
                AppLogger.log("CLOUD", "Download failed", e)
                _uiState.value = _uiState.value.copy(
                    cloudSyncLoading = false,
                    error = context.getString(R.string.cloud_download_failed, e.message ?: "")
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
    // ========== Backup Password ==========
    fun showBackupPasswordDialog() { _uiState.value = _uiState.value.copy(showBackupPasswordDialog = true) }
    fun dismissBackupPasswordDialog() { _uiState.value = _uiState.value.copy(showBackupPasswordDialog = false) }

    fun setBackupPassword(password: String, confirmPassword: String) {
        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(error = "备份密码至少 6 位")
            return
        }
        if (password != confirmPassword) {
            _uiState.value = _uiState.value.copy(error = "两次密码不一致")
            return
        }
        // Store hash for verification display
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray()).joinToString("") { "%02x".format(it) }
        settingsManager.setBackupPasswordHash(hash)
        // Store the backup password encrypted with device key (for backup use)
        if (SessionKeyHolder.isSet()) {
            val encryptedPwd = fieldCrypto.encrypt(password, SessionKeyHolder.get())
            settingsManager.setBackupPasswordEncrypted(encryptedPwd ?: "")
        }
        _uiState.value = _uiState.value.copy(
            showBackupPasswordDialog = false,
            backupPasswordSet = true,
            success = "备份密码已设置"
        )
    }

    fun clearBackupPassword() {
        settingsManager.clearBackupPassword()
        _uiState.value = _uiState.value.copy(backupPasswordSet = false, success = "备份密码已清除")
    }

    /**
     * Derive backup encryption key from backup password.
     * Uses a fixed salt prefix + password for simplicity (backup salt is in the envelope).
     */
    private fun deriveBackupKey(password: String, salt: ByteArray): ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 100_000, 256)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    // ========== GitHub Backup ==========
    fun showGithubDialog() { _uiState.value = _uiState.value.copy(showGithubDialog = true) }
    fun dismissGithubDialog() { _uiState.value = _uiState.value.copy(showGithubDialog = false) }

    fun showLocalBackupDialog() { _uiState.value = _uiState.value.copy(showLocalBackupDialog = true) }
    fun dismissLocalBackupDialog() { _uiState.value = _uiState.value.copy(showLocalBackupDialog = false) }

    fun saveGithubConfig(repo: String, token: String) {
        if (repo.isBlank() || token.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "仓库地址和 Token 不能为空")
            return
        }
        settingsManager.setGitHubBackup(true, repo, token)
        _uiState.value = _uiState.value.copy(
            githubBackupEnabled = true,
            githubRepo = repo,
            githubToken = token,
            showGithubDialog = false,
            success = "GitHub 备份已配置"
        )
    }

    fun toggleGithubBackup(enabled: Boolean) {
        if (enabled) {
            showGithubDialog()
        } else {
            settingsManager.setGitHubBackup(false)
            _uiState.value = _uiState.value.copy(githubBackupEnabled = false, success = "GitHub 备份已关闭")
        }
    }

    fun githubBackup() {
        val repo = _uiState.value.githubRepo
        val token = _uiState.value.githubToken
        if (repo.isBlank() || token.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请先配置 GitHub 仓库")
            return
        }
        if (!settingsManager.isBackupPasswordSet()) {
            _uiState.value = _uiState.value.copy(error = "请先设置备份密码")
            showBackupPasswordDialog()
            return
        }
        _uiState.value = _uiState.value.copy(githubLoading = true)
        viewModelScope.launch {
            try {
                val envelope = buildBackupEnvelope()
                val content = android.util.Base64.encodeToString(envelope.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                val path = "boshconnect/backup_${sdf.format(java.util.Date())}.enc"
                val githubApi = GitHubApi()
                withContext(Dispatchers.IO) {
                    githubApi.uploadFile(repo, path, content, "BoshConnect backup", token)
                }
                _uiState.value = _uiState.value.copy(
                    githubLoading = false,
                    success = "已备份到 GitHub: $path"
                )
            } catch (e: Exception) {
                AppLogger.log("GITHUB", "Backup failed", e)
                _uiState.value = _uiState.value.copy(
                    githubLoading = false,
                    error = "GitHub 备份失败: ${e.message}"
                )
            }
        }
    }

    fun githubRestore() {
        val repo = _uiState.value.githubRepo
        val token = _uiState.value.githubToken
        if (repo.isBlank() || token.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请先配置 GitHub 仓库")
            return
        }
        _uiState.value = _uiState.value.copy(githubLoading = true)
        viewModelScope.launch {
            try {
                val githubApi = GitHubApi()
                // List backup files
                val files = withContext(Dispatchers.IO) {
                    githubApi.listFiles(repo, "boshconnect", token)
                }.filter { it.name.endsWith(".enc") }.sortedByDescending { it.name }
                if (files.isEmpty()) {
                    _uiState.value = _uiState.value.copy(githubLoading = false, error = "GitHub 上没有备份文件")
                    return@launch
                }
                // Get latest backup
                val latest = files.first()
                val fileData = withContext(Dispatchers.IO) {
                    githubApi.getFile(repo, latest.path, token)
                } ?: throw Exception("无法获取文件")
                val content = String(android.util.Base64.decode(fileData.content, android.util.Base64.NO_WRAP), Charsets.UTF_8)
                // Parse and restore (same as local restore)
                val envelope = try { gson.fromJson(content, BackupEnvelope::class.java) } catch (_: Exception) { null }
                if (envelope != null && envelope.format == "sbssh_backup_v1" && envelope.encrypted) {
                    val backupSalt = if (envelope.salt != null) {
                        try { android.util.Base64.decode(envelope.salt, android.util.Base64.NO_WRAP) } catch (_: Exception) { null }
                    } else null
                    if (backupSalt != null) {
                        _pendingPayload = envelope.payload
                        _pendingBackupSalt = backupSalt
                        _uiState.value = _uiState.value.copy(
                            githubLoading = false,
                            showRestorePasswordDialog = true
                        )
                    } else {
                        // Try with current session key
                        if (SessionKeyHolder.isSet()) {
                            val json = fieldCrypto.decrypt(envelope.payload, SessionKeyHolder.get())
                                ?: throw Exception("解密失败")
                            val count = restoreFromJson(json)
                            _uiState.value = _uiState.value.copy(
                                githubLoading = false,
                                success = "从 GitHub 恢复了 $count 台服务器"
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(githubLoading = false, error = "请先解锁应用")
                        }
                    }
                } else {
                    _uiState.value = _uiState.value.copy(githubLoading = false, error = "备份格式不正确")
                }
            } catch (e: Exception) {
                AppLogger.log("GITHUB", "Restore failed", e)
                _uiState.value = _uiState.value.copy(
                    githubLoading = false,
                    error = "GitHub 恢复失败: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() { _uiState.value = _uiState.value.copy(error = null, success = null) }

    class Factory(private val context: Context, private val activity: AppCompatActivity? = null) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(context, activity) as T
    }
}
