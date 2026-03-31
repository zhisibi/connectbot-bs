package com.sbssh.ui.terminal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.sbssh.connectbot.data.ConnectBotDatabase
import com.sbssh.connectbot.data.entity.Host
import com.sbssh.connectbot.util.SecurePasswordStorage
import com.sbssh.data.crypto.FieldCryptoManager
import com.sbssh.data.crypto.SessionKeyHolder
import com.sbssh.data.db.VpsDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class TerminalUiState {
    data object Loading : TerminalUiState()
    data class Error(val message: String) : TerminalUiState()
    data class Ready(val hostId: Long) : TerminalUiState()
}

@HiltViewModel
class TerminalScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vpsDao: VpsDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<TerminalUiState>(TerminalUiState.Loading)
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    fun openTerminal(vpsId: Long) {
        _uiState.value = TerminalUiState.Loading
        viewModelScope.launch {
            try {
                val vps = withContext(Dispatchers.IO) { vpsDao.getVpsById(vpsId) }
                    ?: throw IllegalStateException("VPS not found")

                val cbDb = Room.databaseBuilder(
                    context.applicationContext,
                    ConnectBotDatabase::class.java,
                    "connectbot.db"
                )
                    .addMigrations(ConnectBotDatabase.MIGRATION_4_5)
                    .build()

                val hostId = withContext(Dispatchers.IO) {
                    if (vps.connectbotHostId != null) {
                        vps.connectbotHostId
                    } else {
                        val host = Host.createSshHost(
                            nickname = vps.alias,
                            hostname = vps.host,
                            port = vps.port,
                            username = vps.username
                        )
                        val newId = cbDb.hostDao().insert(host)
                        vpsDao.updateConnectbotHostId(vpsId, newId)
                        newId
                    }
                } ?: throw IllegalStateException("Failed to create host")

                if (vps.authType == "PASSWORD" && vps.encryptedPassword != null) {
                    val key = SessionKeyHolder.get()
                    val crypto = FieldCryptoManager()
                    val password = crypto.decrypt(vps.encryptedPassword, key)
                    if (!password.isNullOrBlank()) {
                        SecurePasswordStorage(context).savePassword(hostId, password)
                    }
                }

                _uiState.value = TerminalUiState.Ready(hostId)
            } catch (e: Exception) {
                _uiState.value = TerminalUiState.Error(e.message ?: "Failed to open terminal")
            }
        }
    }
}
