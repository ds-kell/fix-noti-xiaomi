package io.github.hypernotifyfix.millethelper

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * Event-driven: system_server watches the whitelist URIs and starts [WatchJobService] only when
 * PowerKeeper rewrites one of them. No polling, alarm or long-running process.
 */
object WatchScheduler {
    private const val JOB_ID = 0x484E4657 // "HNFW"

    fun schedule(context: Context): Boolean = runCatching {
        val app = context.applicationContext
        val builder = JobInfo.Builder(JOB_ID, ComponentName(app, WatchJobService::class.java))
            .setTriggerContentUpdateDelay(1_000L)
            .setTriggerContentMaxDelay(5_000L)
        MilletGuard.KEYS.forEach { builder.addTriggerContentUri(JobInfo.TriggerContentUri(Settings.System.getUriFor(it), 0)) }
        app.getSystemService(JobScheduler::class.java).schedule(builder.build()) == JobScheduler.RESULT_SUCCESS
    }.getOrDefault(false)

    fun isScheduled(context: Context) = runCatching { context.getSystemService(JobScheduler::class.java).getPendingJob(JOB_ID) != null }.getOrDefault(false)
}
