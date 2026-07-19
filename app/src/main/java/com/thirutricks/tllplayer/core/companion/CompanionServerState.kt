package com.thirutricks.tllplayer.core.companion

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Lifecycle of the Remote companion listener — a tiny embedded HTTP server on the TV that serves a
 * mobile-friendly add-source form any phone/laptop on the same Wi-Fi can open.
 *
 * The QR encodes only the [Listening.urls] address; the phone is asked for [Listening.pin] on a gate
 * page before the form is served. Submissions arrive as [CompanionPayload]s (the TV user still presses
 * Start Import — the phone only fills the form).
 */
sealed interface CompanionServerState {
    data object Idle : CompanionServerState
    data object Starting : CompanionServerState
    data class Listening(
        val port: Int,
        val urls: List<String>,
        val pin: String,
        val qr: ImageBitmap?,
    ) : CompanionServerState
    data class Failed(val message: String) : CompanionServerState
}
