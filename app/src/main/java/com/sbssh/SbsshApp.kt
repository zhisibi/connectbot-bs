package com.sbssh

import android.app.Application
import com.sbssh.util.AppLogger
import com.sbssh.util.CrashHandler

open class SbsshApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLogger.init(this)
        CrashHandler.init(this)
        AppLogger.log("APP", "SbSSH started")
    }

    companion object {
        lateinit var instance: SbsshApp
            private set
    }
}
