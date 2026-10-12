package io.github.hypernotifyfix.core.system

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

enum class AutostartState { ALLOWED, DENIED, UNKNOWN }

/** Read-only checks of this app's own HyperOS/Android background setup. */
object SetupState {
    private const val OP_AUTO_START = 10008

    /** Xiaomi stores Autostart as AppOp 10008; the hidden read may be blocked, which yields UNKNOWN. */
    fun autostart(context: Context): AutostartState = runCatching {
        val manager = context.getSystemService(AppOpsManager::class.java)
        val method = AppOpsManager::class.java.getDeclaredMethod("checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
        val mode = method.invoke(manager, OP_AUTO_START, context.applicationInfo.uid, context.packageName) as Int
        if (mode == AppOpsManager.MODE_ALLOWED) AutostartState.ALLOWED else AutostartState.DENIED
    }.getOrDefault(AutostartState.UNKNOWN)

    fun batteryExempt(context: Context) = runCatching { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
    fun backgroundRestricted(context: Context) = Build.VERSION.SDK_INT >= 28 && runCatching { context.getSystemService(ActivityManager::class.java).isBackgroundRestricted }.getOrDefault(false)

    fun requestBatteryExemption(context: Context): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )
        val intent = candidates.firstOrNull { it.resolveActivity(context.packageManager) != null } ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
