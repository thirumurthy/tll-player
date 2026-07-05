package com.thirutricks.tllplayer.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Network-based security checks shared by the modern Compose UI and the legacy fragment UI.
 *
 * `isVpnActive` was previously duplicated inside [com.thirutricks.tllplayer.legacy.LegacyMainActivity]
 * — but that activity is no longer the launcher (the Compose [com.thirutricks.tllplayer.MainActivity]
 * is), so the VPN guard was silently dropped from the production path. Centralizing it here lets the
 * modern entry point enforce the same block the legacy app always did.
 */
object NetworkSecurity {

    /** True if the device's active/default network is a VPN transport. */
    fun isVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } else {
            @Suppress("DEPRECATION")
            cm.allNetworks.any { network ->
                @Suppress("DEPRECATION")
                cm.getNetworkInfo(network)?.type == ConnectivityManager.TYPE_VPN
            }
        }
    }
}
