package com.boshconnect.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boshconnect.R
import com.boshconnect.BuildConfig
import com.boshconnect.ui.LocalTerminalManager
import com.boshconnect.util.AppLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onViewLog: () -> Unit = {}, onLogout: () -> Unit = {}) {
    val context = LocalContext.current
    val activity = context as? androidx.appcompat.app.AppCompatActivity
    val terminalManager = LocalTerminalManager.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(context, activity, terminalManager))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Backup: save directly to Downloads (no SAF picker)
    // Use GetContent for better device compatibility: some DocumentsUI flows return RESULT_OK
    // but a null intent/data on first pick.
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        AppLogger.log("RESTORE_UI", "onResult(GetContent): uri=$uri")
        if (uri == null) {
            Toast.makeText(context, "No file selected", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        viewModel.restoreServers(uri)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearMessages() }
    }
    LaunchedEffect(uiState.success) {
        uiState.success?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearMessages() }
    }
    LaunchedEffect(uiState.logoutMessage) {
        uiState.logoutMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearMessages() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = stringResource(R.string.action_logout))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsCard(Icons.Default.Language, stringResource(R.string.language),
                if (uiState.language == "zh") stringResource(R.string.language_zh) else stringResource(R.string.language_en),
                onClick = { viewModel.showLanguageDialog() })

            SettingsCard(Icons.Default.FormatSize, stringResource(R.string.font_size),
                when (uiState.fontSize) { "small" -> stringResource(R.string.font_small); "large" -> stringResource(R.string.font_large); else -> stringResource(R.string.font_medium) },
                onClick = { viewModel.showFontSizeDialog() })

            // Backup Password (MUST be before server backup)
            SettingsCard(Icons.Default.VpnKey, stringResource(R.string.backup_password),
                if (uiState.backupPasswordSet) stringResource(R.string.backup_password_set) else stringResource(R.string.backup_password_not_set),
                onClick = { viewModel.showBackupPasswordDialog() })

            // Local Backup (Unified Page: Backup + Restore)
            SettingsCard(Icons.Default.Backup, stringResource(R.string.local_backup),
                stringResource(R.string.local_backup_desc),
                onClick = { viewModel.showLocalBackupDialog() })

            // Cloud Sync (SbSSH Server)
            SettingsCard(Icons.Default.CloudSync, stringResource(R.string.cloud_sync),
                if (uiState.cloudSyncEnabled) stringResource(R.string.cloud_sync_enabled) else stringResource(R.string.cloud_sync_not_enabled),
                onClick = { viewModel.showCloudSyncDialog() })

            // GitHub Backup (Unified Card)
            SettingsCard(Icons.Default.Code, stringResource(R.string.github_backup),
                if (uiState.githubBackupEnabled) stringResource(R.string.github_backup_configured, uiState.githubRepo) else stringResource(R.string.github_backup_not_configured),
                onClick = { viewModel.showGithubDialog() })

            SettingsCard(Icons.Default.BugReport, stringResource(R.string.title_debug_log), stringResource(R.string.subtitle_view_app_logs),
                onClick = { onViewLog() })

            SettingsCard(Icons.Default.Info, stringResource(R.string.about), stringResource(R.string.about_version, stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
                onClick = { viewModel.showAbout() })
        }
    }

    // Biometric dialog
    if (uiState.showBiometricPasswordDialog) {
        var pwd by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { viewModel.dismissBiometricPasswordDialog() },
            title = { Text(if (uiState.biometricEnabled) stringResource(R.string.disable_biometric) else stringResource(R.string.enable_biometric)) },
            text = {
                Column {
                    if (!uiState.biometricEnabled) { Text(stringResource(R.string.enter_password_enable)); Spacer(Modifier.height(12.dp)) }
                    OutlinedTextField(value = pwd, onValueChange = { pwd = it }, label = { Text(stringResource(R.string.master_password)) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.toggleBiometric(pwd) }, enabled = pwd.isNotEmpty()) {
                Text(if (uiState.biometricEnabled) stringResource(R.string.disable) else stringResource(R.string.enable)) } },
            dismissButton = { TextButton(onClick = { viewModel.dismissBiometricPasswordDialog() }) { Text(stringResource(R.string.cancel)) } })
    }

    // Language dialog
    if (uiState.showLanguageDialog) {
        AlertDialog(onDismissRequest = { viewModel.dismissLanguageDialog() },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    ListItem(headlineContent = { Text(stringResource(R.string.language_zh)) },
                        leadingContent = { if (uiState.language == "zh") Icon(Icons.Default.Check, null) },
                        modifier = Modifier.clickable { viewModel.setLanguage("zh") })
                    ListItem(headlineContent = { Text(stringResource(R.string.language_en)) },
                        leadingContent = { if (uiState.language == "en") Icon(Icons.Default.Check, null) },
                        modifier = Modifier.clickable { viewModel.setLanguage("en") })
                }
            }, confirmButton = {},
            dismissButton = { TextButton(onClick = { viewModel.dismissLanguageDialog() }) { Text(stringResource(R.string.cancel)) } })
    }

    // Font size dialog
    if (uiState.showFontSizeDialog) {
        AlertDialog(onDismissRequest = { viewModel.dismissFontSizeDialog() },
            title = { Text(stringResource(R.string.font_size)) },
            text = {
                Column {
                    for ((key, label) in listOf("small" to R.string.font_small, "medium" to R.string.font_medium, "large" to R.string.font_large)) {
                        ListItem(headlineContent = { Text(stringResource(label)) },
                            leadingContent = { if (uiState.fontSize == key) Icon(Icons.Default.Check, null) },
                            modifier = Modifier.clickable { viewModel.setFontSize(key) })
                    }
                }
            }, confirmButton = {},
            dismissButton = { TextButton(onClick = { viewModel.dismissFontSizeDialog() }) { Text(stringResource(R.string.cancel)) } })
    }

    // Cloud sync dialog
    if (uiState.showCloudSyncDialog) {
        if (uiState.cloudSyncLoggedIn) {
            // Logged in — show sync actions + auto-sync toggle
            AlertDialog(onDismissRequest = { viewModel.dismissCloudSyncDialog() },
                title = { Text(stringResource(R.string.cloud_sync)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.username) + ": " + uiState.cloudSyncUsername)
                        Text(stringResource(R.string.server_url) + ": " + uiState.cloudSyncUrl)
                        val lastSync = uiState.cloudSyncLastSync
                        if (lastSync != null) {
                            Text(stringResource(R.string.cloud_sync_last_sync, lastSync),
                                style = MaterialTheme.typography.labelSmall)
                        }
                        // Auto-sync toggle
                        Row(
                            Modifier.fillMaxWidth(),
                            Arrangement.SpaceBetween,
                            Alignment.CenterVertically
                        ) {
                            Text(if (uiState.cloudAutoSync) stringResource(R.string.cloud_sync_auto_sync) else stringResource(R.string.cloud_sync_manual_sync))
                            Switch(
                                checked = uiState.cloudAutoSync,
                                onCheckedChange = { viewModel.toggleAutoSync(it) }
                            )
                        }
                        if (uiState.cloudSyncLoading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.cloudSmartSync() }, enabled = !uiState.cloudSyncLoading) {
                            Text(stringResource(R.string.action_sync))
                        }
                        TextButton(onClick = { viewModel.cloudDownload() }, enabled = !uiState.cloudSyncLoading) {
                            Text(stringResource(R.string.action_download))
                        }
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { viewModel.cloudLogout() }) { Text(stringResource(R.string.action_logout)) }
                        TextButton(onClick = { viewModel.dismissCloudSyncDialog() }) { Text(stringResource(R.string.cancel)) }
                    }
                })
        } else {
            // Not logged in — show login/register
            var url by remember { mutableStateOf(uiState.cloudSyncUrl.ifEmpty { "" }) }
            var username by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var showPassword by remember { mutableStateOf(false) }
            var isRegister by remember { mutableStateOf(false) }
            AlertDialog(onDismissRequest = { viewModel.dismissCloudSyncDialog() },
                title = { Text(if (isRegister) stringResource(R.string.action_register) else stringResource(R.string.cloud_sync)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = url, onValueChange = { url = it },
                            label = { Text(stringResource(R.string.server_url)) },
                            placeholder = { Text("http://192.168.1.100:9800") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = username, onValueChange = { username = it },
                            label = { Text(stringResource(R.string.username)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = password, onValueChange = { password = it },
                            label = { Text(stringResource(R.string.master_password)) },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                            modifier = Modifier.fillMaxWidth())
                        if (isRegister) {
                            Text(stringResource(R.string.prompt_register_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (uiState.cloudSyncLoading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        TextButton(onClick = { isRegister = !isRegister }, modifier = Modifier.align(Alignment.End)) {
                            Text(if (isRegister) stringResource(R.string.prompt_login_have_account) else stringResource(R.string.prompt_register_no_account))
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (isRegister) viewModel.cloudRegister(url, username, password)
                            else viewModel.cloudLogin(url, username, password)
                        },
                        enabled = url.isNotBlank() && username.isNotBlank() && password.isNotBlank() && !uiState.cloudSyncLoading
                    ) { Text(if (isRegister) stringResource(R.string.action_register) else stringResource(R.string.action_login)) }
                },
                dismissButton = { TextButton(onClick = { viewModel.dismissCloudSyncDialog() }) { Text(stringResource(R.string.cancel)) } })
        }
    }

    // Restore password dialog (when backup salt differs from local)
    if (uiState.showRestorePasswordDialog) {
        var restorePwd by remember { mutableStateOf("") }
        var showRestorePwd by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { /* don't dismiss - must enter password */ },
            title = { Text(stringResource(R.string.enter_password_restore)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.restore_password_hint))
                    OutlinedTextField(value = restorePwd, onValueChange = { restorePwd = it },
                        label = { Text(stringResource(R.string.password)) }, singleLine = true,
                        visualTransformation = if (showRestorePwd) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { showRestorePwd = !showRestorePwd }) { Icon(if (showRestorePwd) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.restoreWithPassword(restorePwd) }, enabled = restorePwd.isNotEmpty()) {
                Text("恢复") } },
            dismissButton = { })
    }

    // GitHub config dialog (Integrated Page)
    if (uiState.showGithubDialog) {
        var ghRepo by remember { mutableStateOf(uiState.githubRepo) }
        var ghToken by remember { mutableStateOf(uiState.githubToken) }
        var showGhToken by remember { mutableStateOf(false) }
        
        AlertDialog(onDismissRequest = { viewModel.dismissGithubDialog() },
            title = { Text(stringResource(R.string.github_backup_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.github_backup_desc), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = ghRepo, onValueChange = { ghRepo = it },
                        label = { Text(stringResource(R.string.github_repo_label)) }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.github_repo_placeholder)) },
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = ghToken, onValueChange = { ghToken = it },
                        label = { Text(stringResource(R.string.github_pat_label)) }, singleLine = true,
                        visualTransformation = if (showGhToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { showGhToken = !showGhToken }) { Icon(if (showGhToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                        modifier = Modifier.fillMaxWidth())
                    
                    if (uiState.githubBackupEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.githubBackup() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.backup_to_github), style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { viewModel.githubRestore() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.restore_from_github), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { 
                TextButton(onClick = { viewModel.saveGithubConfig(ghRepo, ghToken) }, enabled = ghRepo.isNotEmpty() && ghToken.isNotEmpty()) {
                    Text("保存") 
                } 
            },
            dismissButton = { 
                TextButton(onClick = { viewModel.dismissGithubDialog() }) { 
                    Text(stringResource(R.string.cancel)) 
                } 
            })
    }

    // Backup password dialog
    if (uiState.showBackupPasswordDialog) {
        var bp1 by remember { mutableStateOf("") }
        var bp2 by remember { mutableStateOf("") }
        var showBp by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { viewModel.dismissBackupPasswordDialog() },
            title = { Text(stringResource(R.string.set_backup_password)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_password_warning), 
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.backup_password_desc), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = bp1, onValueChange = { bp1 = it },
                        label = { Text(stringResource(R.string.enter_backup_password)) }, singleLine = true,
                        visualTransformation = if (showBp) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { showBp = !showBp }) { Icon(if (showBp) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bp2, onValueChange = { bp2 = it },
                        label = { Text(stringResource(R.string.confirm_backup_password)) }, singleLine = true,
                        visualTransformation = if (showBp) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth())
                    if (uiState.backupPasswordSet) {
                        TextButton(onClick = { viewModel.clearBackupPassword() }) {
                            Text(stringResource(R.string.clear_backup_password), color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.setBackupPassword(bp1, bp2) }, enabled = bp1.isNotEmpty() && bp2.isNotEmpty()) {
                Text("设置") } },
            dismissButton = { TextButton(onClick = { viewModel.dismissBackupPasswordDialog() }) { Text(stringResource(R.string.cancel)) } })
    }

    // Local Backup dialog (Unified Page)
    if (uiState.showLocalBackupDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLocalBackupDialog() },
            title = { Text(stringResource(R.string.local_backup)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.local_backup_desc), style = MaterialTheme.typography.bodySmall)
                    
                    // Backup Button
                    Button(
                        onClick = {
                            viewModel.dismissLocalBackupDialog()
                            viewModel.saveBackupToDownloads()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.backupPasswordSet
                    ) {
                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.server_backup))
                    }
                    
                    // Restore Button
                    OutlinedButton(
                        onClick = {
                            viewModel.dismissLocalBackupDialog()
                            restoreLauncher.launch("*/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.server_restore))
                    }
                }
            },
            confirmButton = { },
            dismissButton = { TextButton(onClick = { viewModel.dismissLocalBackupDialog() }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // About dialog
    if (uiState.showAbout) {
        AlertDialog(onDismissRequest = { viewModel.dismissAbout() },
            title = { Text(stringResource(R.string.about_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.about_version, stringResource(R.string.app_name), BuildConfig.VERSION_NAME), fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.about_desc)); Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.about_features), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.about_ssh)); Text(stringResource(R.string.about_sftp))
                    Text(stringResource(R.string.about_encryption)); Text(stringResource(R.string.about_biometric))
                    Text(stringResource(R.string.about_backup)); Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.about_copyright), style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.dismissAbout() }) { Text(stringResource(R.string.ok)) } })
    }
}

@Composable
private fun SettingsCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String,
                          onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (trailing != null) trailing() else Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
