package io.github.hypernotifyfix

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.hypernotifyfix.core.system.AurogonKeepAlive
import io.github.hypernotifyfix.data.BackgroundMaintenanceStore
import kotlin.concurrent.thread

class BackgroundMaintenanceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_USER_UNLOCKED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        AurogonKeepAlive.startIfEnabled(context)
        // The helper call crosses processes; keep it off the main thread but inside the broadcast lifetime.
        val pending = goAsync()
        thread(name = "hypernotify-boot") {
            try { BackgroundMaintenanceStore(context).reapply("boot") } finally { pending.finish() }
        }
    }
}
