package com.sbssh.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    private lateinit var logFile: File

    fun init(context: Context) {
        logFile = File(context.filesDir, "sbssh_debug.log")
        try {
            if (!logFile.exists()) {
                logFile.parentFile?.mkdirs()
                logFile.writeText("=== SbSSH Debug Log ===\n")
            }
        } catch (_: Exception) { }
    }

    fun log(tag: String, message: String, throwable: Throwable? = null) {
        if (!::logFile.isInitialized) return
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val time = sdf.format(Date())
        var entry = "[$time] [$tag] $message\n"
        if (throwable != null) {
            entry += "  EXCEPTION: ${throwable.javaClass.simpleName}: ${throwable.message}\n"
            throwable.stackTrace.take(5).forEach { entry += "    at $it\n" }
        }
        try {
            logFile.appendText(entry)
        } catch (_: Exception) { }
    }

    fun getLog(): String {
        if (!::logFile.isInitialized) return "Logger not initialized"
        return try {
            logFile.readText().ifEmpty { "No logs yet" }
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }

    fun clear() {
        if (::logFile.isInitialized) {
            try { logFile.writeText("") } catch (_: Exception) { }
        }
    }
}
