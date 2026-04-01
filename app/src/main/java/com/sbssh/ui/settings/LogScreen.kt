package com.sbssh.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbssh.util.AppLogger
import com.sbssh.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    var logText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            logText = AppLogger.getLog()
            kotlinx.coroutines.delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_debug_log)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(logText))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.action_copy_logs))
                    }
                    IconButton(onClick = {
                        logText = AppLogger.getLog()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh_logs))
                    }
                    IconButton(onClick = {
                        AppLogger.clear()
                        logText = context.getString(R.string.logs_cleared)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_clear_logs))
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
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                logText,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
