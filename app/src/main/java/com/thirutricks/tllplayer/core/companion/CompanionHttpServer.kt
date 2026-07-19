package com.thirutricks.tllplayer.core.companion

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.thirutricks.tllplayer.core.model.SourceType

/**
 * Tiny embedded HTTP listener behind the Remote add-source flow. It serves a mobile-friendly,
 * OwnTV-themed form (Xtream / M3U / Stalker) that any device on the same Wi-Fi can open.
 *
 * ## Security model
 * The QR encodes only the server URL — never the PIN. Opening it shows a PIN-entry gate; the visitor
 * types the 6-digit PIN shown on the TV. Only then is the form served (with the PIN baked into its
 * submit endpoints). Direct POSTs to `/xtream|/m3u|/stalker` must also carry the PIN (`?pin=` or the
 * `X-Companion-Pin` header) or receive 401. So a stray device on the network cannot push a source.
 *
 * The phone only fills the form; it never starts the import. Submitted details are surfaced via
 * [start]'s onPayload callback, which the host uses to pre-fill Add Source on the TV.
 *
 * Everything HTTP happens off the main thread on [Dispatchers.IO]. Passwords/MAC are never logged.
 */
class CompanionHttpServer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var pin: String = ""

    /** Bundled Lora TTF served at `/lora.ttf` so the web form matches the app's popup font offline. */
    @Volatile private var fontBytes: ByteArray? = null

    /**
     * Bind [port] and start accepting. [pin] gates the form and the POST endpoints. [fontBytes], when
     * provided, is served at `/lora.ttf`. Returns the LAN URLs to show on the TV. Throws on bind
     * failure (the caller maps that to [CompanionServerState.Failed]).
     */
    fun start(port: Int, pin: String, fontBytes: ByteArray?, onPayload: (CompanionPayload) -> Unit): List<String> {
        stop()
        this.pin = pin
        this.fontBytes = fontBytes
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(port))
        serverSocket = socket
        running.set(true)
        scope.launch { acceptLoop(socket, onPayload) }
        return CompanionLink.lanUrls(port)
    }

    fun stop() {
        running.set(false)
        pin = ""
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    /** Permanent teardown — cancels the I/O scope. Call when the owning singleton is disposed. */
    fun close() {
        stop()
        scope.cancel()
    }

    private suspend fun acceptLoop(socket: ServerSocket, onPayload: (CompanionPayload) -> Unit) {
        try {
            while (running.get() && !socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    if (running.get()) Log.w(TAG, "Companion accept failed", e)
                    break
                }
                scope.launch { handleClient(client, onPayload) }
            }
        } finally {
            stop()
        }
    }

    private fun handleClient(client: Socket, onPayload: (CompanionPayload) -> Unit) {
        client.use { socket ->
            socket.soTimeout = 10_000
            val input = BufferedInputStream(socket.getInputStream())
            val requestLine = readLine(input) ?: return sendText(socket, 400, "Bad request")
            val parts = requestLine.split(' ')
            if (parts.size < 3) return sendText(socket, 400, "Bad request")

            val method = parts[0].uppercase()
            val rawPath = parts[1]
            val path = rawPath.substringBefore('?')
            val query = rawPath.substringAfter('?', "")
            val queryPin = parseQuery(query)["pin"].orEmpty()

            val headers = LinkedHashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }

            // Font asset for the web form (no PIN needed — it is only a font).
            if (method == "GET" && path == "/lora.ttf") {
                val bytes = fontBytes
                return if (bytes != null) sendBytes(socket, 200, "font/ttf", bytes) else sendText(socket, 404, "Not found")
            }

            // Landing: a correct PIN in the query (e.g. the "send another" link) skips straight to the
            // form; otherwise show the PIN gate.
            if (method == "GET" && (path == "/" || path == "/index.html")) {
                val page = if (pinOk(queryPin)) CompanionHtml.formPage(pin)
                else CompanionHtml.pinPage(if (queryPin.isNotBlank()) "That PIN did not match — try again." else null)
                return sendHtml(socket, 200, page)
            }

            // PIN submission from the gate.
            if (method == "POST" && path == "/") {
                val submitted = parseQuery(readBody(input, headers))["pin"].orEmpty()
                val page = if (pinOk(submitted)) CompanionHtml.formPage(pin)
                else CompanionHtml.pinPage("That PIN did not match — try again.")
                return sendHtml(socket, 200, page)
            }

            // Source submissions — PIN required (query or header), else 401.
            if (method == "POST" && (path == "/xtream" || path == "/m3u" || path == "/stalker")) {
                val headerPin = headers["x-companion-pin"].orEmpty()
                if (!pinOk(queryPin.ifBlank { headerPin })) return sendText(socket, 401, "Unauthorized")
                val fallback = when (path) {
                    "/stalker" -> SourceType.STALKER
                    "/m3u" -> SourceType.M3U
                    else -> SourceType.XTREAM
                }
                val payload = parsePayload(headers["content-type"], readBody(input, headers), fallback)
                    ?: return sendText(socket, 400, "Missing required fields")
                onPayload(payload)
                return sendHtml(socket, 200, CompanionHtml.savedPage(payload, pin))
            }

            sendText(socket, 404, "Not found")
        }
    }

    private fun pinOk(candidate: String): Boolean = pin.isNotBlank() && pinEquals(candidate, pin)

    private fun readBody(input: BufferedInputStream, headers: Map<String, String>): String {
        val length = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        if (length == 0) return ""
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(bytes, offset, length - offset)
            if (read <= 0) break
            offset += read
        }
        return String(bytes, 0, offset, StandardCharsets.UTF_8)
    }

    /**
     * Parse a submitted body into a [CompanionPayload], or null if required fields are missing.
     * Public so tests exercise it without a socket. Accepts form-encoded and JSON bodies; field names
     * tolerate camelCase / snake_case / common synonyms.
     */
    fun parsePayload(contentType: String?, bodyText: String, fallbackType: SourceType): CompanionPayload? {
        val isJson = contentType?.contains("application/json", ignoreCase = true) == true ||
            bodyText.trimStart().startsWith("{")
        val fields = if (isJson) {
            // A malformed JSON body (or a JSON-less test env) must not crash the accept thread.
            runCatching { parseJsonFields(bodyText) }.getOrDefault(emptyMap())
        } else {
            parseQuery(bodyText)
        }

        val rawType = pick(fields, "type", "sourceType", "source_type").ifBlank { fallbackType.name }.trim().lowercase()
        val type = when (rawType) {
            "m3u", "m3u8" -> SourceType.M3U
            "stalker" -> SourceType.STALKER
            else -> SourceType.XTREAM
        }

        val server = pick(fields, "server", "url").trim()
        val user = pick(fields, "user", "username").trim()
        val pass = pick(fields, "pass", "password").trim()
        val portalUrl = pick(fields, "portalUrl", "portal_url").trim()
        val mac = pick(fields, "mac", "macAddress", "mac_address").trim()

        when (type) {
            SourceType.STALKER -> if (portalUrl.isBlank() || mac.isBlank()) return null
            SourceType.M3U -> if (server.isBlank()) return null
            SourceType.XTREAM, SourceType.LOCAL_BACKUP -> if (server.isBlank() || user.isBlank() || pass.isBlank()) return null
        }

        return CompanionPayload(
            type = type,
            name = pick(fields, "name").trim(),
            server = server,
            user = user,
            pass = pass,
            portalUrl = portalUrl,
            mac = mac,
            userAgent = pick(fields, "userAgent", "user_agent").trim(),
            epgUrl = pick(fields, "epgUrl", "epg_url").trim(),
            autoRefresh = pick(fields, "autoRefresh", "auto_refresh").ifBlank { "OFF" },
            syncLive = bool(fields, "syncLive", "sync_live", default = true),
            syncMovies = bool(fields, "syncMovies", "sync_movies", default = true),
            syncSeries = bool(fields, "syncSeries", "sync_series", default = true),
            isDefault = bool(fields, "isDefault", "is_default", default = false),
        )
    }

    private fun parseJsonFields(bodyText: String): Map<String, String> {
        val json = org.json.JSONObject(bodyText)
        val out = LinkedHashMap<String, String>()
        json.keys().forEach { key ->
            when (val value = json.opt(key)) {
                null, org.json.JSONObject.NULL -> Unit
                else -> out[key] = value.toString()
            }
        }
        return out
    }

    private fun pick(fields: Map<String, String>, vararg keys: String): String =
        keys.firstNotNullOfOrNull { key -> fields[key]?.takeIf { it.isNotBlank() } } ?: ""

    private fun bool(fields: Map<String, String>, primary: String, secondary: String, default: Boolean): Boolean {
        val raw = fields[primary] ?: fields[secondary] ?: return default
        return raw.equals("true", true) || raw == "1" || raw.equals("on", true) || raw.equals("yes", true)
    }

    private fun sendHtml(socket: Socket, code: Int, body: String) =
        sendBytes(socket, code, "text/html; charset=utf-8", body.toByteArray(StandardCharsets.UTF_8))

    private fun sendText(socket: Socket, code: Int, body: String) =
        sendBytes(socket, code, "text/plain; charset=utf-8", body.toByteArray(StandardCharsets.UTF_8))

    private fun sendBytes(socket: Socket, code: Int, contentType: String, bytes: ByteArray) {
        val status = when (code) {
            200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 404 -> "Not Found"; else -> "OK"
        }
        runCatching {
            socket.getOutputStream().use { out ->
                val header = buildString {
                    append("HTTP/1.1 ").append(code).append(' ').append(status).append("\r\n")
                    append("Content-Type: ").append(contentType).append("\r\n")
                    append("Content-Length: ").append(bytes.size).append("\r\n")
                    append("Cache-Control: no-store\r\n")
                    append("Connection: close\r\n\r\n")
                }
                out.write(header.toByteArray(StandardCharsets.UTF_8))
                out.write(bytes)
                out.flush()
            }
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next == -1) {
                if (buffer.size() == 0) return null
                break
            }
            if (next == '\n'.code) break
            if (next != '\r'.code) buffer.write(next)
        }
        return buffer.toString(StandardCharsets.UTF_8.name())
    }

    companion object {
        private const val TAG = "CompanionServer"

        /** Length-checked, constant-time PIN compare so timing cannot leak the PIN digit by digit. */
        fun pinEquals(submitted: String, expected: String): Boolean {
            if (submitted.length != expected.length) return false
            var diff = 0
            for (i in expected.indices) diff = diff or (submitted[i].code xor expected[i].code)
            return diff == 0
        }

        /** Parse an `application/x-www-form-urlencoded` string (query or body) into a key -> value map. */
        fun parseQuery(query: String): Map<String, String> {
            if (query.isBlank()) return emptyMap()
            val out = LinkedHashMap<String, String>()
            query.split('&').forEach { pair ->
                if (pair.isBlank()) return@forEach
                val split = pair.split('=', limit = 2)
                val key = urlDecode(split[0])
                if (key.isNotBlank()) out[key] = urlDecode(split.getOrNull(1).orEmpty())
            }
            return out
        }

        private fun urlDecode(value: String): String =
            runCatching { URLDecoder.decode(value.replace('+', ' '), StandardCharsets.UTF_8.name()) }.getOrDefault(value)
    }
}
