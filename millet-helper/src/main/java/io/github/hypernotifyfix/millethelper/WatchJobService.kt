package io.github.hypernotifyfix.millethelper

import android.app.job.JobParameters
import android.app.job.JobService

class WatchJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val triggered = !params?.triggeredContentUris.isNullOrEmpty()
        // Strongest passive proof that HyperOS lets system_server wake this app on a whitelist change.
        if (triggered) HelperState(this).markWatcherTriggered()
        MilletGuard.protect(this, "watcher", triggeredByWatcher = triggered)
        // Content-trigger jobs are one-shot. Our own write re-fires once and then finds nothing to do.
        WatchScheduler.schedule(this)
        return false
    }

    override fun onStopJob(params: JobParameters?) = false
}
