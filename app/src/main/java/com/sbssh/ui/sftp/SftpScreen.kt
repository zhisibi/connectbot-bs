@file:OptIn(ExperimentalMaterial3Api::class)

package com.sbssh.ui.sftp

import android.widget.Toast
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
                Toast.makeText(context, "Failed to read file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SFTP") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { uploadLauncher.launch("*/*") }) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Upload")
                    }
                    IconButton(onClick = { viewModel.showCreateFolder() }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                    }
                    IconButton(onClick = { viewModel.loadDirectory(uiState.currentPath) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Up")
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
                            Icon(Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }

            when {
                uiState.isConnecting -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Connecting...")
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
                        Text("Loading files...")
                    }
                }
                uiState.files.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Empty directory", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    val downloadsDir = context.getExternalFilesDir(null) ?: context.cacheDir
                                    viewModel.downloadFile(file, downloadsDir)
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
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.createFolder(folderName) },
                    enabled = folderName.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCreateFolder() }) { Text("Cancel") }
            }
        )
    }

    uiState.showRenameDialog?.let { file ->
        var newName by remember { mutableStateOf(file.name) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissRename() },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.rename(file, newName) },
                    enabled = newName.isNotBlank() && newName != file.name
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRename() }) { Text("Cancel") }
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
            title = { Text("Change Permissions") },
            text = {
                Column {
                    Text("File: ${file.name}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    PermissionRow("Owner", ownerPerms) { ownerPerms = it }
                    PermissionRow("Group", groupPerms) { groupPerms = it }
                    PermissionRow("Other", otherPerms) { otherPerms = it }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Octal: ${permsToInt().toString().padStart(3, '0')}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.chmod(file, permsToInt()) }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissChmod() }) { Text("Cancel") }
            }
        )
    }

    uiState.showDeleteConfirm?.let { file ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDelete() },
            title = { Text("Delete") },
            text = { Text("Delete \"${file.name}\"?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteFile(file) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDelete() }) { Text("Cancel") }
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
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (!file.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Download") },
                            onClick = { showMenu = false; onDownload() },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { showMenu = false; onRename() },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Permissions") },
                        onClick = { showMenu = false; onChmod() },
                        leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
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
