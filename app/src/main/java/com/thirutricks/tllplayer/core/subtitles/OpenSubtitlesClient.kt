package com.thirutricks.tllplayer.core.subtitles

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * OpenSubtitles REST client (subtitle plan Part B Phase 1). All calls go through the OwnTV
 * OpenSubtitles Worker (worker/opensub/), which injects the application consumer key — the app
 * never holds an Api-Key. The Worker forwards to api.opensubtitles.com or
 * vip-api.opensubtitles.com per request via the X-OS-Host header, so the login-returned
 * `base_url` is honoured through the proxy (plan §5.4).
 *
 * Logging rule: never log usernames, passwords, tokens, or full auth responses (plan §12).
 */
class OpenSubtitlesClient(private val okHttpClient: OkHttpClient) {

    /** Non-2xx from OpenSubtitles. 401 drives the one-shot silent re-login upstream. */
    class ApiException(val code: Int, message: String) : IOException(message)

    /** Login result: the bearer token plus the account snapshot OpenSubtitles returns with it. */
    data class LoginResult(
        val token: String,
        /** Upstream API host from the login `base_url` (e.g. vip-api.opensubtitles.com). */
        val apiHost: String,
        val user: UserInfo,
    )

    /** Account status per plan §5.3 — provider-reported values only, never assumed totals. */
    data class UserInfo(
        val username: String?,
        val level: String?,
        val vip: Boolean,
        /** Downloads remaining today, or null if the provider didn't report it. */
        val remainingDownloads: Int?,
        /** Provider-reported daily allowance, or null (remaining-only display then). */
        val allowedDownloads: Int?,
        /** Provider-reported reset time text (e.g. "23 hours and 57 minutes"), or null. */
        val resetTime: String?,
    )

    /** POST /login. Throws [ApiException] (401 = wrong credentials) or IOException (network). */
    suspend fun login(username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("username", username).put("password", password)
        val json = call("POST", "/login", host = DEFAULT_HOST, token = null, body = body)
        val token = json.optString("token").takeIf { it.isNotBlank() }
            ?: throw ApiException(0, "Login response had no token")
        val host = json.optString("base_url").takeIf { it.isNotBlank() } ?: DEFAULT_HOST
        LoginResult(token = token, apiHost = host, user = parseUser(json.optJSONObject("user"), username))
    }

    /** DELETE /logout — best-effort server-side session invalidation; local erase always happens anyway. */
    suspend fun logout(token: String, apiHost: String) = withContext(Dispatchers.IO) {
        runCatching { call("DELETE", "/logout", host = apiHost, token = token) }
            .onFailure { Log.w(TAG, "logout call failed (ignored): ${it.message}") }
        Unit
    }

    /** GET /infos/user — refreshes the allowance display. Throws [ApiException] 401 on expired token. */
    suspend fun userInfo(token: String, apiHost: String, username: String?): UserInfo =
        withContext(Dispatchers.IO) {
            val json = call("GET", "/infos/user", host = apiHost, token = token)
            parseUser(json.optJSONObject("data"), username)
        }

    /** GET /subtitles search (unauthenticated — Api-Key only, injected by the Worker). Phase 2 UI. */
    suspend fun search(query: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val qs = query.entries.joinToString("&") {
            "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
        }
        call("GET", "/subtitles?$qs", host = DEFAULT_HOST, token = null)
    }

    /**
     * POST /download — mints the single-use, time-limited download link for [fileId] and consumes one
     * quota unit (review R2). Callers MUST check the cache by file_id first (review R3). Requests
     * UTF-8 normalization (plan §7.2). Returns the raw response: `link`, `remaining`, `reset_time`.
     */
    suspend fun requestDownload(token: String, apiHost: String, fileId: Long): JSONObject =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("file_id", fileId).put("force_download", false)
                .put("sub_format", "srt")
            call("POST", "/download", host = apiHost, token = token, body = body)
        }

    private fun call(
        method: String,
        pathAndQuery: String,
        host: String,
        token: String?,
        body: JSONObject? = null,
    ): JSONObject {
        val builder = Request.Builder()
            .url("$WORKER_BASE/api/v1$pathAndQuery")
            .header("X-OS-Host", host)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
        if (token != null) builder.header("Authorization", "Bearer $token")
        val requestBody = body?.toString()?.toRequestBody(JSON_MEDIA_TYPE)
        builder.method(method, requestBody)

        okHttpClient.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                // The body may echo credentials on auth endpoints — log only code + path.
                Log.w(TAG, "$method $pathAndQuery -> HTTP ${resp.code}")
                throw ApiException(resp.code, "OpenSubtitles HTTP ${resp.code}")
            }
            return runCatching { JSONObject(text) }.getOrElse {
                throw IOException("OpenSubtitles returned malformed JSON for $pathAndQuery")
            }
        }
    }

    private fun parseUser(user: JSONObject?, fallbackUsername: String?): UserInfo = UserInfo(
        username = user?.optString("username")?.takeIf { it.isNotBlank() } ?: fallbackUsername,
        level = user?.optString("level")?.takeIf { it.isNotBlank() },
        vip = user?.optBoolean("vip") ?: false,
        remainingDownloads = user?.optInt("remaining_downloads", -1)?.takeIf { it >= 0 },
        allowedDownloads = user?.optInt("allowed_downloads", -1)?.takeIf { it > 0 },
        resetTime = user?.optString("reset_time")?.takeIf { it.isNotBlank() },
    )

    companion object {
        private const val TAG = "OpenSubtitles"

        /** The maintainer's deployment of worker/opensub/ — holds the consumer key server-side. */
        private const val WORKER_BASE = "https://my-owntv-opensub.xiannero.workers.dev"

        /** Standard upstream; login's base_url may switch a VIP account to vip-api (plan §5.4). */
        const val DEFAULT_HOST = "api.opensubtitles.com"

        /** OpenSubtitles requires an identifying UA; must match what the Worker sends upstream. */
        private const val USER_AGENT = "OwnTV"

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
