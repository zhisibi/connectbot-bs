package com.sbssh.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashHandler {

    private var initialized = false
    private lateinit var crashDir: File

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        crashDir = File(context.filesDir, "crash")
        if (!crashDir.exists()) crashDir.mkdirs()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                val crashFile = File(crashDir, "crash_$timestamp.txt")

                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))

                val header = buildString {
                    append("=== SbSSH Crash Log ===\n")
                    append("Time: ").append(timestamp).append("\n")
                    append("Thread: ").append(thread.name).append(" (id=").append(thread.id).append(")\n")
                    append("App: com.sbssh\n")
                    append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
                    append("Android: ").append(Build.VERSION.SDK_INT).append(" ( ").append(Build.VERSION.RELEASE).append(" )\n")
                    append("-----\n")
                }

                crashFile.writeText(header + sw.toString())
                AppLogger.log("CRASH", "Uncaught exception saved to ${crashFile.absolutePath}", throwable)
            } catch (_: Exception) {
                // ignore
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun getLatestCrashLog(): String {
        if (!::crashDir.isInitialized || !crashDir.exists()) return "No crash logs"
        val latest = crashDir.listFiles()?.maxByOrNull { it.lastModified() } ?: return "No crash logs"
        return try { latest.readText() } catch (e: Exception) { "Error reading crash log: ${e.message}" }
    }
}
