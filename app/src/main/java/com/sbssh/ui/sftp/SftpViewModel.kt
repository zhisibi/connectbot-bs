package com.sbssh.ui.sftp

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sbssh.data.crypto.FieldCryptoManager
import com.sbssh.data.crypto.SessionKeyHolder
import com.sbssh.data.db.AppDatabase
import com.sbssh.data.db.VpsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SftpUiState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = true,
    val currentPath: String = "/",
    val files: List<SftpFileInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCreateFolderDialog: Boolean = false,
    val showRenameDialog: SftpFileInfo? = null,
    val showChmodDialog: SftpFileInfo? = null,
    val showDeleteConfirm: SftpFileInfo? = null,
    val uploadProgress: String? = null,
    val connectionError: String? = null
)

class SftpViewModel(private val vpsId: Long, private val context: Context) : ViewModel() {

    private var dao = runCatching { AppDatabase.getInstance().vpsDao() }.getOrNull()
    private val manager = SftpManager()

    private val _uiState = MutableStateFlow(SftpUiState())
    val uiState: StateFlow<SftpUiState> = _uiState.asStateFlow()

    private var vps: VpsEntity? = null

    init {
        if (dao == null) {
            _uiState.value = _uiState.value.copy(
                isConnecting = false,
                connectionError = "Database not initialized"
            )
        } else {
            viewModelScope.launch {
                vps = dao!!.getVpsById(vpsId)
                vps?.let { v ->
                    try {
                        val key = SessionKeyHolder.get()
                        val crypto = FieldCryptoManager()
                        val success = manager.connect(
                            host = v.host,
                            port = v.port,
                            username = v.username,
                            authType = v.authType,
                            password = crypto.decrypt(v.encryptedPassword, key),
                            keyContent = crypto.decrypt(v.encryptedKeyContent, key),
                            keyPassphrase = crypto.decrypt(v.encryptedKeyPassphrase, key)
                        )
                        if (success) {
                            val pwd = manager.getCurrentPath()
                            _uiState.value = _uiState.value.copy(isConnected = true, isConnecting = false, currentPath = pwd)
                            loadDirectory(pwd)
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isConnecting = false,
                                connectionError = "Failed to connect to ${v.host}"
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isConnecting = false,
                            connectionError = e.message ?: "Connection failed"
                        )
                    }
                }
            }
        }
    }

    fun loadDirectory(path: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val files = manager.listDirectory(path)
                _uiState.value = _uiState.value.copy(
                    files = files,
                    currentPath = path,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to list directory"
                )
            }
        }
    }

    fun navigateUp() {
        val current = _uiState.value.currentPath
        if (current == "/") return
        val parent = current.substringBeforeLast("/").ifEmpty { "/" }
        loadDirectory(parent)
    }

    fun navigateTo(file: SftpFileInfo) {
        if (file.isDirectory) {
            loadDirectory(file.path)
        }
    }

    fun showCreateFolder() { _uiState.value = _uiState.value.copy(showCreateFolderDialog = true) }
    fun dismissCreateFolder() { _uiState.value = _uiState.value.copy(showCreateFolderDialog = false) }

    fun createFolder(name: String) {
        val path = "${_uiState.value.currentPath}/$name"
        viewModelScope.launch {
            try {
                manager.mkdir(path)
                _uiState.value = _uiState.value.copy(showCreateFolderDialog = false)
                loadDirectory(_uiState.value.currentPath)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to create folder")
            }
        }
    }

    fun showRename(file: SftpFileInfo) { _uiState.value = _uiState.value.copy(showRenameDialog = file) }
    fun dismissRename() { _uiState.value = _uiState.value.copy(showRenameDialog = null) }

    fun rename(oldFile: SftpFileInfo, newName: String) {
        val newPath = "${_uiState.value.currentPath}/$newName"
        viewModelScope.launch {
            try {
                manager.rename(oldFile.path, newPath)
                _uiState.value = _uiState.value.copy(showRenameDialog = null)
                loadDirectory(_uiState.value.currentPath)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to rename")
            }
        }
    }

    fun showChmod(file: SftpFileInfo) { _uiState.value = _uiState.value.copy(showChmodDialog = file) }
    fun dismissChmod() { _uiState.value = _uiState.value.copy(showChmodDialog = null) }

    fun chmod(file: SftpFileInfo, permissions: Int) {
        viewModelScope.launch {
            try {
                manager.chmod(file.path, permissions)
                _uiState.value = _uiState.value.copy(showChmodDialog = null)
                loadDirectory(_uiState.value.currentPath)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to change permissions")
            }
        }
    }

    fun showDelete(file: SftpFileInfo) { _uiState.value = _uiState.value.copy(showDeleteConfirm = file) }
    fun dismissDelete() { _uiState.value = _uiState.value.copy(showDeleteConfirm = null) }

    fun deleteFile(file: SftpFileInfo) {
        viewModelScope.launch {
            try {
                manager.deleteFile(file.path)
                _uiState.value = _uiState.value.copy(showDeleteConfirm = null)
                loadDirectory(_uiState.value.currentPath)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to delete")
            }
        }
    }

    fun uploadFile(localPath: String) {
        val localFile = File(localPath)
        if (!localFile.exists()) return
        val remotePath = "${_uiState.value.currentPath}/${localFile.name}"

        _uiState.value = _uiState.value.copy(uploadProgress = "Uploading ${localFile.name}...")
        viewModelScope.launch {
            try {
                manager.uploadFile(localFile, remotePath)
                _uiState.value = _uiState.value.copy(uploadProgress = null)
                loadDirectory(_uiState.value.currentPath)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Upload complete", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    uploadProgress = null,
                    error = e.message ?: "Upload failed"
                )
            }
        }
    }

    fun downloadFile(file: SftpFileInfo, localDir: File) {
        val localFile = File(localDir, file.name)
        _uiState.value = _uiState.value.copy(uploadProgress = "Downloading ${file.name}...")
        viewModelScope.launch {
            try {
                manager.downloadFile(file.path, localFile)
                _uiState.value = _uiState.value.copy(uploadProgress = null)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Downloaded to ${localFile.absolutePath}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    uploadProgress = null,
                    error = e.message ?: "Download failed"
                )
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    override fun onCleared() {
        super.onCleared()
        manager.disconnect()
    }

    class Factory(private val vpsId: Long, private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SftpViewModel(vpsId, context) as T
        }
    }
}
