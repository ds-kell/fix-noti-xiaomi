package io.github.hypernotifyfix.millethelper

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

/** Control surface for the main app; call() is not covered by provider permissions, so check explicitly. */
class ControlProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val context = requireNotNull(context)
        context.enforceCallingPermission(PERMISSION, "HyperNotify signature required")
        when (method) {
            "configure" -> {
                val packages = extras?.getStringArrayList("packages").orEmpty().filter { it.matches(PACKAGE) }.toSet()
                HelperState(context).setPackages(packages)
                WatchScheduler.schedule(context)
                MilletGuard.protect(context, "configure")
            }
            "protect" -> { WatchScheduler.schedule(context); MilletGuard.protect(context, arg ?: "app") }
            "status" -> Unit
            else -> throw IllegalArgumentException("Unknown method")
        }
        val state = HelperState(context)
        return Bundle().apply {
            putInt("version", 1)
            putBoolean("canWrite", runCatching { Settings.System.canWrite(context) }.getOrDefault(false))
            putInt("packages", state.packages().size)
            putBoolean("lastSuccess", state.lastSuccess())
            putString("lastResult", state.lastResult())
            putLong("lastRun", state.lastRun())
            putBoolean("watcherScheduled", WatchScheduler.isScheduled(context))
            putLong("watcherTriggeredAt", state.watcherTriggeredAt())
            putLong("autoRepairAt", state.autoRepairAt())
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    private companion object {
        const val PERMISSION = "io.github.hypernotifyfix.permission.MILLET_HELPER"
        val PACKAGE = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
    }
}
