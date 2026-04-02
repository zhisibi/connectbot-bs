@file:OptIn(ExperimentalMaterial3Api::class)

package com.sbssh.ui.sftp

import android.widget.Toast
import android.os.Environment
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sbssh.R
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreen(
    vpsId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SftpViewModel = viewModel(
        factory = SftpViewModel.Factory(vpsId, context)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val tempFile = File(context.cacheDir, "sftp_upload_temp")
                context.contentResolver.openInputStream(it)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                // Get original filename
                val cursor = context.contentResolver.query(it, null, null, null, null)
                val name = cursor?.use { c ->
                    val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    c.moveToFirst()
                    c.getString(nameIndex)
                } ?: "upload_file"
                val namedFile = File(context.cacheDir, name)
                tempFile.renameTo(namedFile)
                viewModel.uploadFile(namedFile.absolutePath)
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.toast_failed_read_file), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        OutlinedTextField(
                            value = uiState.query,
                            onValueChange = { viewModel.updateQuery(it) },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.sftp_search_hint)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(stringResource(R.string.label_sftp))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // Download list button (shows badge if active downloads)
                    val downloadCount = uiState.downloadTasks.count { !it.isCompleted && !it.isCancelled && it.error == null }
                    IconButton(onClick = { viewModel.toggleDownloadList() }) {
                        BadgedBox(
                            badge = {
                                if (downloadCount > 0) {
                                    Badge { Text("$downloadCount") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Downloads")
                        }
                    }
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.content_desc_search))
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.content_desc_sort))
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sftp_sort_name)) },
                                onClick = { showSortMenu = false; viewModel.updateSort(SortType.NAME) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sftp_sort_time)) },
                                onClick = { showSortMenu = false; viewModel.updateSort(SortType.TIME) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sftp_sort_type)) },
                                onClick = { showSortMenu = false; viewModel.updateSort(SortType.TYPE) }
                            )
                        }
                    }
                    IconButton(onClick = { uploadLauncher.launch("*/*") }) {
                        Icon(Icons.Default.UploadFile, contentDescription = stringResource(R.string.content_desc_upload))
                    }
                    IconButton(onClick = { viewModel.showCreateFolder() }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.content_desc_new_folder))
                    }
                    IconButton(onClick = { viewModel.loadDirectory(uiState.currentPath) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.content_desc_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Current path bar
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.navigateUp() }) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.content_desc_up))
                    }
                    Text(
                        uiState.currentPath,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Upload progress
            uiState.uploadProgress?.let { progress ->
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    progress,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Error display
            uiState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.content_desc_dismiss))
                        }
                    }
                }
            }

            when {
                uiState.isConnecting -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.sftp_connecting))
                        }
                    }
                }
                uiState.connectionError != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(uiState.connectionError!!, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.sftp_loading_files))
                    }
                }
                uiState.files.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.sftp_empty_directory), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(uiState.files, key = { it.path }) { file ->
                            SftpFileItem(
                                file = file,
                                onClick = { viewModel.navigateTo(file) },
                                onDownload = {
                                    // Use MediaStore.Downloads for Android 10+ scoped storage compatibility
                                    val contentValues = android.content.ContentValues().apply {
                                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, file.name)
                                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                    }
                                    val uri = context.contentResolver.insert(
                                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                        contentValues
                                    )
                                    if (uri != null) {
                                        viewModel.downloadFileToUri(file, uri)
                                    } else {
                                        android.widget.Toast.makeText(context, "Failed to create file", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onRename = { viewModel.showRename(file) },
                                onChmod = { viewModel.showChmod(file) },
                                onDelete = { viewModel.showDelete(file) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (uiState.showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissCreateFolder() },
            title = { Text(stringResource(R.string.sftp_new_folder)) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(stringResource(R.string.sftp_folder_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.createFolder(folderName) },
                    enabled = folderName.isNotBlank()
                ) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCreateFolder() }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    uiState.showRenameDialog?.let { file ->
        var newName by remember { mutableStateOf(file.name) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissRename() },
            title = { Text(stringResource(R.string.sftp_rename)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.sftp_new_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.rename(file, newName) },
                    enabled = newName.isNotBlank() && newName != file.name
                ) { Text(stringResource(R.string.sftp_rename)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRename() }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    uiState.showChmodDialog?.let { file ->
        var ownerPerms by remember { mutableStateOf("rwx") }
        var groupPerms by remember { mutableStateOf("r-x") }
        var otherPerms by remember { mutableStateOf("r-x") }

        fun permsToInt(): Int {
            var result = 0
            if ('r' in ownerPerms) result += 400
            if ('w' in ownerPerms) result += 200
            if ('x' in ownerPerms) result += 100
            if ('r' in groupPerms) result += 40
            if ('w' in groupPerms) result += 20
            if ('x' in groupPerms) result += 10
            if ('r' in otherPerms) result += 4
            if ('w' in otherPerms) result += 2
            if ('x' in otherPerms) result += 1
            return result
        }

        AlertDialog(
            onDismissRequest = { viewModel.dismissChmod() },
            title = { Text(stringResource(R.string.sftp_change_permissions)) },
            text = {
                Column {
                    Text(stringResource(R.string.sftp_file_label, file.name), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    PermissionRow(stringResource(R.string.permission_owner), ownerPerms) { ownerPerms = it }
                    PermissionRow(stringResource(R.string.permission_group), groupPerms) { groupPerms = it }
                    PermissionRow(stringResource(R.string.permission_other), otherPerms) { otherPerms = it }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.permission_octal, permsToInt().toString().padStart(3, '0')),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.chmod(file, permsToInt()) }) { Text(stringResource(R.string.action_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissChmod() }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    uiState.showDeleteConfirm?.let { file ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDelete() },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(stringResource(R.string.sftp_delete_confirm, file.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteFile(file) }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDelete() }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    // Download list dialog
    if (uiState.showDownloadList) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDownloadList() },
            title = {
                Row(
                    Modifier.fillMaxWidth(),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Text("Downloads")
                    if (uiState.downloadTasks.any { it.isCompleted || it.isCancelled || it.error != null }) {
                        TextButton(onClick = { viewModel.clearCompletedDownloads() }) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            text = {
                if (uiState.downloadTasks.isEmpty()) {
                    Text("No downloads", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (task in uiState.downloadTasks.reversed()) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        task.error != null -> MaterialTheme.colorScheme.errorContainer
                                        task.isCancelled -> MaterialTheme.colorScheme.surfaceVariant
                                        task.isCompleted -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            task.fileName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            when {
                                                task.error != null -> "Error: ${task.error}"
                                                task.isCancelled -> "Cancelled"
                                                task.isCompleted -> "Completed"
                                                else -> "Downloading..."
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!task.isCompleted && !task.isCancelled && task.error == null) {
                                        IconButton(
                                            onClick = { viewModel.cancelDownload(task.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Cancel",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDownloadList() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun PermissionRow(label: String, perms: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(60.dp), style = MaterialTheme.typography.bodySmall)
        PermissionToggle("R", 'r' in perms) {
            onChange(if ('r' in perms) perms.replace("r", "-") else perms.replace("-", "r"))
        }
        PermissionToggle("W", 'w' in perms) {
            onChange(if ('w' in perms) perms.replace("w", "-") else perms.replace("-", "w"))
        }
        PermissionToggle("X", 'x' in perms) {
            onChange(if ('x' in perms) perms.replace("x", "-") else perms.replace("-", "x"))
        }
    }
}

@Composable
private fun PermissionToggle(label: String, enabled: Boolean, onToggle: () -> Unit) {
    FilterChip(
        selected = enabled,
        onClick = onToggle,
        label = { Text(label) },
        modifier = Modifier.padding(end = 4.dp)
    )
}

@Composable
private fun SftpFileItem(
    file: SftpFileInfo,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onChmod: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (file.isDirectory) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    buildString {
                        if (!file.isDirectory) append(formatSize(file.size))
                        append("  ")
                        append(file.permissions)
                        append("  ")
                        append(dateFormat.format(Date(file.modifiedTime)))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.content_desc_more))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (!file.isDirectory) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_download)) },
                            onClick = { showMenu = false; onDownload() },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sftp_rename)) },
                        onClick = { showMenu = false; onRename() },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_permissions)) },
                        onClick = { showMenu = false; onChmod() },
                        leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = { showMenu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}
