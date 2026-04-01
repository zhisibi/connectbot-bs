/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("ktlint:compose:compositionlocal-allowlist")

package com.sbssh.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.sbssh.service.TerminalManager
import com.sbssh.ui.navigation.NavGraph
import com.sbssh.ui.settings.SettingsManager
import com.sbssh.ui.theme.SbsshTheme

val LocalTerminalManager = compositionLocalOf<TerminalManager?> {
    null
}

@Composable
fun ConnectBotApp(
    appUiState: AppUiState,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val terminalManager = (appUiState as? AppUiState.Ready)?.terminalManager

    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val settings by settingsManager.settings.collectAsState()

    SbsshTheme(fontScale = settings.fontScale) {
        CompositionLocalProvider(LocalTerminalManager provides terminalManager) {
            NavGraph(navController = navController)
        }
    }
}
