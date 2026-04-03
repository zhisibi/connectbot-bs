package com.boshconnect.connectbot.util

import android.content.Context
import timber.log.Timber

/**
 * Minimal provider loader stub for sbssh integration.
 * ConnectBot uses this to load optional providers (e.g. native libs).
 * In this build we simply invoke the callbacks immediately.
 */
object ProviderLoader {
    fun load(context: Context, listener: ProviderLoaderListener) {
        Timber.d("ProviderLoader: no-op load")
        listener.onProviderLoaderSuccess()
    }
}
