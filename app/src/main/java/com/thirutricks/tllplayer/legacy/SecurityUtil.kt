@file:Suppress("unused")
package com.thirutricks.tllplayer.legacy

import android.content.Context

/**
 * Legacy shim. The real implementation now lives at
 * [com.thirutricks.tllplayer.core.network.SecurityUtil] / [com.thirutricks.tllplayer.core.network.SecurityInterceptor]
 * and is shared by both UIs. These typealias re-exports keep any lingering legacy references compiling
 * without duplicating the security logic.
 */
typealias SecurityUtil = com.thirutricks.tllplayer.core.network.SecurityUtil
typealias SecurityInterceptor = com.thirutricks.tllplayer.core.network.SecurityInterceptor

/** Legacy call-through kept for source compatibility. */
fun isDeviceRestricted(context: Context): Boolean =
    com.thirutricks.tllplayer.core.network.SecurityUtil.isDeviceRestricted(context)
