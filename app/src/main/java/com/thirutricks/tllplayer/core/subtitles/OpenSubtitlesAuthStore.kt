package com.thirutricks.tllplayer.core.subtitles

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Per-profile OpenSubtitles session storage (subtitle plan §5.4/§5.5, review R5).
 *
 * Each profile's blob (username, optional password, token, API host, account metadata) is sealed
 * with an Android Keystore AES-256-GCM key that never leaves secure hardware, and the ciphertext
 * sits in a private SharedPreferences file. The password is present only when the user opted in
 * to "Stay signed in" (R5) — otherwise only the token is stored and expiry means manual sign-in.
 *
 * No insecure fallback (plan §5.4): if the Keystore key is missing/reset or a blob fails to
 * decrypt (GCM tag mismatch), the stored session is erased and the profile is simply signed out.
 *
 * Logging rule: never log usernames, passwords, tokens, or blob contents (plan §12).
 */
class OpenSubtitlesAuthStore(context: Context) {

    /** One profile's stored session. [password] is null unless "Stay signed in" was chosen. */
    data class Session(
        val username: String,
        val password: String?,
        val token: String,
        val apiHost: String,
        val level: String?,
        val vip: Boolean,
        val remainingDownloads: Int?,
        val allowedDownloads: Int?,
        val resetTime: String?,
    ) {
        val staySignedIn: Boolean get() = password != null
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The stored session for [profileId], or null if signed out / unreadable (then it's erased). */
    fun load(profileId: Long): Session? {
        val sealed = prefs.getString(key(profileId), null) ?: return null
        return runCatching { decode(decrypt(sealed)) }.getOrElse {
            Log.w(TAG, "stored session unreadable for profile $profileId — erasing (signed out)")
            erase(profileId)
            null
        }
    }

    /** Seals and stores [session] for [profileId]. On any crypto failure nothing is written. */
    fun save(profileId: Long, session: Session) {
        runCatching {
            prefs.edit().putString(key(profileId), encrypt(encode(session))).apply()
        }.onFailure {
            Log.w(TAG, "could not store session for profile $profileId: ${it.javaClass.simpleName}")
        }
    }

    /** Permanent erasure for sign-out and profile deletion (plan §5.5). */
    fun erase(profileId: Long) {
        prefs.edit().remove(key(profileId)).apply()
    }

    private fun key(profileId: Long) = "profile_$profileId"

    // --- backup / restore (used by BackupManager; the blob is re-sealed with the backup passphrase,
    // so this returns/accepts PLAIN JSON — never write the plaintext anywhere but the encrypted backup) ---

    /** The stored session for [profileId] as plain JSON (username, password, token, …), or null. */
    fun exportJson(profileId: Long): JSONObject? = load(profileId)?.let { JSONObject(encode(it)) }

    /** Restores a session rebuilt from [json] (as returned by [exportJson]) under [profileId]. */
    fun importJson(profileId: Long, json: JSONObject) {
        runCatching { save(profileId, decode(json.toString())) }
    }

    // --- serialization ---

    private fun encode(s: Session): String = JSONObject().apply {
        put("u", s.username)
        s.password?.let { put("p", it) }
        put("t", s.token)
        put("h", s.apiHost)
        s.level?.let { put("lvl", it) }
        put("vip", s.vip)
        s.remainingDownloads?.let { put("rem", it) }
        s.allowedDownloads?.let { put("alw", it) }
        s.resetTime?.let { put("rst", it) }
    }.toString()

    private fun decode(json: String): Session {
        val o = JSONObject(json)
        return Session(
            username = o.getString("u"),
            password = o.optString("p").takeIf { o.has("p") },
            token = o.getString("t"),
            apiHost = o.optString("h").ifBlank { OpenSubtitlesClient.DEFAULT_HOST },
            level = o.optString("lvl").takeIf { o.has("lvl") },
            vip = o.optBoolean("vip"),
            remainingDownloads = if (o.has("rem")) o.getInt("rem") else null,
            allowedDownloads = if (o.has("alw")) o.getInt("alw") else null,
            resetTime = o.optString("rst").takeIf { o.has("rst") },
        )
    }

    // --- Keystore AES-GCM sealing ---

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey()) // Keystore supplies a fresh random IV
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(ct, Base64.NO_WRAP)
        return "$iv:$body"
    }

    private fun decrypt(sealed: String): String {
        val sep = sealed.indexOf(':')
        require(sep > 0) { "malformed sealed blob" }
        val iv = Base64.decode(sealed.substring(0, sep), Base64.NO_WRAP)
        val ct = Base64.decode(sealed.substring(sep + 1), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    companion object {
        private const val TAG = "OpenSubtitles"
        private const val PREFS_NAME = "opensub_auth"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "owntv_opensub_auth"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
