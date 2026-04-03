package com.boshconnect.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun FloatingActionButtonMenu(
    expanded: Boolean,
    button: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box {
        button()
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { }
        ) {
            content()
        }
    }
}

@Composable
fun FloatingActionButtonMenuItem(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        leadingIcon = icon,
        modifier = modifier
    )
}

interface ToggleFloatingActionButtonScope {
    val checkedProgress: Float
}

private class ToggleFabScopeImpl(override val checkedProgress: Float) : ToggleFloatingActionButtonScope

@Composable
fun ToggleFloatingActionButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ToggleFloatingActionButtonScope.() -> Unit
) {
    val progress = if (checked) 1f else 0f
    FloatingActionButton(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier
    ) {
        ToggleFabScopeImpl(progress).content()
    }
}

object ToggleFloatingActionButtonDefaults {
    fun Modifier.animateIcon(progress: () -> Float): Modifier = this
}
