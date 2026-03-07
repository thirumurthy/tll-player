package com.thirutricks.tllplayer.infrastructure

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.thirutricks.tllplayer.models.TVList
import java.util.concurrent.TimeUnit

class LauncherChannelJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.i(TAG, "onStartJob")
        
        // Refresh launcher channels
        // Note: TVList might not be initialized here if the app is not running.
        // We might need a minimal initialization or wait for it.
        // Since TVList.init(context) is called from MainActivity, 
        // we might need to initialize it here too if we want background updates.
        
        if (TVList.size() == 0) {
            TVList.init(applicationContext)
        }
        
        LauncherChannelHelper.updateFavoritesChannel(applicationContext, TVList)
        
        jobFinished(params, false)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }

    companion object {
        private const val TAG = "LauncherChannelJobService"
        private const val JOB_ID = 1001

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            
            val jobInfo = JobInfo.Builder(JOB_ID, ComponentName(context, LauncherChannelJobService::class.java))
                .setPeriodic(TimeUnit.HOURS.toMillis(12)) // Refresh every 12 hours
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build()

            scheduler.schedule(jobInfo)
        }
    }
}
