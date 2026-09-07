package io.github.hypernotifyfix

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.hypernotifyfix.data.BackgroundMaintenanceStore

class BackgroundMaintenanceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_USER_UNLOCKED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            BackgroundMaintenanceStore(context).reapply()
        }
    }
}
