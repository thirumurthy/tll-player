package com.thirutricks.tllplayer.core.util

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Lightweight salted PIN hashing for profile locks. Not a password vault — a 4-digit PIN has only
 * 10k combinations — but it keeps the PIN out of plaintext storage. Format: `salt:sha256(salt+pin)`,
 * both hex-encoded.
 */
object Pin {
    fun hash(pin: String): String {
        val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }
        return "${salt.toHex()}:${digest(salt, pin)}"
    }

    fun verify(pin: String, stored: String?): Boolean {
        if (stored.isNullOrBlank()) return true // no PIN set
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = parts[0].fromHex() ?: return false
        return digest(salt, pin) == parts[1]
    }

    private fun digest(salt: ByteArray, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray()).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray? = runCatching {
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }.getOrNull()
}
