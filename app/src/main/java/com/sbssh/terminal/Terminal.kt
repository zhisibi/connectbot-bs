package com.sbssh.terminal

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.TextUnit
import org.connectbot.terminal.ModifierManager
import org.connectbot.terminal.SelectionController
import org.connectbot.terminal.ComposeController
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.Terminal as ConnectBotTerminal

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
    val actualTypeface = typeface ?: Typeface.MONOSPACE
    val actualFocusRequester = focusRequester ?: remember { FocusRequester() }

    ConnectBotTerminal(
        terminalEmulator = terminalEmulator,
        modifier = modifier,
        typeface = actualTypeface,
        initialFontSize = initialFontSize,
        keyboardEnabled = keyboardEnabled,
        showSoftKeyboard = showSoftKeyboard,
        focusRequester = actualFocusRequester,
        onTerminalTap = onTerminalTap,
        onImeVisibilityChanged = onImeVisibilityChanged,
        forcedSize = forcedSize,
        modifierManager = modifierManager as? ModifierManager,
        onSelectionControllerAvailable = onSelectionControllerAvailable,
        onHyperlinkClick = onHyperlinkClick,
        onComposeControllerAvailable = { _: ComposeController? -> }
    )
}
