package com.sbssh.ui.auth

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sbssh.R
import com.sbssh.data.crypto.CryptoManager
import com.sbssh.util.BiometricHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterPasswordScreen(
    onAuthenticated: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val cryptoManager = remember { CryptoManager(context) }
    val viewModel: MasterPasswordViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = MasterPasswordViewModel.Factory(cryptoManager)
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var usePasswordLogin by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) {
            onAuthenticated()
        }
    }

    // Default to biometric when enabled; fall back to password when not available or first launch
    LaunchedEffect(uiState.isFirstLaunch, uiState.biometricAvailable) {
        if (uiState.isFirstLaunch || !uiState.biometricAvailable) {
            usePasswordLogin = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SbSSH") },
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (uiState.isFirstLaunch) stringResource(R.string.set_master_password) else stringResource(R.string.enter_master_password),
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (uiState.isFirstLaunch)
                    stringResource(R.string.create_password_desc)
                else
                    stringResource(R.string.unlock_password_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (uiState.isFirstLaunch || usePasswordLogin) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.master_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (uiState.isFirstLaunch) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(stringResource(R.string.confirm_password)) },
                        singleLine = true,
                        visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { confirmVisible = !confirmVisible }) {
                                Icon(
                                    imageVector = if (confirmVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle visibility"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.isFirstLaunch || usePasswordLogin) {
                Button(
                    onClick = {
                        if (uiState.isFirstLaunch) {
                            viewModel.setPassword(password, confirmPassword)
                        } else {
                            viewModel.unlock(password)
                        }
                    },
                    enabled = !uiState.isLoading && password.isNotEmpty() &&
                            (!uiState.isFirstLaunch || confirmPassword.isNotEmpty()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isLoading) {
                        Text(if (uiState.isFirstLaunch) stringResource(R.string.creating) else stringResource(R.string.unlocking))
                    } else {
                        Text(if (uiState.isFirstLaunch) stringResource(R.string.create) else stringResource(R.string.unlock))
                    }
                }
            }

            if (!uiState.isFirstLaunch && uiState.biometricAvailable && !usePasswordLogin) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        // Simple biometric prompt (no CryptoObject, like WebSSH)
                        val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
                        val callback = object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                super.onAuthenticationSucceeded(result)
                                try {
                                    val keyBytes = cryptoManager.getBiometricKey()
                                    viewModel.unlockWithBiometric(keyBytes)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Biometric error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                super.onAuthenticationError(errorCode, errString)
                                Toast.makeText(context, errString, Toast.LENGTH_SHORT).show()
                            }
                        }
                        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Biometric Authentication")
                            .setSubtitle("Use your fingerprint to unlock")
                            .setNegativeButtonText("Cancel")
                            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                            .build()
                        val biometricPrompt = androidx.biometric.BiometricPrompt(activity, executor, callback)
                        biometricPrompt.authenticate(promptInfo)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.unlock_biometrics))
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { usePasswordLogin = true }) {
                    Text("使用密码登录")
                }
            }

            if (!uiState.isFirstLaunch && uiState.biometricAvailable && usePasswordLogin) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { usePasswordLogin = false }) {
                    Text("使用指纹登录")
                }
            }
        }
    }
}
