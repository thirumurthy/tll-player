package com.thirutricks.tllplayer.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.Authenticator
import okhttp3.Credentials
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URLEncoder

/**
 * Immutable snapshot of the global app proxy (Approach 1 — one app-wide HTTP proxy). SOCKS and
 * per-source overrides are deliberately out of scope for this version; see extras/PROXY_SUPPORT_PLAN.md.
 */
data class ProxyConfig(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
) {
    /** Enabled AND has a sane host/port — only then do we actually route traffic through it. */
    val usable: Boolean get() = enabled && host.isNotBlank() && port in 1..65535
    val hasAuth: Boolean get() = username.isNotBlank()
}

/**
 * Single source of truth for the live proxy config. Holds a volatile snapshot kept up to date from a
 * DataStore flow, and exposes an OkHttp [ProxySelector] + [Authenticator] that read that snapshot on
 * every request. This lets the proxy be toggled at runtime WITHOUT rebuilding the singleton
 * [okhttp3.OkHttpClient] — so Coil and the ExoPlayer engines keep using the same client instance.
 *
 * mpv (libmpv / FFmpeg) does its own networking, so it reads [mpvProxyUrl] before each load instead.
 */
class ProxyConfigHolder(configFlow: Flow<ProxyConfig>) {

    @Volatile
    private var current: ProxyConfig = ProxyConfig()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        configFlow.onEach { current = it }.launchIn(scope)
    }

    fun snapshot(): ProxyConfig = current

    private fun activeProxy(): Proxy? {
        val c = current
        // createUnresolved: let the connection layer resolve the proxy host; the destination host is
        // resolved by the proxy itself (correct behavior for an HTTP forward proxy).
        return if (c.usable) Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(c.host, c.port)) else null
    }

    /** Routes every OkHttp request through the active proxy, or DIRECT when the proxy is off/invalid. */
    val proxySelector: ProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> = listOf(activeProxy() ?: Proxy.NO_PROXY)
        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) { /* no-op: surfaced as a normal request failure */ }
    }

    /**
     * Supplies `Proxy-Authorization` (Basic) when the proxy needs credentials. Returns null when the
     * proxy is off, has no username, or the credentials were already tried (so a wrong password fails
     * cleanly instead of looping). Credentials are never logged.
     */
    val proxyAuthenticator: Authenticator = Authenticator { _, response ->
        val c = current
        if (!c.usable || !c.hasAuth) return@Authenticator null
        if (response.request.header("Proxy-Authorization") != null) return@Authenticator null
        response.request.newBuilder()
            .header("Proxy-Authorization", Credentials.basic(c.username, c.password))
            .build()
    }

    /**
     * mpv `http-proxy` value — `http://[user:pass@]host:port`, or null when the proxy is off/invalid.
     * Credentials are URL-encoded and embedded in the URL (the only way mpv/FFmpeg takes proxy auth).
     * NEVER log the returned string — use [HttpClient.redactUrl] if it must appear anywhere.
     */
    fun mpvProxyUrl(): String? {
        val c = current
        if (!c.usable) return null
        val auth = if (c.hasAuth) {
            val u = URLEncoder.encode(c.username, "UTF-8")
            val p = URLEncoder.encode(c.password, "UTF-8")
            "$u:$p@"
        } else {
            ""
        }
        return "http://$auth${c.host}:${c.port}"
    }
}
