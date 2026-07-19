package com.thirutricks.tllplayer.core.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.thirutricks.tllplayer.core.network.ConnectivityObserver
import java.util.concurrent.TimeUnit

/**
 * Phase 7 — fetches current weather via Open-Meteo (free, no API key) after resolving the device's
 * approximate location from a free IP geolocation service. Results are cached in-memory for 30 minutes
 * so we don't hammer the free APIs on every recomposition.
 */
class WeatherRepository(
    private val http: OkHttpClient,
    private val connectivity: ConnectivityObserver,
) {
    private var cached: WeatherInfo? = null
    private var cacheTime: Long = 0L
    // The manual location the cache was built with. If the user changes the override, the cache must
    // be rebuilt even inside the 30-min window, otherwise the chip would keep showing the old city.
    private var cachedLocation: String = ""

    companion object {
        private const val CACHE_MS = 30 * 60 * 1000L // 30 min
    }

    /**
     * Returns cached weather if fresh, otherwise fetches. null = offline / unavailable.
     *
     * @param manualLocation blank = auto-detect from public IP; otherwise a city name or "lat,lon" pair.
     *   Used to fix the chip showing the VPN server's city instead of the user's.
     */
    suspend fun get(manualLocation: String = ""): WeatherInfo? {
        val now = System.currentTimeMillis()
        if (cached != null && (now - cacheTime) < CACHE_MS && cachedLocation == manualLocation) return cached
        if (!connectivity.isOnlineNow()) return cached // return stale if offline
        return withContext(Dispatchers.IO) {
            runCatching { fetchFresh(manualLocation) }
                .onSuccess { cached = it; cacheTime = now; cachedLocation = manualLocation }
                .getOrNull() ?: cached
        }
    }

    private fun fetchFresh(manualLocation: String): WeatherInfo {
        val (lat, lon, city) = resolveLocation(manualLocation)

        // Fetch current weather from Open-Meteo
        val weatherUrl = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day" +
            "&timezone=auto"
        val weatherReq = Request.Builder().url(weatherUrl).build()
        val weatherJson = http.newCall(weatherReq).execute().use { it.body?.string() ?: return error("no weather") }
        val w = JSONObject(weatherJson).getJSONObject("current")
        val temp = w.getDouble("temperature_2m").toFloat()
        val code = w.getInt("weather_code")
        val isDay = w.optInt("is_day", 1) == 1

        return WeatherInfo(temperatureC = temp, city = city, weatherCode = code, isDay = isDay)
    }

    /**
     * Manual override wins; falls back to IP geolocation. Returns (lat, lon, cityName).
     * - blank → free IP geolocation API (the legacy path)
     * - "lat,lon" → parsed directly
     * - anything else → geocoded via Open-Meteo's free geocoding API (one extra HTTP call)
     */
    private fun resolveLocation(manual: String): Triple<Double, Double, String> {
        val m = manual.trim()
        if (m.isEmpty()) {
            val locReq = Request.Builder()
                .url("https://ipapi.co/json/")
                .header("User-Agent", "OwnTV/1.0")
                .build()
            val locJson = http.newCall(locReq).execute().use { it.body?.string() ?: return error("no loc") }
            val loc = JSONObject(locJson)
            return Triple(
                loc.getDouble("latitude"),
                loc.getDouble("longitude"),
                loc.optString("city", loc.optString("region", "")),
            )
        }
        // "lat,lon" — parse directly.
        val parts = m.split(",").mapNotNull { it.trim().toDoubleOrNull() }
        if (parts.size == 2) return Triple(parts[0], parts[1], m)
        // Else geocode a city name via Open-Meteo's free geocoding API.
        val name = java.net.URLEncoder.encode(m, "UTF-8")
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$name&count=1&language=en&format=json"
        val req = Request.Builder().url(url).build()
        val json = http.newCall(req).execute().use { it.body?.string() ?: return error("no geo") }
        val hit = JSONObject(json).optJSONArray("results")?.optJSONObject(0) ?: return error("geo no match")
        return Triple(hit.getDouble("latitude"), hit.getDouble("longitude"), hit.optString("name", m))
    }

    private fun error(msg: String): Nothing = throw IllegalStateException("Weather fetch failed: $msg")
}
