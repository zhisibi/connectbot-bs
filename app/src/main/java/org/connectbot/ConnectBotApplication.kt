package com.boshconnect

import dagger.hilt.android.HiltAndroidApp
import com.boshconnect.logging.TimberInitializer
import javax.inject.Inject

@HiltAndroidApp
class ConnectBotApplication : SbsshApp() {

    @Inject
    lateinit var timberInitializer: TimberInitializer

    override fun onCreate() {
        super.onCreate()
        timberInitializer.initialize()
    }
}
