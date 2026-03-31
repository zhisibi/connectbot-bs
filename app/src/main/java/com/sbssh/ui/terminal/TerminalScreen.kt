package com.sbssh.ui.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.room.Room
import com.sbssh.connectbot.data.ConnectBotDatabase
import com.sbssh.connectbot.data.entity.Host
import com.sbssh.connectbot.util.SecurePasswordStorage
import com.sbssh.data.crypto.FieldCryptoManager
import com.sbssh.data.crypto.SessionKeyHolder
import com.sbssh.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TerminalScreen(
    vpsId: Long,
    onBack: () -> Unit,
    onOpenConsole: (Long) -> Unit
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vpsId) {
        try {
            val sbDb = AppDatabase.getInstance(context)
            val vps = withContext(Dispatchers.IO) { sbDb.vpsDao().getVpsById(vpsId) }
                ?: throw IllegalStateException("VPS not found")

            // Build ConnectBot database instance
            val cbDb = Room.databaseBuilder(
                context.applicationContext,
                ConnectBotDatabase::class.java,
                "connectbot.db"
            )
                .addMigrations(ConnectBotDatabase.MIGRATION_4_5)
                .build()

            val hostId = withContext(Dispatchers.IO) {
                if (vps.connectbotHostId != null) {
                    vps.connectbotHostId
                } else {
                    val host = Host.createSshHost(
                        nickname = vps.alias,
                        hostname = vps.host,
                        port = vps.port,
                        username = vps.username
                    )
                    val newId = cbDb.hostDao().insert(host)
                    sbDb.vpsDao().updateConnectbotHostId(vpsId, newId)
                    newId
                }
            } ?: throw IllegalStateException("Failed to create host")

            // Save password to ConnectBot secure storage (if present)
            if (vps.authType == "PASSWORD" && vps.encryptedPassword != null) {
                val key = SessionKeyHolder.get()
                val crypto = FieldCryptoManager()
                val password = crypto.decrypt(vps.encryptedPassword, key)
                SecurePasswordStorage(context).savePassword(hostId, password)
            }

            onOpenConsole(hostId)
        } catch (e: Exception) {
            error = e.message ?: "Failed to open terminal"
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (error != null) {
            Text(error!!)
        } else {
            CircularProgressIndicator()
        }
    }
}
