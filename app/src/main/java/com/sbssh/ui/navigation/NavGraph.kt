package com.sbssh.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sbssh.data.crypto.CryptoManager
import com.sbssh.data.crypto.SessionKeyHolder
import com.sbssh.util.AppLogger
import com.sbssh.ui.sftp.SftpScreen
import com.sbssh.ui.settings.LogScreen
import com.sbssh.ui.settings.SettingsScreen
import com.sbssh.ui.terminal.TerminalScreen
import com.sbssh.ui.vpslist.AddEditVpsScreen
import com.sbssh.ui.vpslist.VpsListScreen
import com.sbssh.ui.screens.console.ConsoleScreen

object Routes {
    const val VPS_LIST = "vps_list"
    const val ADD_VPS = "add_vps"
    const val EDIT_VPS = "edit_vps/{vpsId}"
    const val TERMINAL = "terminal/{vpsId}"
    const val CONSOLE = "console/{hostId}"
    const val SFTP = "sftp/{vpsId}"
    const val SETTINGS = "settings"
    const val LOG = "log"

    fun editVps(vpsId: Long) = "edit_vps/$vpsId"
    fun terminal(vpsId: Long) = "terminal/$vpsId"
    fun console(hostId: Long) = "console/$hostId"
    fun sftp(vpsId: Long) = "sftp/$vpsId"
}

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController()
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Auto-initialize device key (no login required)
    LaunchedEffect(Unit) {
        if (!SessionKeyHolder.isSet()) {
            val cryptoManager = CryptoManager(context)
            // Check if we have a stored device key
            val storedKey = try { cryptoManager.getBiometricKey() } catch (_: Exception) { null }
            if (storedKey != null) {
                SessionKeyHolder.set(storedKey)
                AppLogger.log("AUTH", "Loaded stored device key")
            } else {
                // Generate and store a new device key
                val deviceKey = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
                cryptoManager.enableBiometric(deviceKey) // reuse this to store the key
                SessionKeyHolder.set(deviceKey)
                // Also generate salt if first launch
                if (cryptoManager.isFirstLaunch()) {
                    cryptoManager.saveSalt(cryptoManager.generateSalt())
                }
                AppLogger.log("AUTH", "Generated new device key")
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.VPS_LIST
    ) {
        composable(Routes.VPS_LIST) {
            VpsListScreen(
                onAddVps = { navController.navigate(Routes.ADD_VPS) },
                onEditVps = { id -> navController.navigate(Routes.editVps(id)) },
                onConnectTerminal = { id -> navController.navigate(Routes.terminal(id)) },
                onConnectSftp = { id -> navController.navigate(Routes.sftp(id)) },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.ADD_VPS) {
            AddEditVpsScreen(
                vpsId = null,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.EDIT_VPS,
            arguments = listOf(navArgument("vpsId") { type = NavType.LongType })
        ) { backStackEntry ->
            val vpsId = backStackEntry.arguments?.getLong("vpsId") ?: return@composable
            AddEditVpsScreen(
                vpsId = vpsId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.TERMINAL,
            arguments = listOf(navArgument("vpsId") { type = NavType.LongType })
        ) { backStackEntry ->
            val vpsId = backStackEntry.arguments?.getLong("vpsId") ?: return@composable
            TerminalScreen(
                vpsId = vpsId,
                onBack = { navController.popBackStack() },
                onOpenConsole = { hostId ->
                    navController.navigate(Routes.console(hostId))
                }
            )
        }

        composable(
            route = Routes.CONSOLE,
            arguments = listOf(navArgument("hostId") { type = NavType.LongType })
        ) {
            ConsoleScreen(
                onNavigateBack = {
                    navController.popBackStack(Routes.VPS_LIST, false)
                },
                onNavigateToPortForwards = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.SFTP,
            arguments = listOf(navArgument("vpsId") { type = NavType.LongType })
        ) { backStackEntry ->
            val vpsId = backStackEntry.arguments?.getLong("vpsId") ?: return@composable
            SftpScreen(
                vpsId = vpsId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onViewLog = { navController.navigate(Routes.LOG) },
                onLogout = {
                    navController.popBackStack(Routes.VPS_LIST, false)
                }
            )
        }

        composable(Routes.LOG) {
            LogScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
