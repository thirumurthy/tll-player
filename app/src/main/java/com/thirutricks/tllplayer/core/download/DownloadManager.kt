package com.thirutricks.tllplayer.core.download

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import com.thirutricks.tllplayer.core.database.dao.DownloadDao
import com.thirutricks.tllplayer.core.database.entity.DownloadEntity
import com.thirutricks.tllplayer.core.model.DownloadStatus
import com.thirutricks.tllplayer.core.model.MediaType
import com.thirutricks.tllplayer.core.network.HttpClient
import com.thirutricks.tllplayer.core.storage.StorageAccess
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 12 — downloads movies & series episodes for offline playback. Files go under the user-chosen
 * download folder, organised as `Movies/<name>.<ext>` and `Series/<show>/Season N/<episode>.<ext>`.
 * Downloads run one-at-a-time on an IO scope and push byte-progress to [DownloadDao]; interrupted
 * ones restart on launch.
 */
class DownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val jobs = ConcurrentHashMap<Long, Job>()

    init {
        scope.launch {
            (downloadDao.byStatus(DownloadStatus.RUNNING) + downloadDao.byStatus(DownloadStatus.QUEUED))
                .forEach { start(it.id) }
        }
    }

    fun observe(profileId: Long): Flow<List<DownloadEntity>> = downloadDao.observeForProfile(profileId)

    /** Queue a download into `<root>/<relativeDir>/<fileName>`. */
    fun enqueue(
        profileId: Long, mediaType: MediaType, itemId: Long, title: String, posterUrl: String?,
        streamUrl: String, relativeDir: String, fileName: String,
    ) {
        scope.launch {
            val root = StorageAccess.resolveRoot(context, settings.downloadRoot.first())
            val target = File(File(root, relativeDir).apply { mkdirs() }, fileName)
            val id = downloadDao.upsert(
                DownloadEntity(
                    profileId = profileId, mediaType = mediaType, itemId = itemId, title = title,
                    posterUrl = posterUrl, streamUrl = streamUrl, filePath = target.absolutePath,
                    status = DownloadStatus.QUEUED,
                ),
            )
            start(id)
        }
    }

    fun retry(download: DownloadEntity) {
        scope.launch {
            download.filePath?.let { runCatching { File(it).delete() } } // start fresh
            downloadDao.updateProgress(download.id, DownloadStatus.QUEUED, 0, download.totalBytes, System.currentTimeMillis())
            start(download.id)
        }
    }

    /** Stop the running download but keep the partial file so it can resume. */
    fun pause(download: DownloadEntity) {
        jobs.remove(download.id)?.cancel()
        scope.launch {
            val d = downloadDao.getById(download.id) ?: download
            downloadDao.updateProgress(d.id, DownloadStatus.PAUSED, d.downloadedBytes, d.totalBytes, System.currentTimeMillis())
        }
    }

    /** Continue a paused download from where it stopped (HTTP Range). */
    fun resume(download: DownloadEntity) = start(download.id)

    fun delete(download: DownloadEntity) {
        jobs.remove(download.id)?.cancel()
        scope.launch {
            download.filePath?.let { runCatching { File(it).delete() } }
            downloadDao.delete(download)
        }
    }

    private fun start(id: Long) {
        if (jobs.containsKey(id)) return
        val job = scope.launch { mutex.withLock { runDownload(id) } }
        jobs[id] = job
        job.invokeOnCompletion { jobs.remove(id) }
    }

    private suspend fun runDownload(id: Long) {
        val d = downloadDao.getById(id) ?: return
        val file = d.filePath?.let { File(it) } ?: File(StorageAccess.defaultRoot(context), "$id.mp4")
        file.parentFile?.mkdirs()
        // Resume only a previously-paused download; anything else starts fresh.
        val resuming = d.status == DownloadStatus.PAUSED && file.exists() && file.length() > 0
        if (!resuming && file.exists()) runCatching { file.delete() }
        val existing = if (resuming) file.length() else 0L
        try {
            val rb = Request.Builder().url(d.streamUrl).header("User-Agent", HttpClient.DEFAULT_USER_AGENT)
            if (existing > 0) rb.header("Range", "bytes=$existing-")
            client.newCall(rb.build()).execute().use { resp ->
                val body = resp.body
                if (!resp.isSuccessful || body == null) { markFailed(id, d.totalBytes); return }
                val append = resp.code == 206 && existing > 0 // server honoured the Range
                val total = (if (append) existing else 0L) + body.contentLength().coerceAtLeast(0)
                var done = if (append) existing else 0L
                downloadDao.updateProgress(id, DownloadStatus.RUNNING, done, total, System.currentTimeMillis())
                body.byteStream().use { input ->
                    java.io.FileOutputStream(file, append).use { out ->
                        val buf = ByteArray(128 * 1024)
                        var lastTick = 0L
                        while (true) {
                            if (!currentCoroutineContext().isActive) return
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val t = System.currentTimeMillis()
                            if (t - lastTick > 500) { downloadDao.updateProgress(id, DownloadStatus.RUNNING, done, total, t); lastTick = t }
                        }
                    }
                }
                val size = file.length()
                downloadDao.upsert(d.copy(status = DownloadStatus.COMPLETED, downloadedBytes = size, totalBytes = size, updatedAt = System.currentTimeMillis()))
            }
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) markFailed(id, d.totalBytes)
        }
    }

    private suspend fun markFailed(id: Long, total: Long) {
        downloadDao.updateProgress(id, DownloadStatus.FAILED, 0, total, System.currentTimeMillis())
    }
}
