package com.thirutricks.tllplayer.infrastructure

import android.util.Log
import com.google.gson.GsonBuilder
import com.thirutricks.tllplayer.models.ConfigType
import com.thirutricks.tllplayer.models.NetworkConfig
import com.thirutricks.tllplayer.models.TV
import io.github.lizongying.Gua
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object PlaylistFetchers {
    private const val TAG = "PlaylistFetchers"

    private val unsafeClient: OkHttpClient by lazy {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())

            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val request = original.newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .method(original.method(), original.body())
                        .build()
                    chain.proceed(request)
                }
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    suspend fun fetch(config: NetworkConfig): List<TV> = withContext(Dispatchers.IO) {
        if (!config.isEnabled || config.url.isBlank()) return@withContext emptyList()

        return@withContext try {
            when (config.type) {
                ConfigType.DEFAULT_JSON -> fetchDefaultJson(config.url)
                ConfigType.M3U_PLAYLIST -> fetchM3uPlaylist(config.url)
                ConfigType.MAC_PORTAL -> fetchMacPortal(config.url, config.macAddress)
                ConfigType.DIRECT_STREAM -> fetchDirectStream(config.url)
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlist for url ${config.url}: ${e.message}")
            emptyList()
        }
    }

    private fun parseOttUrlAndHeaders(rawUrl: String): Pair<String, Map<String, String>> {
        val splitIndex = rawUrl.indexOf('|')
        if (splitIndex == -1) return Pair(rawUrl, emptyMap())
        
        val url = rawUrl.substring(0, splitIndex)
        val paramsPart = rawUrl.substring(splitIndex + 1)
        
        val headers = mutableMapOf<String, String>()
        val pairs = paramsPart.split("&")
        for (pair in pairs) {
            val kv = pair.split("=", limit = 2)
            if (kv.size == 2) {
                val key = kv[0].trim()
                val value = kv[1].trim()
                try {
                    val decodedValue = java.net.URLDecoder.decode(value, "UTF-8")
                    val lowerKey = key.lowercase()
                    when (lowerKey) {
                        "user-agent" -> headers["User-Agent"] = decodedValue
                        "referer" -> headers["Referer"] = decodedValue
                        "cookie" -> headers["Cookie"] = decodedValue
                        "drmscheme" -> headers["drm-type"] = decodedValue
                        "drmlicense" -> headers["drm-key"] = decodedValue
                        else -> headers[key] = decodedValue
                    }
                } catch (e: Exception) {
                    // Ignore decoding failures
                }
            }
        }
        return Pair(url, headers)
    }

    private suspend fun fetchDirectStream(rawUrl: String): List<TV> = withContext(Dispatchers.Default) {
        val (url, headers) = parseOttUrlAndHeaders(rawUrl)
        val tv = TV(
            id = 0,
            title = "Channel - ${(Math.random() * 10000).toInt()}",
            name = "",
            logo = "",
            group = "No Name",
            uris = listOf(rawUrl),
            child = emptyList(),
            headers = if (headers.isNotEmpty()) headers else null
        )
        listOf(tv)
    }

    suspend fun parseRawJson(str: String): List<TV> = withContext(Dispatchers.Default) {
        var string = str.trim()
        val g = Gua()
        if (g.verify(string)) {
            string = g.decode(string)
        }
        
        if (string.isBlank()) {
            return@withContext emptyList()
        }
        
        val startIndex = string.indexOf('[')
        if (startIndex != -1) {
             string = string.substring(startIndex)
             try {
                val type = object : com.google.gson.reflect.TypeToken<List<TV>>() {}.type
                return@withContext GsonBuilder().setLenient().create().fromJson<List<TV>>(string, type)
            } catch (e: Exception) {
                Log.e(TAG, "JSON parse error", e)
            }
        }
        return@withContext emptyList()
    }

    private suspend fun fetchDefaultJson(url: String): List<TV> {
        val request = Request.Builder().url(url).build()
        val response = unsafeClient.newCall(request).execute()
        return if (response.isSuccessful) {
            parseRawJson(response.body()?.string() ?: "")
        } else {
            emptyList()
        }
    }

    private suspend fun fetchM3uPlaylist(url: String): List<TV> {
        val request = Request.Builder().url(url).build()
        val response = unsafeClient.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()

        val body = response.body()?.string() ?: return emptyList()
        
        // HLS Direct Stream Detection
        // If the downloaded body contains specific HLS playback tags, it is a single video stream, not an IPTV channel list.
        if (body.contains("#EXT-X-TARGETDURATION") || 
            body.contains("#EXT-X-STREAM-INF") || 
            body.contains("#EXT-X-MEDIA-SEQUENCE")) {
            return fetchDirectStream(url)
        }
        
        val lines = body.lines()
        val channels = mutableListOf<TV>()

        var pendingTitle = ""
        var pendingLogo = ""
        var pendingGroup = "No Name"
        var pendingTvgId: String? = null
        var pendingCatchup: String? = null
        var pendingCatchupDays: String? = null
        var pendingCatchupSource: String? = null
        val pendingHeaders = mutableMapOf<String, String>()
        val pendingAttributes = mutableMapOf<String, String>()

        val attrRegex = Regex("""([a-zA-Z0-9_-]+)="([^"]*)"""")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            when {
                trimmed.startsWith("#EXTINF:", ignoreCase = true) -> {
                    // Reset properties as a new channel is starting
                    pendingTitle = ""
                    pendingLogo = ""
                    pendingGroup = "No Name"
                    pendingTvgId = null
                    pendingCatchup = null
                    pendingCatchupDays = null
                    pendingCatchupSource = null
                    pendingHeaders.clear()
                    pendingAttributes.clear()

                    // Parse attributes
                    val attrsPart = if (trimmed.contains(",")) trimmed.substring(0, trimmed.lastIndexOf(",")) else trimmed
                    val matches = attrRegex.findAll(attrsPart)
                    for (match in matches) {
                        val key = match.groupValues[1].lowercase()
                        val value = match.groupValues[2]
                        
                        when (key) {
                            "group-title" -> pendingGroup = value
                            "tvg-logo", "logo" -> pendingLogo = value
                            "tvg-id" -> pendingTvgId = value
                            "catchup" -> pendingCatchup = value
                            "catchup-days" -> pendingCatchupDays = value
                            "catchup-source", "c-s" -> pendingCatchupSource = value
                            else -> pendingAttributes[key] = value
                        }
                    }

                    // Extract title (everything after the last comma)
                    val commaIndex = trimmed.lastIndexOf(',')
                    if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                        pendingTitle = trimmed.substring(commaIndex + 1).trim()
                    } else {
                        pendingTitle = "Unknown Channel"
                    }
                }
                trimmed.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    val grp = trimmed.substringAfter(":", "").trim()
                    if (grp.isNotEmpty()) {
                        pendingGroup = grp
                    }
                }
                trimmed.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = trimmed.substringAfter(":", "").trim()
                    val pair = opt.split("=", limit = 2)
                    if (pair.size == 2) {
                        val key = pair[0].trim().lowercase()
                        val value = pair[1].trim()
                        if (key == "http-user-agent") {
                            pendingHeaders["User-Agent"] = value
                        } else if (key == "http-referrer" || key == "http-referer") {
                            pendingHeaders["Referer"] = value
                        }
                    }
                }
                trimmed.startsWith("#KODIPROP:", ignoreCase = true) -> {
                    val opt = trimmed.substringAfter(":", "").trim()
                    val pair = opt.split("=", limit = 2)
                    if (pair.size == 2) {
                        val key = pair[0].trim().lowercase()
                        val value = pair[1].trim()
                        
                        // Handle kodi properties like streams or headers
                        if (key == "inputstream.adaptive.stream_headers") {
                            // Format: User-Agent=x&Referer=y
                            val headers = value.split("&")
                            for (h in headers) {
                                val split = h.split("=", limit = 2)
                                if (split.size == 2) {
                                                                        // Follow order and overwrite
                                    pendingHeaders[split[0]] = split[1] 
                                }
                            }
                        } else if (key == "inputstream.adaptive.license_type") {
                            pendingHeaders["drm-type"] = value
                        } else if (key == "inputstream.adaptive.license_key") {
                            pendingHeaders["drm-key"] = value
                        } else if (key == "inputstreamaddon") {
                            pendingHeaders["drm-addon"] = value
                        }
                    }
                }
                trimmed.startsWith("#EXTHTTP:", ignoreCase = true) -> {
                    // Handle JSON format
                    val opt = trimmed.substringAfter(":", "").trim()
                    try {
                        if (opt.startsWith("{") && opt.endsWith("}")) {
                            val jsonHeaders = com.google.gson.JsonParser.parseString(opt).asJsonObject
                            for (entry in jsonHeaders.entrySet()) {
                                pendingHeaders[entry.key] = entry.value.asString
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore malformed exthttp
                    }
                }
                !trimmed.startsWith("#") -> {
                    // URL line
                    if (pendingTitle.isNotEmpty()) {
                        val parsedGroups = pendingGroup.split(";").map { it.trim() }.filter { it.isNotEmpty() }
                        val activeGroups = if (parsedGroups.isEmpty()) listOf("No Name") else parsedGroups
                        
                        for (grp in activeGroups) {
                            val tv = TV(
                                id = 0,
                                title = pendingTitle,
                                name = "",
                                logo = pendingLogo,
                                group = grp,
                                uris = listOf(trimmed),
                                child = emptyList(),
                                tvgId = pendingTvgId,
                                catchup = pendingCatchup,
                                catchupDays = pendingCatchupDays,
                                catchupSource = pendingCatchupSource,
                                headers = if (pendingHeaders.isNotEmpty()) pendingHeaders.toMap() else null,
                                attributes = if (pendingAttributes.isNotEmpty()) pendingAttributes.toMap() else null
                            )
                            channels.add(tv)
                        }
                        
                        // Reset line-specific properties for specific streams
                        pendingTitle = ""
                        pendingLogo = ""
                        pendingTvgId = null
                        pendingCatchup = null
                        pendingCatchupDays = null
                        pendingCatchupSource = null
                        pendingHeaders.clear()
                        pendingAttributes.clear()
                    }
                }
            }
        }
        
        // Fallback: If no channels were parsed (e.g. it was an M3U8 direct stream file with no EXTINF)
        if (channels.isEmpty()) {
            return fetchDirectStream(url)
        }
        
        return channels
    }

    private suspend fun fetchMacPortal(url: String, mac: String?): List<TV> {
        if (mac == null) return emptyList()

        // Placeholder for advanced Stalker middleware authentication logic.
        // Currently, it attaches MAC to cookie and attempts a direct payload fetch.
        // If Stalker token generation is required, further handshake requests would be executed here.
        val stalkerUrl = if (url.endsWith("/")) "${url}server/load.php?type=itv&action=get_all_channels" else "$url/server/load.php?type=itv&action=get_all_channels"
        
        val request = Request.Builder()
            .url(stalkerUrl)
            .header("Cookie", "mac=${mac.lowercase()}")
            .build()
            
        val response = unsafeClient.newCall(request).execute()
        
        if (!response.isSuccessful) return emptyList()
        
        val body = response.body()?.string() ?: return emptyList()
        
        // This parser would need to know the specific JSON schema of the stalker middleware output
        // For now, it will attempt a best-effort structural parsing if it aligns with `List<TV>`
        // If it doesn't align, it returns an empty list, allowing us to build upon it later.
        return parseRawJson(body)
    }
}
