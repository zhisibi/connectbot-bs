package com.sbssh.ui.vpslist

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.sbssh.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditVpsScreen(
    vpsId: Long? = null,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: AddEditVpsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaved()
    }

    val keyPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText()
                if (content != null) {
                    viewModel.updateKeyContent(content)
                }
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.toast_failed_read_key_file), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vpsId == null) stringResource(R.string.add_server) else stringResource(R.string.edit_server)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.alias,
                onValueChange = viewModel::updateAlias,
                label = { Text(stringResource(R.string.label_alias_server_nickname)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = uiState.host,
                onValueChange = viewModel::updateHost,
                label = { Text(stringResource(R.string.label_host_ip_or_domain)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = uiState.port,
                onValueChange = viewModel::updatePort,
                label = { Text(stringResource(R.string.label_port)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::updateUsername,
                label = { Text(stringResource(R.string.label_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Auth type selector
            Text(stringResource(R.string.label_auth_type), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.authType == "PASSWORD",
                    onClick = { viewModel.updateAuthType("PASSWORD") },
                    label = { Text(stringResource(R.string.label_password)) }
                )
                FilterChip(
                    selected = uiState.authType == "KEY",
                    onClick = { viewModel.updateAuthType("KEY") },
                    label = { Text(stringResource(R.string.label_ssh_key)) }
                )
            }

            if (uiState.authType == "PASSWORD") {
                var passwordVisible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::updatePassword,
                    label = { Text(stringResource(R.string.label_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(R.string.toggle_visibility)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = uiState.keyContent,
                    onValueChange = viewModel::updateKeyContent,
                    label = { Text(stringResource(R.string.label_private_key_content)) },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { keyPickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_import_key_file))
                    }
                }

                var passphraseVisible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = uiState.keyPassphrase,
                    onValueChange = viewModel::updateKeyPassphrase,
                    label = { Text(stringResource(R.string.label_key_passphrase_optional)) },
                    singleLine = true,
                    visualTransformation = if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passphraseVisible = !passphraseVisible }) {
                            Icon(
                                if (passphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(R.string.toggle_visibility)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.save() },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    Text(stringResource(R.string.status_saving))
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (vpsId == null) stringResource(R.string.add_server) else stringResource(R.string.save_changes))
                }
            }
        }
    }
}
