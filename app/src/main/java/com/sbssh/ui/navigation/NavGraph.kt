package com.sbssh.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sbssh.ui.auth.MasterPasswordScreen
import com.sbssh.ui.sftp.SftpScreen
import com.sbssh.ui.settings.LogScreen
import com.sbssh.ui.settings.SettingsScreen
import com.sbssh.ui.terminal.TerminalScreen
import com.sbssh.ui.vpslist.AddEditVpsScreen
import com.sbssh.ui.vpslist.VpsListScreen
import com.sbssh.ui.screens.console.ConsoleScreen

object Routes {
    const val AUTH = "auth"
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
    NavHost(
        navController = navController,
        startDestination = Routes.AUTH
    ) {
        composable(Routes.AUTH) {
            MasterPasswordScreen(
                onAuthenticated = {
                    navController.navigate(Routes.VPS_LIST) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                }
            )
        }

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
                onNavigateBack = { navController.popBackStack() },
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
                onViewLog = { navController.navigate(Routes.LOG) }
            )
        }

        composable(Routes.LOG) {
            LogScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
