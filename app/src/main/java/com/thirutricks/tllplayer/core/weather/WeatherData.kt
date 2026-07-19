package com.thirutricks.tllplayer.core.weather

import androidx.compose.runtime.Immutable

/** Phase 7 — weather chip data. null means no data yet / offline / location unavailable. */
@Immutable
data class WeatherInfo(
    val temperatureC: Float,
    val city: String,
    val weatherCode: Int,  // WMO weather code
    val isDay: Boolean,    // from Open-Meteo — drives day/night icon choice
) {
    /** Maps WMO code + day/night → symbol key (matches owntv_weather_canvas_symbols_api_v2.html). */
    fun symbolKey(): String = when {
        weatherCode == 0 && isDay -> "sunny"
        weatherCode == 0 && !isDay -> "clearNight"
        weatherCode in 1..2 && isDay -> "partlyDay"
        weatherCode in 1..2 && !isDay -> "partlyNight"
        weatherCode == 3 -> "cloudy"
        weatherCode in 45..48 -> "fog"
        weatherCode in 51..57 -> "drizzle"
        weatherCode in 61..67 || weatherCode in 80..82 -> "rain"
        weatherCode in 71..77 || weatherCode in 85..86 -> "snow"
        weatherCode in 95..99 -> "thunder"
        else -> "cloudy" // fallback
    }
}
