package com.thirutricks.tllplayer.core.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.thirutricks.tllplayer.BuildConfig
import java.io.File

/**
 * In-app updates straight from GitHub Releases: checks the repo's latest release, compares its tag
 * with the installed version, downloads the release APK, and hands it to the system installer.
 * No server of our own — the releases CI already publishes `TLLPlayer-vX.Y.Z.apk` per tag.
 */
class UpdateManager(
    private val context: Context,
    private val client: OkHttpClient,
) {
    data class UpdateInfo(val version: String, val notes: String, val apkUrl: String)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: UpdateInfo) : State
        data class Downloading(val percent: Int) : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

    /** Queries GitHub's latest release; moves to Available / UpToDate / Failed. */
    fun check() {
        if (_state.value is State.Checking || _state.value is State.Downloading) return
        _state.value = State.Checking
        scope.launch {
            runCatching {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/$REPO/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "TLLPlayer")
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) error("GitHub responded ${resp.code}")
                    val o = JSONObject(resp.body!!.string())
                    val version = o.getString("tag_name").removePrefix("v")
                    val notes = o.optString("body").take(16_000)
                    val assets = o.optJSONArray("assets")
                    var apkUrl: String? = null
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val a = assets.getJSONObject(i)
                            if (a.getString("name").endsWith(".apk")) {
                                apkUrl = a.getString("browser_download_url"); break
                            }
                        }
                    }
                    if (apkUrl == null) error("The latest release has no APK attached")
                    if (isNewer(version, currentVersion)) {
                        _state.value = State.Available(UpdateInfo(version, notes, apkUrl))
                    } else {
                        _state.value = State.UpToDate
                    }
                }
            }.onFailure { _state.value = State.Failed(it.message ?: "Update check failed") }
        }
    }

    /** Downloads the release APK with progress, then opens the system installer. */
    fun downloadAndInstall() {
        val info = (_state.value as? State.Available)?.info ?: return
        _state.value = State.Downloading(0)
        scope.launch {
            runCatching {
                val dir = File(context.filesDir, "updates").apply { mkdirs() }
                val out = File(dir, "tllplayer-update.apk")
                val request = Request.Builder().url(info.apkUrl).header("User-Agent", "TLLPlayer").build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) error("Download failed (${resp.code})")
                    val body = resp.body ?: error("Empty download")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var copied = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                copied += n
                                if (total > 0) _state.value = State.Downloading((copied * 100 / total).toInt())
                            }
                        }
                    }
                }
                install(out)
                _state.value = State.Available(info) // dialog stays sane if the user cancels install
            }.onFailure { _state.value = State.Failed(it.message ?: "Download failed") }
        }
    }

    private fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun reset() {
        if (_state.value !is State.Downloading) _state.value = State.Idle
    }

    /** Numeric segment-wise compare: "1.10.0" > "1.9.3"; non-numeric junk compares as 0. */
    private fun isNewer(remote: String, local: String): Boolean {
        val cleanRemote = remote.trim().removePrefix("v").removePrefix("V")
        val cleanLocal = local.trim().removePrefix("v").removePrefix("V")
        val r = cleanRemote.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val l = cleanLocal.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    companion object {
        const val REPO = "thirumurthy/tll-player"
    }
}
