package com.thirutricks.tllplayer.models

enum class ConfigType {
    DEFAULT_JSON,
    M3U_PLAYLIST,
    MAC_PORTAL,
    STALKER_PORTAL,
    UNKNOWN
}

data class NetworkConfig(
    val rawString: String,
    val type: ConfigType,
    val url: String,
    val macAddress: String? = null,
    val username: String? = null,
    val password: String? = null,
    val isEnabled: Boolean = true
)
