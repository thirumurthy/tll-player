package com.thirutricks.tllplayer.core.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * Filesystem access for downloads & backup. Android TV usually lacks the SAF document-picker, so we
 * use plain [File] access: an app-specific dir works with no permission, and "All files access"
 * (MANAGE_EXTERNAL_STORAGE) unlocks user-chosen folders elsewhere on storage.
 */
object StorageAccess {

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /** Opens the system screen to grant All-files access (best effort; some TVs lack the screen). */
    fun requestAllFilesAccess(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val perApp = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(perApp) }.isFailure) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /** App-specific external dir — always writable, no permission. Visible under Android/data/<pkg>/files. */
    fun defaultRoot(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "OwnTV").apply { mkdirs() }

    /** The effective base folder: the configured path if usable, else the default. */
    fun resolveRoot(context: Context, configured: String?): File {
        val dir = configured?.takeIf { it.isNotBlank() }?.let { File(it) }
        return if (dir != null && (dir.exists() || dir.mkdirs())) dir else defaultRoot(context)
    }

    /** Top-level browsable storage roots (label → dir) for the folder picker. */
    fun storageRoots(context: Context): List<Pair<String, File>> {
        val roots = LinkedHashMap<String, File>()
        val internal = Environment.getExternalStorageDirectory()
        if (internal != null && internal.exists()) roots["Internal storage"] = internal
        // Removable volumes: derive each volume root from its app-specific dir (…/Android/data/pkg/files).
        context.getExternalFilesDirs(null).forEach { f ->
            val vol = f?.parentFile?.parentFile?.parentFile?.parentFile
            if (vol != null && vol.exists() && vol.absolutePath != internal?.absolutePath) {
                roots[vol.name.ifBlank { "Removable storage" }] = vol
            }
        }
        roots["App storage (no permission needed)"] = defaultRoot(context)
        return roots.map { it.key to it.value }
    }

    /** Strips characters that are illegal in file/folder names. */
    fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifBlank { "untitled" }.take(120)

    /** Best-effort file extension from a stream URL (defaults to mp4). */
    fun extOf(url: String): String {
        val ext = url.substringAfterLast('/', "").substringBefore('?').substringAfterLast('.', "")
        return ext.takeIf { it.isNotBlank() && it.length <= 4 } ?: "mp4"
    }
}
