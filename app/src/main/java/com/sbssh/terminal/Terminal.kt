package com.sbssh.terminal

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import org.connectbot.terminal.TerminalEmulator

interface SelectionController {
    fun copySelection() {}
}

@Composable
fun Terminal(
    terminalEmulator: TerminalEmulator,
    modifier: Modifier = Modifier,
    typeface: Typeface? = null,
    initialFontSize: TextUnit = TextUnit.Unspecified,
    keyboardEnabled: Boolean = true,
    showSoftKeyboard: Boolean = false,
    focusRequester: FocusRequester? = null,
    forcedSize: Pair<Int, Int>? = null,
    modifierManager: Any? = null,
    onSelectionControllerAvailable: (SelectionController?) -> Unit = {},
    onTerminalTap: () -> Unit = {},
    onImeVisibilityChanged: (Boolean) -> Unit = {},
    onHyperlinkClick: (String) -> Unit = {}
) {
    val selectionController = remember { object : SelectionController {} }

    LaunchedEffect(Unit) {
        onSelectionControllerAvailable(selectionController)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTerminalTap() })
            }
    )
}
