package com.boshconnect.ui.terminal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boshconnect.R
import com.boshconnect.connectbot.data.ConnectBotDatabase
import com.boshconnect.connectbot.data.entity.Host
import com.boshconnect.connectbot.util.SecurePasswordStorage
import com.boshconnect.data.crypto.FieldCryptoManager
import com.boshconnect.data.crypto.SessionKeyHolder
import com.boshconnect.data.db.VpsDao
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
    private val vpsDao: VpsDao,
    private val cbDb: ConnectBotDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow<TerminalUiState>(TerminalUiState.Loading)
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    fun openTerminal(vpsId: Long) {
        _uiState.value = TerminalUiState.Loading
        viewModelScope.launch {
            try {
                val vps = withContext(Dispatchers.IO) { vpsDao.getVpsById(vpsId) }
                    ?: throw IllegalStateException(context.getString(R.string.error_vps_not_found))

                val hostId = withContext(Dispatchers.IO) {
                    val key = SessionKeyHolder.get()
                    val crypto = FieldCryptoManager()

                    var pubkeyId: Long? = null
                    if (vps.authType == "KEY") {
                        val keyContent = crypto.decrypt(vps.encryptedKeyContent, key)
                            ?: throw IllegalStateException(context.getString(R.string.error_private_key_missing))
                        val passphrase = crypto.decrypt(vps.encryptedKeyPassphrase, key)

                        val keyPair = try {
                            if (passphrase.isNullOrBlank()) {
                                com.trilead.ssh2.crypto.PEMDecoder.decode(keyContent.toCharArray(), null)
                            } else {
                                com.trilead.ssh2.crypto.PEMDecoder.decode(keyContent.toCharArray(), passphrase)
                            }
                        } catch (e: Exception) {
                            throw IllegalStateException(context.getString(R.string.error_parse_key_failed, e.message ?: ""))
                        }

                        val algorithm = if (keyPair.private.algorithm == "EdDSA") "Ed25519" else keyPair.private.algorithm
                        val nicknameBase = "${vps.alias}_key"
                        val existing = cbDb.pubkeyDao().getByNickname(nicknameBase)
                        val nickname = if (existing == null) nicknameBase else "${nicknameBase}_${System.currentTimeMillis()}"

                        val pubkey = com.boshconnect.connectbot.data.entity.Pubkey(
                            id = 0,
                            nickname = nickname,
                            type = algorithm,
                            encrypted = false,
                            startup = false,
                            confirmation = false,
                            createdDate = System.currentTimeMillis(),
                            privateKey = keyPair.private.encoded,
                            publicKey = keyPair.public.encoded
                        )
                        pubkeyId = cbDb.pubkeyDao().insert(pubkey)
                    }

                    val existingHostId = vps.connectbotHostId
                    if (existingHostId != null) {
                        val existingHost = cbDb.hostDao().getById(existingHostId)
                        if (existingHost != null) {
                            // Don't blindly set nickname to alias — another host may already use it.
                            val desiredNickname = vps.alias
                            val conflict = cbDb.hostDao().getByNickname(desiredNickname)
                            val finalNickname = if (conflict != null && conflict.id != existingHost.id) {
                                "${desiredNickname}_${System.currentTimeMillis()}"
                            } else {
                                desiredNickname
                            }
                            val updatedHost = existingHost.copy(
                                nickname = finalNickname,
                                hostname = vps.host,
                                port = vps.port,
                                username = vps.username,
                                pubkeyId = pubkeyId ?: existingHost.pubkeyId,
                                useKeys = vps.authType == "KEY"
                            )
                            cbDb.hostDao().update(updatedHost)
                            existingHostId
                        } else {
                            null
                        }
                    } else {
                        null
                    } ?: run {
                        // Host.nickname is UNIQUE in ConnectBot DB. Restored servers may contain duplicate aliases,
                        // so ensure nickname is unique before insert.
                        val nicknameBase = vps.alias.ifBlank { "server" }
                        val nickname = run {
                            val exists = cbDb.hostDao().getByNickname(nicknameBase)
                            if (exists == null) nicknameBase else "${nicknameBase}_${System.currentTimeMillis()}"
                        }

                        val host = Host.createSshHost(
                            nickname = nickname,
                            hostname = vps.host,
                            port = vps.port,
                            username = vps.username
                        ).copy(
                            pubkeyId = pubkeyId ?: -1L,
                            useKeys = vps.authType == "KEY"
                        )
                        val newId = cbDb.hostDao().insert(host)
                        vpsDao.updateConnectbotHostId(vpsId, newId)
                        newId
                    }
                }

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
                _uiState.value = TerminalUiState.Error(e.message ?: context.getString(R.string.error_failed_open_terminal))
            }
        }
    }
}
