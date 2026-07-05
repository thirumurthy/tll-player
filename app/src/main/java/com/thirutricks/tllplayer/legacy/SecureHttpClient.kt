package com.thirutricks.tllplayer.legacy
import com.thirutricks.tllplayer.MyTVApplication

import okhttp3.OkHttpClient

/**
 * Legacy entry point for a security-checked OkHttpClient. The [com.thirutricks.tllplayer.core.network.SecurityInterceptor]
 * now lives in the modern network package and is wired into the Koin-provided client used by the whole
 * app; this object keeps the legacy call sites compiling and routes to the same interceptor.
 */
object SecureHttpClient {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(com.thirutricks.tllplayer.core.network.SecurityInterceptor(MyTVApplication.getInstance()))
            .build()
    }
}