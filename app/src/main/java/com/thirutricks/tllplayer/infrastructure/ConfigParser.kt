package com.thirutricks.tllplayer.infrastructure

import com.thirutricks.tllplayer.models.ConfigType
import com.thirutricks.tllplayer.models.NetworkConfig
import java.util.regex.Pattern

object ConfigParser {
    
    private val macPattern = Pattern.compile("(?:^|[?&])mac=([0-9a-fA-F:-]+)", Pattern.CASE_INSENSITIVE)
    
    fun identifyAndParse(rawString: String): NetworkConfig {
        val lines = rawString.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            return NetworkConfig(rawString, ConfigType.UNKNOWN, "")
        }
        
        // Find a URL line; usually the first line that starts with http or https
        val urlLine = lines.firstOrNull { it.startsWith("http://", true) || it.startsWith("https://", true) } ?: lines.first()
        
        // Extract MAC address
        var macAddress: String? = null
        for (line in lines) {
            val matcher = macPattern.matcher(line)
            if (matcher.find()) {
                macAddress = matcher.group(1)
                break
            }
        }
        
        // Determine config type
        val urlLower = urlLine.lowercase()
        val type = when {
            macAddress != null -> ConfigType.MAC_PORTAL
            urlLower.substringBefore("?").endsWith(".mpd") || urlLower.substringBefore("?").endsWith(".ts") || 
            urlLower.substringBefore("?").endsWith(".mkv") || urlLower.substringBefore("?").endsWith(".mp4") || 
            urlLine.contains("|") -> ConfigType.DIRECT_STREAM
            urlLower.substringBefore("?").endsWith(".m3u") || urlLower.substringBefore("?").endsWith(".m3u8") || urlLower.contains("m3u") -> ConfigType.M3U_PLAYLIST
            else -> ConfigType.DEFAULT_JSON // Fallback to your custom JSON endpoint schema
        }
        
        return NetworkConfig(
            rawString = rawString,
            type = type,
            url = urlLine,
            macAddress = macAddress?.uppercase(),
            isEnabled = true
        )
    }
}
