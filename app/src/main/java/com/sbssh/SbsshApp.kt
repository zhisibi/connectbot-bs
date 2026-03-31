package com.sbssh

import android.app.Application
import com.sbssh.util.AppLogger

open class SbsshApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLogger.init(this)
        AppLogger.log("APP", "SbSSH started")
    }

    companion object {
        lateinit var instance: SbsshApp
            private set
    }
}
