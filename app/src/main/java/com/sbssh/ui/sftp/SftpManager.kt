package com.sbssh.ui.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpATTRS
import com.sbssh.data.db.VpsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.Vector

data class SftpFileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val permissions: String,
    val modifiedTime: Long,
    val owner: String
)

class SftpManager {

    private var session: Session? = null
    private var channel: ChannelSftp? = null
    private val jsch = JSch()
    var isConnected = false
        private set

    suspend fun connect(
        host: String,
        port: Int,
        username: String,
        authType: String,
        password: String?,
        keyContent: String?,
        keyPassphrase: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            jsch.removeAllIdentity()
            if (authType == "KEY" && keyContent != null) {
                jsch.addIdentity(
                    "sftp_key_${host}_${port}",
                    keyContent.toByteArray(),
                    null,
                    keyPassphrase?.toByteArray()
                )
            }

            session = jsch.getSession(username, host, port)
            if (authType == "PASSWORD" && password != null) {
                session?.setPassword(password)
            }
            val config = Properties()
            config["StrictHostKeyChecking"] = "no"
            session?.setConfig(config)
            session?.connect(15000)

            channel = session?.openChannel("sftp") as? ChannelSftp
            channel?.connect(5000)
            isConnected = true
            true
        } catch (e: Exception) {
            isConnected = false
            false
        }
    }

    suspend fun listDirectory(path: String): List<SftpFileInfo> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<SftpFileInfo>()
        try {
            @Suppress("UNCHECKED_CAST")
            val vector = channel?.ls(path) as? Vector<ChannelSftp.LsEntry>
            vector?.forEach { entry ->
                val attrs = entry.attrs
                if (entry.filename != "." && entry.filename != "..") {
                    entries.add(
                        SftpFileInfo(
                            name = entry.filename,
                            path = if (path.endsWith("/")) "$path${entry.filename}" else "$path/${entry.filename}",
                            size = attrs.size,
                            isDirectory = attrs.isDir,
                            permissions = attrs.permissionsString,
                            modifiedTime = attrs.mTime.toLong() * 1000,
                            owner = entry.longname.split("\\s+".toRegex()).getOrElse(2) { "" }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            throw e
        }
        entries.sortedWith(compareByDescending<SftpFileInfo> { it.isDirectory }.thenBy { it.name })
    }

    suspend fun downloadFile(remotePath: String, localFile: File): Unit = withContext(Dispatchers.IO) {
        channel?.get(remotePath, FileOutputStream(localFile))
    }

    suspend fun uploadFile(localFile: File, remotePath: String): Unit = withContext(Dispatchers.IO) {
        channel?.put(FileInputStream(localFile), remotePath, ChannelSftp.OVERWRITE)
    }

    suspend fun deleteFile(path: String): Unit = withContext(Dispatchers.IO) {
        val attrs = channel?.stat(path)
        if (attrs?.isDir == true) {
            channel?.rmdir(path)
        } else {
            channel?.rm(path)
        }
    }

    suspend fun rename(oldPath: String, newPath: String): Unit = withContext(Dispatchers.IO) {
        channel?.rename(oldPath, newPath)
    }

    suspend fun mkdir(path: String): Unit = withContext(Dispatchers.IO) {
        channel?.mkdir(path)
    }

    suspend fun chmod(path: String, permissions: Int): Unit = withContext(Dispatchers.IO) {
        channel?.chmod(permissions, path)
    }

    suspend fun getCurrentPath(): String = withContext(Dispatchers.IO) {
        channel?.pwd() ?: "/"
    }

    fun disconnect() {
        isConnected = false
        try { channel?.disconnect() } catch (_: Exception) { }
        try { session?.disconnect() } catch (_: Exception) { }
        channel = null
        session = null
    }
}
