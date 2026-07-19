package com.thirutricks.tllplayer.core.sync.work

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.thirutricks.tllplayer.core.sync.ImportFinalizer

class ContentIndexWorker(
    context: Context,
    params: WorkerParameters,
    private val importFinalizer: ImportFinalizer,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reason = inputData.getString(KEY_REASON) ?: "unknown"
        val startedAt = SystemClock.elapsedRealtime()
        Log.i(TAG, "Starting content index build reason=$reason")
        importFinalizer.ensureContentIndexes()
        Log.i(TAG, "Content index build finished reason=$reason ms=${SystemClock.elapsedRealtime() - startedAt}")
        return Result.success()
    }

    companion object {
        const val TAG = "ContentIndexWorker"
        const val KEY_REASON = "reason"
        fun workName(): String = "content-index-build"
    }
}
