package com.thirutricks.tllplayer.core.sync.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import org.koin.core.context.GlobalContext

class KoinWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        val koin = GlobalContext.get()
        return when (workerClassName) {
            EpgSyncWorker::class.java.name -> EpgSyncWorker(
                appContext,
                workerParameters,
                koin.get(),
                koin.get(),
                koin.get(),
                koin.get(),
            )
            CatalogSyncWorker::class.java.name -> CatalogSyncWorker(
                appContext,
                workerParameters,
                koin.get(),
                koin.get(),
                koin.get(),
                koin.get(),
                koin.get(),
                koin.get(),
            )
            ContentIndexWorker::class.java.name -> ContentIndexWorker(
                appContext,
                workerParameters,
                koin.get(),
            )
            else -> null
        }
    }
}
