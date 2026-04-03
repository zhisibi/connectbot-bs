package com.boshconnect

import android.app.Application
import com.boshconnect.util.AppLogger
import com.boshconnect.util.CrashHandler

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
