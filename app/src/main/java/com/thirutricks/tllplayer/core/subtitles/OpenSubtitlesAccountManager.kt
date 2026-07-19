package com.thirutricks.tllplayer.core.subtitles

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-profile OpenSubtitles account orchestration (subtitle plan §5.4/§5.5).
 *
 * Owns the sign-in/sign-out lifecycle over [OpenSubtitlesClient] + [OpenSubtitlesAuthStore] and
 * enforces the session rules:
 *  - authenticated calls use the stored token against the login-returned host;
 *  - on a 401, at most ONE silent re-login with stored credentials ("Stay signed in" only), then
 *    the session is erased and the caller sees signed-out — never a login loop;
 *  - sign-out permanently erases stored credentials (server-side logout is best-effort).
 *
 * [sessions] is an in-memory mirror of the store so Settings UI can observe state reactively.
 */
class OpenSubtitlesAccountManager(
    private val client: OpenSubtitlesClient,
    private val store: OpenSubtitlesAuthStore,
) {
    private val _sessions = MutableStateFlow<Map<Long, OpenSubtitlesAuthStore.Session>>(emptyMap())

    /** Loaded sessions by profile id (only profiles touched this run; load() fills on demand). */
    val sessions: StateFlow<Map<Long, OpenSubtitlesAuthStore.Session>> = _sessions.asStateFlow()

    /** The stored session for [profileId], or null when signed out. */
    fun session(profileId: Long): OpenSubtitlesAuthStore.Session? {
        _sessions.value[profileId]?.let { return it }
        return store.load(profileId)?.also { publish(profileId, it) }
    }

    /**
     * Signs [profileId] in. [staySignedIn] = keep the password for silent re-login (review R5);
     * off = token only. Throws [OpenSubtitlesClient.ApiException] (401 = bad credentials) or
     * IOException upward for the UI's §14 dialogs.
     */
    suspend fun signIn(profileId: Long, username: String, password: String, staySignedIn: Boolean): OpenSubtitlesAuthStore.Session {
        val result = client.login(username.trim(), password)
        val session = OpenSubtitlesAuthStore.Session(
            username = result.user.username ?: username.trim(),
            password = if (staySignedIn) password else null,
            token = result.token,
            apiHost = result.apiHost,
            level = result.user.level,
            vip = result.user.vip,
            remainingDownloads = result.user.remainingDownloads,
            allowedDownloads = result.user.allowedDownloads,
            resetTime = result.user.resetTime,
        )
        store.save(profileId, session)
        publish(profileId, session)
        return session
    }

    /** Signs [profileId] out: best-effort server logout, then permanent local erasure (§5.5). */
    suspend fun signOut(profileId: Long) {
        session(profileId)?.let { client.logout(it.token, it.apiHost) }
        eraseFor(profileId)
    }

    /** Permanent local erasure without a server call — used on profile deletion (§5.5). */
    fun eraseFor(profileId: Long) {
        store.erase(profileId)
        _sessions.value = _sessions.value - profileId
    }

    /**
     * Refreshes the allowance/account display from /infos/user (§5.3). On token expiry performs
     * the one-shot silent re-login when possible. Returns the updated session, or null when the
     * profile ended up signed out (caller shows the §14 "session expired" path).
     */
    suspend fun refreshUserInfo(profileId: Long): OpenSubtitlesAuthStore.Session? {
        val current = session(profileId) ?: return null
        val info = try {
            client.userInfo(current.token, current.apiHost, current.username)
        } catch (e: OpenSubtitlesClient.ApiException) {
            if (e.code != 401) throw e
            return silentReLogin(profileId, current)
        }
        val updated = current.copy(
            level = info.level ?: current.level,
            vip = info.vip,
            remainingDownloads = info.remainingDownloads,
            allowedDownloads = info.allowedDownloads,
            resetTime = info.resetTime,
        )
        store.save(profileId, updated)
        publish(profileId, updated)
        return updated
    }

    /** ONE re-login attempt with stored credentials; any failure erases the session (no loops). */
    private suspend fun silentReLogin(profileId: Long, expired: OpenSubtitlesAuthStore.Session): OpenSubtitlesAuthStore.Session? {
        val password = expired.password
        if (password == null) {
            Log.i(TAG, "token expired, no stored password (Stay signed in off) — manual sign-in needed")
            eraseFor(profileId)
            return null
        }
        return runCatching { signIn(profileId, expired.username, password, staySignedIn = true) }
            .getOrElse {
                Log.w(TAG, "silent re-login failed — manual sign-in needed")
                eraseFor(profileId)
                null
            }
    }

    private fun publish(profileId: Long, session: OpenSubtitlesAuthStore.Session) {
        _sessions.value = _sessions.value + (profileId to session)
    }

    private companion object {
        const val TAG = "OpenSubtitles"
    }
}
