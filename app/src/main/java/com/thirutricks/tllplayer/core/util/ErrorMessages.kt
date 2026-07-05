package com.thirutricks.tllplayer.core.util

/**
 * Maps a raw exception / sync message to a friendly, user-facing one. [online] lets callers turn the
 * common "everything failed" case into a clear offline message instead of a stack-trace-ish string.
 */
fun friendlySyncError(raw: String?, online: Boolean): String = when {
    !online -> "You appear to be offline. Check your connection and try again."
    raw.isNullOrBlank() -> "Something went wrong. Please try again."
    raw.containsAny("timeout", "timed out") ->
        "The server took too long to respond. Please try again."
    raw.containsAny("Unable to resolve host", "UnknownHost", "No address associated") ->
        "Couldn't reach the server. Check the address and your connection."
    raw.containsAny("Failed to connect", "ECONNREFUSED", "Connection refused", "Connection reset") ->
        "Couldn't connect to the server. It may be down, or the address may be wrong."
    raw.containsAny("stream was reset", "PROTOCOL_ERROR", "StreamReset") ->
        "The server interrupted the download. Please try again."
    raw.containsAny("HTTP 401", "HTTP 403") ->
        "The server rejected your login. Check your username and password."
    raw.contains("HTTP 404") -> "Not found on the server. Check the URL."
    raw.containsAny("HTTP 500", "HTTP 502", "HTTP 503", "HTTP 504") ->
        "The server had a problem. Please try again later."
    raw.containsAny("CertPath", "SSLHandshake", "trust anchor", "CertificateException") ->
        "Secure connection failed — the server's certificate may be invalid."
    raw.containsAny("END_TAG", "START_TAG", "XmlPull", "PullParser", "ParserException") ->
        "The guide data from the server was malformed. Some of it may still have loaded — please try again, or check the EPG URL."
    else -> raw
}

private fun String.containsAny(vararg needles: String): Boolean =
    needles.any { contains(it, ignoreCase = true) }
