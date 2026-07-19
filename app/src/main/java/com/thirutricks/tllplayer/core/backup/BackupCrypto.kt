package com.thirutricks.tllplayer.core.backup

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Field-level secret encryption for backups (NOT whole-file encryption — only individual secret values).
 *
 * Only individual secret values (source/proxy passwords) are encrypted; everything else in
 * owntv-backup.json stays human-readable. A user-supplied passphrase is stretched with PBKDF2 over a
 * per-backup random salt (so the file is portable across devices — no Android Keystore), then each
 * field is sealed with AES-256-GCM using a fresh random IV. The GCM tag authenticates both the value
 * and lets us detect a wrong passphrase (decrypt throws) without ever comparing the passphrase itself.
 *
 * Detection on import: a secret stored as a JSON string is legacy plaintext (v5); a secret stored as a
 * JSON object `{ "iv":..., "ct":... }` is encrypted. The root `crypto` block carries the KDF params.
 *
 * Logging rule: this class never logs the passphrase, derived key, plaintext, or ciphertext.
 */
object BackupCrypto {
    const val SCHEME = "field-aes-gcm"
    const val KDF = "PBKDF2WithHmacSHA256"
    const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    private val rng = SecureRandom()

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    /** A fresh random salt for a new backup. */
    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }

    /** Builds the root `crypto` metadata block for the given salt. */
    fun cryptoBlock(salt: ByteArray): JSONObject = JSONObject().apply {
        put("scheme", SCHEME)
        put("kdf", KDF)
        put("iterations", ITERATIONS)
        put("salt", b64(salt))
    }

    /** Derives the AES key from a passphrase + the backup's salt/iterations. */
    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(KDF)
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** Derives the key from a [crypto] root block + passphrase. Returns null if the block is malformed. */
    fun deriveKey(passphrase: String, crypto: JSONObject?): SecretKey? {
        crypto ?: return null
        val salt = runCatching { unb64(crypto.getString("salt")) }.getOrNull() ?: return null
        val iters = crypto.optInt("iterations", ITERATIONS)
        return deriveKey(passphrase.toCharArray(), salt, iters)
    }

    /** Seals a plaintext secret into a `{ "iv":..., "ct":... }` object. */
    fun encrypt(key: SecretKey, plaintext: String): JSONObject {
        val iv = ByteArray(IV_BYTES).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return JSONObject().put("iv", b64(iv)).put("ct", b64(ct))
    }

    /** True if [value] is an encrypted secret object (vs a legacy plaintext string). */
    fun isEncrypted(value: Any?): Boolean = value is JSONObject && value.has("iv") && value.has("ct")

    /**
     * Opens a sealed secret object. Throws on a wrong passphrase (GCM tag mismatch) or malformed input —
     * callers use this both to validate the passphrase and to decrypt.
     */
    fun decrypt(key: SecretKey, sealed: JSONObject): String {
        val iv = unb64(sealed.getString("iv"))
        val ct = unb64(sealed.getString("ct"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}
