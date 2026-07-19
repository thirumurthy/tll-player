package com.thirutricks.tllplayer.core.stalker

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * What a Stalker call needs to authenticate (plan §5.2). Phase B maps a `SourceEntity` to this
 * (portal URL from `url`, MAC from the new `mac` column, per-source User-Agent override) — keeping
 * this type free of DB dependencies lets Phase A compile and be tested standalone.
 */
data class StalkerCredentials(
    val sourceId: Long,
    val portalUrl: String,
    /** Canonical `AA:BB:CC:DD:EE:FF` (see [StalkerClient.canonicalizeMac]). */
    val mac: String,
    val userAgent: String? = null,
)

/** One live portal session. Tokens live in memory only — never persisted (plan key decision #3). */
data class StalkerSession(
    val apiBase: String,
    val token: String,
    val expiresAtMs: Long,
    /** Scalar fields of `get_profile` (subscription/watchdog details land here). */
    val profile: Map<String, String>,
) {
    val isExpired: Boolean get() = SystemClock.elapsedRealtime() >= expiresAtMs
}

/**
 * Owns the in-memory Stalker sessions, one per source (plan §5.2): [sessionFor] handshakes (probing
 * the API-endpoint candidates on first contact) and caches the token with a conservative TTL;
 * [withAuthRetry] re-handshakes ONCE when a call reports the token died mid-session
 * ([StalkerClient.StalkerAuthException] — portals also signal this as an empty `{"js":false}` payload).
 */
open class StalkerAuthManager(private val client: StalkerClient) {

    private val sessions = ConcurrentHashMap<Long, StalkerSession>()
    private val locks = ConcurrentHashMap<Long, Mutex>()

    /** Cached session if still fresh, else a full handshake → get_profile round trip. */
    suspend fun sessionFor(creds: StalkerCredentials): StalkerSession {
        sessions[creds.sourceId]?.takeIf { !it.isExpired }?.let { return it }
        return locks.getOrPut(creds.sourceId) { Mutex() }.withLock {
            // Re-check under the lock — a concurrent caller may have just handshaken.
            sessions[creds.sourceId]?.takeIf { !it.isExpired }?.let { return it }
            openSession(creds).also { sessions[creds.sourceId] = it }
        }
    }

    /** Drop the cached session so the next call re-handshakes (used on auth failure & source edit/delete). */
    fun invalidate(sourceId: Long) {
        sessions.remove(sourceId)
    }

    /**
     * Run [block] with a valid session; on an auth failure invalidate and retry ONCE with a fresh
     * handshake (§5.2 `withAuthRetry`). Anything failing twice is a real error for the caller.
     */
    open suspend fun <T> withAuthRetry(creds: StalkerCredentials, block: suspend (StalkerSession) -> T): T =
        try {
            block(sessionFor(creds))
        } catch (e: StalkerClient.StalkerAuthException) {
            Log.i(TAG, "auth expired sourceId=${creds.sourceId} (${e.message}) — re-handshaking once")
            invalidate(creds.sourceId)
            block(sessionFor(creds))
        }

    /**
     * "Test connection" for the add-source form (and the Phase A spike): always performs a FRESH
     * handshake + get_profile (ignoring any cache) and caches the result on success.
     */
    suspend fun testConnection(creds: StalkerCredentials): StalkerSession {
        invalidate(creds.sourceId)
        return sessionFor(creds)
    }

    private suspend fun openSession(creds: StalkerCredentials): StalkerSession {
        val startedAt = SystemClock.elapsedRealtime()
        val handshake = client.resolveHandshake(creds.portalUrl, creds.mac, creds.userAgent)
        val profile = client.getProfile(handshake.apiBase, creds.mac, handshake.token, creds.userAgent)
        Log.i(
            TAG,
            "session open sourceId=${creds.sourceId} apiBase=${handshake.apiBase} " +
                "profileFields=${profile.size} status=${profile["status"] ?: "?"} " +
                "watchdog=${profile["watchdog_timeout"] ?: "?"} ms=${SystemClock.elapsedRealtime() - startedAt}",
        )
        return StalkerSession(
            apiBase = handshake.apiBase,
            token = handshake.token,
            expiresAtMs = SystemClock.elapsedRealtime() + SESSION_TTL_MS,
            profile = profile,
        )
    }

    companion object {
        private const val TAG = "StalkerAuth"

        /** Conservative — real token lifetimes vary per portal (minutes–hours); re-handshaking is cheap (§5.2). */
        private const val SESSION_TTL_MS = 5 * 60_000L
    }
}
