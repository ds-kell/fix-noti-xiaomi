package io.github.hypernotifyfix.core.helper

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import io.github.hypernotifyfix.core.shell.PrivilegedCommand
import io.github.hypernotifyfix.core.shell.PrivilegedCommandExecutor
import io.github.hypernotifyfix.domain.AppOpsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

data class HelperStatus(
    val canWrite: Boolean,
    val packages: Int,
    val lastSuccess: Boolean,
    val lastResult: String?,
    val lastRun: Long,
    val watcherScheduled: Boolean,
    val watcherTriggeredAt: Long,
    val autoRepairAt: Long,
)

/**
 * The `:millet-helper` APK targets SDK 22 so it can write MILLET_NO_RESTRICT_APP with plain
 * WRITE_SETTINGS (no Shizuku after reboot). This client installs it via Shizuku and drives it
 * through its signature-protected provider.
 */
class MilletHelperClient(context: Context) {
    private val context = context.applicationContext

    fun installedVersion(): Long? = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0).let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() } }.getOrNull()
    fun isInstalled() = installedVersion() != null
    fun needsUpdate() = installedVersion()?.let { it < BUNDLED_VERSION } ?: false

    fun status(): HelperStatus? = call("status")
    fun protect(reason: String): HelperStatus? = call("protect", reason)
    fun configure(packages: Set<String>): HelperStatus? = call("configure", extras = Bundle().apply { putStringArrayList("packages", ArrayList(packages.sorted())) })

    private fun call(method: String, arg: String? = null, extras: Bundle? = null): HelperStatus? = runCatching {
        context.contentResolver.call(Uri.parse("content://$AUTHORITY"), method, arg, extras)
    }.getOrNull()?.let {
        HelperStatus(it.getBoolean("canWrite"), it.getInt("packages"), it.getBoolean("lastSuccess"), it.getString("lastResult"), it.getLong("lastRun"), it.getBoolean("watcherScheduled"), it.getLong("watcherTriggeredAt"), it.getLong("autoRepairAt"))
    }

    /** Returns null on success, otherwise a user-facing error. */
    suspend fun install(executor: PrivilegedCommandExecutor): String? = withContext(Dispatchers.IO) {
        if (!executor.isAvailable()) return@withContext "Cần Shizuku để cài helper"
        val source = runCatching {
            File(context.getExternalFilesDir(null), "millet-helper.apk").also { target -> context.assets.open("millet-helper.apk").use { input -> target.outputStream().use { input.copyTo(it) } } }
        }.getOrElse { return@withContext "Không chuẩn bị được APK helper" }
        // Stage in /data/local/tmp so the shell-side installer never depends on scoped-storage access.
        val staged = "/data/local/tmp/hypernotify-millet-helper.apk"
        val apk = if (executor.execute(PrivilegedCommand("helper.stage", listOf("cp", source.absolutePath, staged))).successful) staged else source.absolutePath
        // Android 14+ blocks installing targetSdk < 23 unless the shell installer opts out explicitly.
        val bypass = if (Build.VERSION.SDK_INT >= 34) listOf("--bypass-low-target-sdk-block") else emptyList()
        val result = executor.execute(PrivilegedCommand("helper.install", listOf("pm", "install", "-r") + bypass + apk, 30_000))
        executor.execute(PrivilegedCommand("helper.unstage", listOf("rm", "-f", staged)))
        source.delete()
        if (!result.successful || !result.stdout.contains("Success")) return@withContext result.stderr.ifBlank { result.stdout }.ifBlank { "pm install thất bại" }
        // SecurityCenter resets Autostart for new packages shortly after PACKAGE_ADDED; grant after it settles.
        repeat(3) { delay(1_500); if (ensureAccess(executor)) return@withContext null }
        "Đã cài helper nhưng HyperOS chưa cho tự khởi chạy; mở lại app khi Shizuku đang chạy"
    }

    /**
     * HyperOS blocks the helper unless all of these hold (verified on Xiaomi 15 Ultra, HyperOS 3):
     * - Android requires a permissions review before any component of a targetSdk < 23 app runs;
     *   the implicitly added POST_NOTIFICATIONS carries REVIEW_REQUIRED and blocks the provider.
     * - WakePathChecker rejects provider/job starts unless Autostart (MIUI ops 10008/10053) is allowed.
     * The Doze exemption also keeps the helper in the EXEMPTED standby bucket so content-trigger jobs run promptly.
     * Returns true when Autostart reads back as allowed.
     */
    suspend fun ensureAccess(executor: PrivilegedCommandExecutor): Boolean = withContext(Dispatchers.IO) {
        if (!isInstalled() || !executor.isAvailable()) return@withContext false
        if (Build.VERSION.SDK_INT >= 33) executor.execute(PrivilegedCommand("helper.review", listOf("pm", "clear-permission-flags", PACKAGE, "android.permission.POST_NOTIFICATIONS", "review-required")))
        executor.execute(PrivilegedCommand("helper.write_settings", listOf("cmd", "appops", "set", PACKAGE, "WRITE_SETTINGS", "allow")))
        listOf("10008", "10053").forEach { op -> executor.execute(PrivilegedCommand("helper.autostart_$op", listOf("cmd", "appops", "set", PACKAGE, op, "allow"))) }
        executor.execute(PrivilegedCommand("helper.doze", listOf("cmd", "deviceidle", "whitelist", "+$PACKAGE")))
        val autostart = executor.execute(PrivilegedCommand("helper.autostart_read", listOf("cmd", "appops", "get", PACKAGE, "10008")))
        autostart.successful && AppOpsParser.parse(autostart.stdout, "10008") == "allow"
    }

    suspend fun uninstall(executor: PrivilegedCommandExecutor): String? = withContext(Dispatchers.IO) {
        if (!executor.isAvailable()) return@withContext "Cần Shizuku để gỡ helper"
        val result = executor.execute(PrivilegedCommand("helper.uninstall", listOf("pm", "uninstall", PACKAGE)))
        if (result.successful && result.stdout.contains("Success")) null else result.stderr.ifBlank { "pm uninstall thất bại" }
    }

    companion object {
        const val PACKAGE = "io.github.hypernotifyfix.millethelper"
        private const val AUTHORITY = "io.github.hypernotifyfix.millethelper.control"
        /** Keep in sync with millet-helper versionCode. */
        const val BUNDLED_VERSION = 1L
    }
}
