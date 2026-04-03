package com.boshconnect.ui.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun TerminalScreen(
    vpsId: Long,
    onBack: () -> Unit,
    onOpenConsole: (Long) -> Unit
) {
    val viewModel: TerminalScreenViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    var hasNavigated by remember { mutableStateOf(false) }

    LaunchedEffect(vpsId) {
        viewModel.openTerminal(vpsId)
    }

    LaunchedEffect(uiState) {
        if (!hasNavigated && uiState is TerminalUiState.Ready) {
            hasNavigated = true
            onOpenConsole((uiState as TerminalUiState.Ready).hostId)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val state = uiState) {
            is TerminalUiState.Error -> Text(state.message)
            else -> CircularProgressIndicator()
        }
    }
}
