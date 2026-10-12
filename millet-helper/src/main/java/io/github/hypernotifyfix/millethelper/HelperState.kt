package io.github.hypernotifyfix.millethelper

import android.content.Context
import android.os.Build

/** Device-protected storage so the list survives reboot and is readable before first unlock. */
class HelperState(context: Context) {
    private val prefs = context.applicationContext.createDeviceProtectedStorageContext()
        .getSharedPreferences("millet_helper", Context.MODE_PRIVATE)

    fun packages(): Set<String> = prefs.getStringSet("packages", emptySet()).orEmpty()
    fun setPackages(packages: Set<String>) = prefs.edit().putStringSet("packages", packages).commit()

    fun lastGood(key: String): String? = prefs.getString("last_good_$key", null)
    fun setLastGood(key: String, value: String) { if (value.isNotBlank() && lastGood(key) != value) prefs.edit().putString("last_good_$key", value).apply() }

    fun record(success: Boolean, message: String) = prefs.edit()
        .putBoolean("last_success", success).putString("last_result", message).putLong("last_run", System.currentTimeMillis()).apply()
    fun lastSuccess() = prefs.getBoolean("last_success", false)
    fun lastResult(): String? = prefs.getString("last_result", null)
    fun lastRun() = prefs.getLong("last_run", 0L)

    // Evidence is only meaningful for the ROM build it was observed on; an OTA can change PowerKeeper.
    fun markWatcherTriggered() = evidence().putLong("watcher_triggered_at", System.currentTimeMillis()).apply()
    fun markAutoRepaired() = evidence().putLong("auto_repair_at", System.currentTimeMillis()).apply()
    fun watcherTriggeredAt() = if (currentBuild()) prefs.getLong("watcher_triggered_at", 0L) else 0L
    fun autoRepairAt() = if (currentBuild()) prefs.getLong("auto_repair_at", 0L) else 0L

    private fun currentBuild() = prefs.getString("fingerprint", null) == Build.FINGERPRINT
    private fun evidence() = prefs.edit().apply {
        if (!currentBuild()) remove("watcher_triggered_at").remove("auto_repair_at").putString("fingerprint", Build.FINGERPRINT)
    }
}
