package io.github.hypernotifyfix.core.system

import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Permission-free HyperOS nudges for Google Play services, ported from GMS-Protect. */
object GmsSignals {
    const val GMS = "com.google.android.gms"

    /** Asks MiPush/Greezer to re-add GMS to its protect list. */
    fun refreshMiPush(context: Context) = runCatching {
        context.sendBroadcast(Intent("com.xiaomi.mipush.REFRESH_WHITE_LIST_ACTION").putExtra("pkgName", GMS).putExtra("option", 1))
    }.isSuccess

    /**
     * Does not write anything: notifying the existing Secure URI makes system_server's observer
     * re-read immobulus_mode_switch_restrict and rebuild Aurogon's runtime allowlist.
     */
    fun reloadAurogon(context: Context) = runCatching {
        context.contentResolver.notifyChange(Settings.Secure.getUriFor("immobulus_mode_switch_restrict"), null)
    }.isSuccess

    fun refreshAll(context: Context) { refreshMiPush(context); reloadAurogon(context) }
}
