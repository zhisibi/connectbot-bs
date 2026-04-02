package com.sbssh.ui.sftp

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import com.sbssh.R
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sbssh.data.crypto.FieldCryptoManager
import com.sbssh.data.crypto.SessionKeyHolder
import com.sbssh.data.db.AppDatabase
import com.sbssh.data.db.VpsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DownloadTask(
    val id: Long = System.nanoTime(),
    val fileName: String,
    val remotePath: String,
    val progress: String = "0%",
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val error: String? = null
)

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
    val connectionError: String? = null,
    val query: String = "",
    val sortType: SortType = SortType.NAME,
    val downloadTasks: List<DownloadTask> = emptyList(),
    val showDownloadList: Boolean = false
)

enum class SortType { NAME, TIME, TYPE }

class SftpViewModel(private val vpsId: Long, private val context: Context) : ViewModel() {

    private var dao = runCatching { AppDatabase.getInstance().vpsDao() }.getOrNull()
    private val manager = SftpManager()

    private val _uiState = MutableStateFlow(SftpUiState())
    private var lastFiles: List<SftpFileInfo> = emptyList()
    val uiState: StateFlow<SftpUiState> = _uiState.asStateFlow()

    private var vps: VpsEntity? = null

    init {
        if (dao == null) {
            _uiState.value = _uiState.value.copy(
                isConnecting = false,
                connectionError = context.getString(R.string.sftp_db_not_initialized)
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
                                connectionError = context.getString(R.string.sftp_connect_failed, v.host)
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isConnecting = false,
                            connectionError = e.message ?: context.getString(R.string.sftp_connection_failed)
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
                lastFiles = files
                _uiState.value = _uiState.value.copy(
                    files = applyFilterSort(files, _uiState.value.query, _uiState.value.sortType),
                    currentPath = path,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: context.getString(R.string.sftp_list_failed)
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
                _uiState.value = _uiState.value.copy(error = e.message ?: context.getString(R.string.sftp_create_folder_failed))
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
                _uiState.value = _uiState.value.copy(error = e.message ?: context.getString(R.string.sftp_rename_failed))
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
                _uiState.value = _uiState.value.copy(error = e.message ?: context.getString(R.string.sftp_chmod_failed))
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
                _uiState.value = _uiState.value.copy(error = e.message ?: context.getString(R.string.sftp_delete_failed))
            }
        }
    }

    fun uploadFile(localPath: String) {
        val localFile = File(localPath)
        if (!localFile.exists()) return
        val remotePath = "${_uiState.value.currentPath}/${localFile.name}"

        _uiState.value = _uiState.value.copy(uploadProgress = context.getString(R.string.sftp_uploading, localFile.name))
        viewModelScope.launch {
            try {
                manager.uploadFile(localFile, remotePath)
                _uiState.value = _uiState.value.copy(uploadProgress = null)
                loadDirectory(_uiState.value.currentPath)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.toast_upload_complete), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    uploadProgress = null,
                    error = e.message ?: context.getString(R.string.sftp_upload_failed)
                )
            }
        }
    }

    fun downloadFile(file: SftpFileInfo, localDir: File) {
        val localFile = File(localDir, file.name)
        _uiState.value = _uiState.value.copy(uploadProgress = context.getString(R.string.sftp_downloading, file.name))
        viewModelScope.launch {
            try {
                manager.downloadFile(file.path, localFile)
                _uiState.value = _uiState.value.copy(uploadProgress = null)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.toast_downloaded_to, localFile.absolutePath), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    uploadProgress = null,
                    error = e.message ?: context.getString(R.string.sftp_download_failed)
                )
            }
        }
    }

    private val activeJobs = mutableMapOf<Long, Job>()

    fun downloadFileToUri(file: SftpFileInfo, uri: android.net.Uri) {
        val task = DownloadTask(
            fileName = file.name,
            remotePath = file.path,
            totalBytes = file.size
        )
        _uiState.value = _uiState.value.copy(
            downloadTasks = _uiState.value.downloadTasks + task,
            showDownloadList = true
        )
        val job = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val outputStream = context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Cannot open output stream")
                    outputStream.use { os ->
                        manager.downloadFileWithProgress(file.path, os) { downloaded, total ->
                            val pct = if (total > 0) "${(downloaded * 100 / total)}%" else "$downloaded B"
                            val tasks = _uiState.value.downloadTasks.toMutableList()
                            val idx = tasks.indexOfFirst { it.id == task.id }
                            if (idx >= 0) {
                                tasks[idx] = tasks[idx].copy(
                                    progress = pct,
                                    bytesDownloaded = downloaded,
                                    totalBytes = total
                                )
                                _uiState.value = _uiState.value.copy(downloadTasks = tasks)
                            }
                        }
                    }
                }
                val tasks = _uiState.value.downloadTasks.toMutableList()
                val idx = tasks.indexOfFirst { it.id == task.id }
                if (idx >= 0) {
                    tasks[idx] = tasks[idx].copy(isCompleted = true, progress = "100%")
                    _uiState.value = _uiState.value.copy(downloadTasks = tasks)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved to Downloads: ${file.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                val tasks = _uiState.value.downloadTasks.toMutableList()
                val idx = tasks.indexOfFirst { it.id == task.id }
                if (idx >= 0) {
                    tasks[idx] = tasks[idx].copy(error = e.message ?: "Failed")
                    _uiState.value = _uiState.value.copy(downloadTasks = tasks)
                }
            } finally {
                activeJobs.remove(task.id)
            }
        }
        activeJobs[task.id] = job
    }

    fun cancelDownload(taskId: Long) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        val tasks = _uiState.value.downloadTasks.toMutableList()
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx >= 0) {
            tasks[idx] = tasks[idx].copy(isCancelled = true)
            _uiState.value = _uiState.value.copy(downloadTasks = tasks)
        }
    }

    fun clearCompletedDownloads() {
        _uiState.value = _uiState.value.copy(
            downloadTasks = _uiState.value.downloadTasks.filter { !it.isCompleted && !it.isCancelled && it.error == null }
        )
    }

    fun toggleDownloadList() {
        _uiState.value = _uiState.value.copy(showDownloadList = !_uiState.value.showDownloadList)
    }

    fun dismissDownloadList() {
        _uiState.value = _uiState.value.copy(showDownloadList = false)
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            query = query,
            files = applyFilterSort(lastFiles, query, _uiState.value.sortType)
        )
    }

    fun updateSort(sortType: SortType) {
        _uiState.value = _uiState.value.copy(
            sortType = sortType,
            files = applyFilterSort(lastFiles, _uiState.value.query, sortType)
        )
    }

    private fun applyFilterSort(
        files: List<SftpFileInfo>,
        query: String,
        sortType: SortType
    ): List<SftpFileInfo> {
        val filtered = if (query.isBlank()) {
            files
        } else {
            files.filter { it.name.contains(query, ignoreCase = true) }
        }

        val sorted = when (sortType) {
            SortType.NAME -> filtered.sortedWith(compareByDescending<SftpFileInfo> { it.isDirectory }.thenBy { it.name.lowercase() })
            SortType.TIME -> filtered.sortedWith(compareByDescending<SftpFileInfo> { it.isDirectory }.thenByDescending { it.modifiedTime })
            SortType.TYPE -> filtered.sortedWith(compareByDescending<SftpFileInfo> { it.isDirectory }.thenBy { it.permissions })
        }
        return sorted
    }

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
