package com.sbssh.ui.vpslist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.sbssh.R
import com.sbssh.data.db.VpsEntity
import com.sbssh.ui.LocalTerminalManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpsListScreen(
    onAddVps: () -> Unit,
    onEditVps: (Long) -> Unit,
    onConnectTerminal: (Long) -> Unit,
    onConnectSftp: (Long) -> Unit,
    onSettings: () -> Unit
) {
    val viewModel: VpsListViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val terminalManager = LocalTerminalManager.current
    val bridges by terminalManager?.bridgesFlow?.collectAsState() ?: remember { mutableStateOf(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_servers)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.content_desc_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddVps) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_server))
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.loading_servers))
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.error_load_failed), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(uiState.error ?: stringResource(R.string.error_unknown))
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.retry() }) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        } else if (uiState.vpsList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🖥️", style = MaterialTheme.typography.displaySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.no_servers_yet),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.tap_add_server),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.vpsList, key = { it.id }) { vps ->
                    val bridge = bridges.firstOrNull { it.host?.id == vps.connectbotHostId }
                    val isConnected = bridge?.isSessionOpen == true && bridge.isDisconnected.not()
                    val isDisconnected = bridge?.isDisconnected == true

                    VpsCard(
                        vps = vps,
                        isConnected = isConnected,
                        isDisconnected = isDisconnected,
                        onEdit = { onEditVps(vps.id) },
                        onDelete = { viewModel.confirmDelete(vps.id) },
                        onConnectTerminal = { onConnectTerminal(vps.id) },
                        onConnectSftp = { onConnectSftp(vps.id) },
                        onDisconnect = {
                            bridge?.dispatchDisconnect(true)
                        }
                    )
                }
            }
        }
    }

    uiState.showDeleteDialog?.let { vpsId ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDelete() },
            title = { Text(stringResource(R.string.delete_server)) },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteVps(vpsId) }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDelete() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun VpsCard(
    vps: VpsEntity,
    isConnected: Boolean,
    isDisconnected: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConnectTerminal: () -> Unit,
    onConnectSftp: () -> Unit,
    onDisconnect: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusColor = when {
                        isConnected -> Color(0xFF4CAF50)
                        isDisconnected -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    Icon(
                        Icons.Default.Computer,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${vps.alias}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vps.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (vps.authType == "KEY") stringResource(R.string.auth_type_private_key) else stringResource(R.string.auth_type_password),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.content_desc_more))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (isConnected || isDisconnected) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_disconnect)) },
                                onClick = { showMenu = false; onDisconnect() },
                                leadingIcon = { Icon(Icons.Default.LinkOff, contentDescription = null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = { showMenu = false; onEdit() },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val buttonWeight = 0.55f
                if (isConnected) {
                    Button(
                        onClick = onConnectTerminal,
                        modifier = Modifier.weight(buttonWeight),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.label_ssh), style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                } else {
                    Button(
                        onClick = onConnectTerminal,
                        modifier = Modifier.weight(buttonWeight),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.label_ssh), style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedButton(
                    onClick = onConnectSftp,
                    modifier = Modifier.weight(buttonWeight),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.label_sftp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
