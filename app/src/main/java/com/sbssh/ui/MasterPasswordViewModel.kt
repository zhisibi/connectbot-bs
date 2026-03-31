package com.sbssh.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sbssh.SbsshApp
import com.sbssh.data.crypto.CryptoManager
import com.sbssh.data.crypto.SessionKeyHolder
import com.sbssh.data.db.AppDatabase
import com.sbssh.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false,
    val isFirstLaunch: Boolean = true,
    val biometricAvailable: Boolean = false
)

class MasterPasswordViewModel(
    private val cryptoManager: CryptoManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        val isFirst = cryptoManager.isFirstLaunch()
        _uiState.value = _uiState.value.copy(
            isFirstLaunch = isFirst,
            biometricAvailable = cryptoManager.isBiometricEnabled()
        )
    }

    fun setPassword(password: String, confirmPassword: String) {
        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(error = "Password must be at least 6 characters")
            return
        }
        if (password != confirmPassword) {
            _uiState.value = _uiState.value.copy(error = "Passwords do not match")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                AppLogger.log("AUTH", "setPassword: starting, pwdLen=${password.length}")
                val salt = cryptoManager.generateSalt()
                val keyBytes = cryptoManager.deriveKey(password, salt)
                AppLogger.log("AUTH", "setPassword: salt generated len=${salt.size}, key derived len=${keyBytes.size}")

                // Store session key for field-level encryption
                SessionKeyHolder.set(keyBytes)

                // Init plain Room database
                AppDatabase.close()
                AppDatabase.getInstance(SbsshApp.instance)
                AppLogger.log("AUTH", "setPassword: database initialized")

                // Now persist salt + password verification hash
                cryptoManager.saveSalt(salt)
                cryptoManager.setPasswordVerification(password, salt)
                AppLogger.log("AUTH", "setPassword: salt and hash saved to prefs")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAuthenticated = true
                )
                AppLogger.log("AUTH", "setPassword: SUCCESS")
            } catch (e: Exception) {
                AppLogger.log("AUTH", "setPassword: FAILED", e)
                SessionKeyHolder.clear()
                cryptoManager.clearMasterPasswordState()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to set password"
                )
            }
        }
    }

    fun unlock(password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                if (!cryptoManager.isMasterPasswordSet()) {
                    cryptoManager.clearMasterPasswordState()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isFirstLaunch = true,
                        error = "Previous setup was incomplete. Please set a new master password."
                    )
                    return@launch
                }

                if (cryptoManager.verifyMasterPassword(password)) {
                    val salt = cryptoManager.getSalt()
                    val keyBytes = cryptoManager.deriveKey(password, salt)

                    // Store session key for field-level decryption
                    SessionKeyHolder.set(keyBytes)

                    // Init plain Room database
                    AppDatabase.close()
                    AppDatabase.getInstance(SbsshApp.instance)

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAuthenticated = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Incorrect password"
                    )
                }
            } catch (e: Exception) {
                SessionKeyHolder.clear()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to unlock"
                )
            }
        }
    }

    fun unlockWithBiometric(decryptedKey: ByteArray) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                SessionKeyHolder.set(decryptedKey)
                AppDatabase.close()
                AppDatabase.getInstance(SbsshApp.instance)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAuthenticated = true
                )
            } catch (e: Exception) {
                SessionKeyHolder.clear()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Biometric unlock failed"
                )
            }
        }
    }

    fun enableBiometric(password: String) {
        viewModelScope.launch {
            try {
                val salt = cryptoManager.getSalt()
                val keyBytes = cryptoManager.deriveKey(password, salt)
                cryptoManager.enableBiometric(keyBytes)
                _uiState.value = _uiState.value.copy(biometricAvailable = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    class Factory(private val cryptoManager: CryptoManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MasterPasswordViewModel(cryptoManager) as T
        }
    }
}
