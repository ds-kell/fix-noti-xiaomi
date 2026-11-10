package io.github.hypernotifyfix.millethelper

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import io.github.hypernotifyfix.domain.WhitelistRepair

object MilletGuard {
    private const val TAG = "MilletGuard"
    val KEYS = listOf("MILLET_NO_RESTRICT_APP", "millet_white", "cloud_lowlatency_whitelist")
    private const val GMS = "com.google.android.gms"

    data class Report(val success: Boolean, val message: String, val repairedKeys: Int)

    @Synchronized
    fun protect(context: Context, reason: String, triggeredByWatcher: Boolean = false): Report {
        val app = context.applicationContext
        val state = HelperState(app)
        val configured = state.packages()
        if (configured.isEmpty()) return finish(state, false, t("Chưa nhận danh sách ứng dụng từ HyperOS Toolkit", "Has not received the app list from HyperOS Toolkit"), 0)
        if (!runCatching { Settings.System.canWrite(app) }.getOrDefault(false)) return finish(state, false, t("Helper chưa có quyền sửa cài đặt hệ thống", "The helper cannot modify system settings yet"), 0)
        val required = configured + app.packageName
        var healthyKeys = 0
        var repairedKeys = 0
        for (key in KEYS) {
            val current = runCatching { Settings.System.getString(app.contentResolver, key) }
                .getOrElse { return finish(state, false, t("Không đọc được $key: ${it.javaClass.simpleName}", "Could not read $key: ${it.javaClass.simpleName}"), repairedKeys) }
            when (val decision = WhitelistRepair.plan(current, state.lastGood(key), required)) {
                WhitelistRepair.Decision.Absent -> continue
                is WhitelistRepair.Decision.Refuse -> return finish(state, false, "$key: ${decision.reason}", repairedKeys)
                is WhitelistRepair.Decision.Unchanged -> { state.setLastGood(key, decision.healthy); healthyKeys++ }
                is WhitelistRepair.Decision.Write -> {
                    val written = runCatching { Settings.System.putString(app.contentResolver, key, decision.value) }
                        .getOrElse { return finish(state, false, t("HyperOS chặn ghi $key: ${it.javaClass.simpleName}", "HyperOS blocked writing $key: ${it.javaClass.simpleName}"), repairedKeys) }
                    val readBack = runCatching { Settings.System.getString(app.contentResolver, key) }.getOrNull()
                    if (!written || !WhitelistRepair.containsAll(readBack, required)) return finish(state, false, t("Không xác minh được $key sau khi ghi", "Could not verify $key after writing"), repairedKeys)
                    state.setLastGood(key, readBack!!)
                    Log.i(TAG, "repaired key=$key reason=$reason restoredFromLastGood=${decision.restoredFromLastGood}")
                    healthyKeys++; repairedKeys++
                }
            }
        }
        if (triggeredByWatcher && repairedKeys > 0) state.markAutoRepaired()
        sendHyperOsSignals(app)
        if (healthyKeys == 0) return finish(state, false, t("ROM không có whitelist MILLET nào", "The ROM has no MILLET whitelist"), 0)
        return finish(state, true, if (repairedKeys > 0) t("Đã sửa lại $repairedKeys whitelist ($reason)", "Repaired $repairedKeys whitelists ($reason)") else t("Whitelist vẫn đầy đủ ($reason)", "Whitelists still complete ($reason)"), repairedKeys)
    }

    /** Ask MiPush/Greezer to re-protect GMS and Aurogon to reload its runtime allowlist; both need no permission. */
    private fun sendHyperOsSignals(app: Context) {
        runCatching { app.sendBroadcast(Intent("com.xiaomi.mipush.REFRESH_WHITE_LIST_ACTION").putExtra("pkgName", GMS).putExtra("option", 1)) }
        runCatching { app.contentResolver.notifyChange(Settings.Secure.getUriFor("immobulus_mode_switch_restrict"), null) }
    }

    private fun finish(state: HelperState, success: Boolean, message: String, repaired: Int): Report {
        state.record(success, message)
        Log.i(TAG, "result success=$success message=$message")
        return Report(success, message, repaired)
    }
}
