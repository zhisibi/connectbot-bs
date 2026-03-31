package com.sbssh.ui.vpslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sbssh.data.crypto.FieldCryptoManager
import com.sbssh.data.crypto.SessionKeyHolder
import com.sbssh.data.db.AppDatabase
import com.sbssh.data.db.VpsEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AddEditVpsUiState(
    val alias: String = "",
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val authType: String = "PASSWORD",
    val password: String = "",
    val keyContent: String = "",
    val keyPassphrase: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

class AddEditVpsViewModel(private val vpsId: Long? = null) : ViewModel() {

    private val crypto = FieldCryptoManager()
    private var dao = runCatching { AppDatabase.getInstance().vpsDao() }.getOrNull()
    private val _uiState = MutableStateFlow(AddEditVpsUiState())
    val uiState: StateFlow<AddEditVpsUiState> = _uiState.asStateFlow()

    init {
        if (dao == null) {
            _uiState.value = _uiState.value.copy(error = "Database not initialized")
        } else if (vpsId != null) {
            viewModelScope.launch {
                try {
                    val vps = dao!!.getVpsById(vpsId)
                    if (vps != null) {
                        val key = SessionKeyHolder.get()
                        _uiState.value = AddEditVpsUiState(
                            alias = vps.alias,
                            host = vps.host,
                            port = vps.port.toString(),
                            username = vps.username,
                            authType = vps.authType,
                            password = crypto.decrypt(vps.encryptedPassword, key) ?: "",
                            keyContent = crypto.decrypt(vps.encryptedKeyContent, key) ?: "",
                            keyPassphrase = crypto.decrypt(vps.encryptedKeyPassphrase, key) ?: ""
                        )
                    }
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to load server")
                }
            }
        }
    }

    fun updateAlias(alias: String) { _uiState.value = _uiState.value.copy(alias = alias) }
    fun updateHost(host: String) { _uiState.value = _uiState.value.copy(host = host) }
    fun updatePort(port: String) { _uiState.value = _uiState.value.copy(port = port) }
    fun updateUsername(username: String) { _uiState.value = _uiState.value.copy(username = username) }
    fun updateAuthType(authType: String) { _uiState.value = _uiState.value.copy(authType = authType) }
    fun updatePassword(password: String) { _uiState.value = _uiState.value.copy(password = password) }
    fun updateKeyContent(keyContent: String) { _uiState.value = _uiState.value.copy(keyContent = keyContent) }
    fun updateKeyPassphrase(passphrase: String) { _uiState.value = _uiState.value.copy(keyPassphrase = passphrase) }

    fun save() {
        val state = _uiState.value
        if (state.alias.isBlank()) {
            _uiState.value = state.copy(error = "Alias is required")
            return
        }
        if (state.host.isBlank()) {
            _uiState.value = state.copy(error = "Host is required")
            return
        }
        if (state.username.isBlank()) {
            _uiState.value = state.copy(error = "Username is required")
            return
        }
        val portInt = state.port.toIntOrNull() ?: 22
        if (state.authType == "PASSWORD" && state.password.isBlank()) {
            _uiState.value = state.copy(error = "Password is required")
            return
        }
        if (state.authType == "KEY" && state.keyContent.isBlank()) {
            _uiState.value = state.copy(error = "Private key is required")
            return
        }

        _uiState.value = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val key = SessionKeyHolder.get()
                val now = System.currentTimeMillis()
                if (vpsId == null) {
                    dao?.insertVps(
                        VpsEntity(
                            alias = state.alias.trim(),
                            host = state.host.trim(),
                            port = portInt,
                            username = state.username.trim(),
                            authType = state.authType,
                            encryptedPassword = if (state.authType == "PASSWORD") crypto.encrypt(state.password, key) else null,
                            encryptedKeyContent = if (state.authType == "KEY") crypto.encrypt(state.keyContent, key) else null,
                            encryptedKeyPassphrase = if (state.authType == "KEY" && state.keyPassphrase.isNotBlank()) crypto.encrypt(state.keyPassphrase, key) else null,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                } else {
                    val existing = dao?.getVpsById(vpsId) ?: return@launch
                    dao?.updateVps(
                        existing.copy(
                            alias = state.alias.trim(),
                            host = state.host.trim(),
                            port = portInt,
                            username = state.username.trim(),
                            authType = state.authType,
                            encryptedPassword = if (state.authType == "PASSWORD") crypto.encrypt(state.password, key) else null,
                            encryptedKeyContent = if (state.authType == "KEY") crypto.encrypt(state.keyContent, key) else null,
                            encryptedKeyPassphrase = if (state.authType == "KEY" && state.keyPassphrase.isNotBlank()) crypto.encrypt(state.keyPassphrase, key) else null,
                            updatedAt = now
                        )
                    )
                }
                _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Save failed")
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    class Factory(private val vpsId: Long?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddEditVpsViewModel(vpsId) as T
        }
    }
}
