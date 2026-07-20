package com.thirutricks.tllplayer.core.companion

import android.content.Context
import android.util.Log
import java.net.BindException
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import com.thirutricks.tllplayer.R

/**
 * Owns the Remote companion HTTP listener for the app's lifetime. Registered as a Koin `single` so
 * [com.thirutricks.tllplayer.features.setup.SetupViewModel] and [com.thirutricks.tllplayer.features.settings.SettingsViewModel]
 * share one server instead of each carrying duplicate networking code.
 *
 * The phone only fills the Add Source form; it never starts the import. Each submission is exposed two
 * ways:
 *  - [payloads] — a live event stream, used by the Remote screen to know when to hand off; and
 *  - [lastPayload] — the retained latest value, so the Manual form (which subscribes *after* the
 *    hand-off navigation) still sees it. A replay-0 SharedFlow alone would drop it in that window.
 *
 * [start]/[stop] are safe to call from the UI thread; the server does its I/O on its own dispatcher.
 */
class CompanionController(context: Context) {

    private val appContext = context.applicationContext
    private val server = CompanionHttpServer()

    private val _state = MutableStateFlow<CompanionServerState>(CompanionServerState.Idle)
    val state: StateFlow<CompanionServerState> = _state.asStateFlow()

    private val _payloads = MutableSharedFlow<CompanionPayload>(extraBufferCapacity = 8)
    val payloads: SharedFlow<CompanionPayload> = _payloads.asSharedFlow()

    private val _lastPayload = MutableStateFlow<CompanionPayload?>(null)
    val lastPayload: StateFlow<CompanionPayload?> = _lastPayload.asStateFlow()

    /** A fresh 6-digit PIN per [start], so a leaked code is short-lived. */
    @Volatile private var currentPin: String = ""

    /** Bundled Lora TTF bytes, loaded once and served on the web form; null if the resource can't be read. */
    private val loraBytes: ByteArray? by lazy {
        runCatching { appContext.resources.openRawResource(R.font.lora_variable).use { it.readBytes() } }
            .onFailure { Log.w(TAG, "Could not load Lora for companion web form; falling back to serif", it) }
            .getOrNull()
    }

    fun start(port: Int) {
        if (port !in 1..65535) {
            _state.value = CompanionServerState.Failed("Enter a valid port between 1 and 65535.")
            return
        }
        _state.value = CompanionServerState.Starting
        try {
            currentPin = generatePin()
            _lastPayload.value = null
            val urls = server.start(port, currentPin, loraBytes) { payload ->
                Log.d(TAG, "Received ${payload.type} payload '${payload.name.ifBlank { "(unnamed)" }}' — forwarding to UI")
                _lastPayload.value = payload
                _payloads.tryEmit(payload)
            }
            _state.value = CompanionServerState.Listening(
                port = port,
                urls = urls,
                pin = currentPin,
                // The QR encodes the plain URL only — the PIN is typed on the phone's gate page.
                qr = CompanionLink.renderQr(CompanionLink.lanUrl(port)),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to start companion listener", t)
            _state.value = CompanionServerState.Failed(friendlyError(t, port))
        }
    }

    fun stop() {
        server.stop()
        currentPin = ""
        _state.value = CompanionServerState.Idle
    }

    /** Clears [lastPayload] after the Manual Add Source form consumed it for pre-fill. */
    fun consumePayload() {
        _lastPayload.value = null
    }

    /** Permanent teardown for the app-wide singleton. */
    fun dispose() {
        server.close()
        _state.value = CompanionServerState.Idle
    }

    private fun generatePin(): String = SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')

    private fun friendlyError(t: Throwable, port: Int): String = when (t) {
        is BindException -> "Port $port is already in use. Try again to pick a fresh one."
        else -> t.message?.takeIf { it.isNotBlank() } ?: "Could not open the local server."
    }

    private companion object {
        const val TAG = "CompanionController"
    }
}
