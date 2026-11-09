package io.github.hypernotifyfix.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.github.hypernotifyfix.BuildConfig
import io.github.hypernotifyfix.core.helper.MilletHelperClient
import io.github.hypernotifyfix.core.model.*
import io.github.hypernotifyfix.core.shell.PrivilegedCommand
import io.github.hypernotifyfix.core.shell.PrivilegedCommandExecutor
import io.github.hypernotifyfix.core.system.GmsSignals
import io.github.hypernotifyfix.domain.PackageValidator
import io.github.hypernotifyfix.domain.WhitelistRepair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

class AppCatalog(private val context: Context) : PackageValidator {
    fun list(): List<InstalledApp> = context.packageManager.getInstalledApplications(0).map { info ->
        val packageInfo = runCatching { context.packageManager.getPackageInfo(info.packageName, 0) }.getOrNull()
        InstalledApp(context.packageManager.getApplicationLabel(info).toString(), info.packageName, packageInfo?.versionName.orEmpty(), info.flags and ApplicationInfo.FLAG_SYSTEM != 0, info.enabled)
    }.sortedBy { it.label.lowercase() }
    fun icon(packageName: String) = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    override fun isInstalled(packageName: String) = list().any { it.packageName == packageName }
}

class DeviceRepository(private val executor: PrivilegedCommandExecutor) {
    suspend fun read(): DeviceInfo {
        suspend fun prop(name: String) = runCatching { executor.execute(PrivilegedCommand("prop.${name.replace('.', '_')}", listOf("getprop", name), 2_000)).stdout.trim().ifBlank { null } }.getOrNull()
        val hyper = listOf("ro.mi.os.version.name", "ro.miui.ui.version.name", "ro.build.version.incremental").firstNotNullOfOrNull { prop(it) }
        val fingerprintHash = MessageDigest.getInstance("SHA-256").digest(Build.FINGERPRINT.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
        val suffix = Regex("(?:CNXM|MIXM|EUXM)", RegexOption.IGNORE_CASE).find(listOfNotNull(hyper, Build.FINGERPRINT).joinToString(" "))?.value?.uppercase()
        return DeviceInfo(Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE, Build.VERSION.SDK_INT, fingerprintHash, Build.VERSION.SECURITY_PATCH.orEmpty(), hyper, suffix)
    }
}

class SessionStore(private val context: Context) {
    private val json = Json { prettyPrint = true; encodeDefaults = true; classDiscriminator = "kind" }
    private val dir get() = File(context.filesDir, "sessions").apply { mkdirs() }
    suspend fun save(session: OptimizationSession): Boolean = withContext(Dispatchers.IO) { runCatching { val target = File(dir, "${session.sessionId}.json"); val temp = File(dir, "${session.sessionId}.tmp"); temp.writeText(json.encodeToString(session)); if (!temp.renameTo(target)) error("atomic rename failed"); true }.getOrDefault(false) }
    suspend fun list(): List<OptimizationSession> = withContext(Dispatchers.IO) { dir.listFiles { f -> f.extension == "json" }?.mapNotNull { runCatching { json.decodeFromString<OptimizationSession>(it.readText()) }.getOrNull() }?.sortedByDescending { it.createdAtEpochMs }.orEmpty() }
}

/** Small durable index; session JSON remains the source of truth for exact rollback values. */
class OptimizationRegistry(context: Context) {
    private val prefs = context.getSharedPreferences("optimization_registry", Context.MODE_PRIVATE)
    fun packages(): Set<String> = prefs.getStringSet("optimized_packages", emptySet()).orEmpty()
    fun initialized(): Boolean = prefs.contains("optimized_packages")
    fun replace(packages: Set<String>) = prefs.edit().putStringSet("optimized_packages", packages).apply()
    fun markOptimized(packages: Set<String>) = replace(this.packages() + packages)
    fun markRestored(packages: Set<String>) { replace(this.packages() - packages); replaceAlreadyOptimal(alreadyOptimal() - packages) }

    /** Found fully optimal with nothing to change (e.g. by an earlier install whose backups are gone): shown, never restorable. */
    fun alreadyOptimal(): Set<String> = prefs.getStringSet("already_optimal_packages", emptySet()).orEmpty()
    fun replaceAlreadyOptimal(packages: Set<String>) = prefs.edit().putStringSet("already_optimal_packages", packages).apply()
}

class ReportExporter(private val context: Context) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    fun export(device: DeviceInfo, shizuku: String, uid: Int?, results: List<OperationResult>): Pair<File, File> {
        val safeResults = results.map { it.copy(message = Redactor.redact(it.message)) }
        val payload = mapOf("appVersion" to BuildConfig.VERSION_NAME, "device" to "${device.manufacturer} ${device.model}", "android" to device.sdk.toString(), "hyperOs" to (device.hyperOsVersion ?: "unknown"), "romRegion" to (device.romRegion ?: "unknown"), "fingerprintHash" to device.fingerprintHash, "shizuku" to shizuku, "effectiveUid" to (uid?.toString() ?: "unknown"), "results" to json.encodeToString(safeResults))
        val jsonFile = File(context.cacheDir, "hypernotify-report.json").apply { writeText(json.encodeToString(payload)) }
        val textFile = File(context.cacheDir, "hypernotify-report.txt").apply { writeText(payload.entries.joinToString("\n") { "${it.key}: ${it.value}" }) }
        return jsonFile to textFile
    }
}
object Redactor {
    private val patterns = listOf(Regex("(?i)(token|authorization|cookie|account)\\s*[:=]\\s*\\S+"), Regex("/data/user/\\d+/[^\\s]+"))
    fun redact(value: String) = patterns.fold(value) { text, regex -> regex.replace(text, "[REDACTED]") }.take(8_192)
}

class SettingsNavigator(private val context: Context) {
    private fun launch(candidates: List<Intent>): Boolean { val intent = candidates.firstOrNull { it.resolveActivity(context.packageManager) != null } ?: return false; intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent); return true }
    fun appInfo(pkg: String) = launch(listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))))
    fun notifications(pkg: String) = launch(listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))))
    fun battery(pkg: String) = launch(listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))))
    fun xiaomiAutostart(pkg: String) = launch(listOf(Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))))
    fun modifySystemSettings() = launch(listOf(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))))

    /**
     * "Assist & voice input" (its "Digital assistant app" row moves the assistant role; the direct
     * MANAGE_DEFAULT_APP picker needs a system permission). With [popupFor], Xiaomi's permission editor
     * for that app opens on top, so Back lands on the picker.
     */
    fun assistantPicker(popupFor: String?): Boolean {
        val picker = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).takeIf { it.resolveActivity(context.packageManager) != null } ?: return false
        val editor = popupFor?.let { Intent("miui.intent.action.APP_PERM_EDITOR").setPackage("com.miui.securitycenter").putExtra("extra_pkgname", it) }
            ?.takeIf { it.resolveActivity(context.packageManager) != null }
        context.startActivities(listOfNotNull(picker.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), editor).toTypedArray())
        return true
    }
}

class BackgroundMaintenanceStore(context: Context) {
    private val context = context.applicationContext
    // Device-protected so the boot receiver can run before first unlock (LOCKED_BOOT_COMPLETED).
    private val prefs = this.context.createDeviceProtectedStorageContext().let { storage ->
        runCatching { if (!storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains("packages")) storage.moveSharedPreferencesFrom(this.context, PREFS) }
        storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
    private val keys = listOf("MILLET_NO_RESTRICT_APP", "millet_white", "cloud_lowlatency_whitelist")
    val helper = MilletHelperClient(this.context)

    fun canWrite(): Boolean = runCatching { Settings.System.canWrite(context) }.getOrDefault(false)
    fun packages(): Set<String> = prefs.getStringSet("packages", emptySet()).orEmpty()
    fun setPackages(packages: Set<String>) {
        val safe = packages.filter { it.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) }.toSet()
        prefs.edit().putStringSet("packages", safe).apply()
    }
    fun lastResult(): String? = prefs.getString("last_result", null)

    /** User selection plus Play Store and HyperNotify itself, mirroring GMS-Protect's protected set. */
    private fun requiredPackages(): Set<String> {
        val extras = listOf(PLAY_STORE, context.packageName).filter { pkg -> runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess }
        return packages() + extras
    }

    fun reapply(reason: String = "app"): Boolean {
        if (packages().isEmpty()) return record(false, "Chưa có ứng dụng cần duy trì")
        val required = requiredPackages()
        if (helper.isInstalled()) {
            // configure() also runs the protection pass and re-arms the helper's MILLET watcher.
            helper.configure(required)?.let { status -> return record(status.lastSuccess, status.lastResult ?: "Helper không trả kết quả") }
            // Writing directly would only hit the targetSdk block the helper exists to avoid.
            GmsSignals.refreshAll(context)
            return record(false, "HyperOS chặn khởi chạy MILLET helper; mở app khi Shizuku đang chạy để cấp lại quyền tự khởi chạy")
        }
        GmsSignals.refreshAll(context)
        return reapplyDirect(required, reason)
    }

    /** Fallback without the helper: works only where HyperOS still accepts the write from a modern targetSdk. */
    private fun reapplyDirect(required: Set<String>, reason: String): Boolean {
        if (!canWrite()) return record(false, "Chưa có quyền sửa cài đặt hệ thống")
        var healthyKeys = 0
        var repairedKeys = 0
        for (key in keys) {
            val current = runCatching { Settings.System.getString(context.contentResolver, key) }
                .getOrElse { return record(false, "HyperOS không cho đọc khóa $key") }
            when (val decision = WhitelistRepair.plan(current, prefs.getString("last_good_$key", null), required)) {
                WhitelistRepair.Decision.Absent -> continue
                is WhitelistRepair.Decision.Refuse -> return record(false, "Định dạng $key không an toàn")
                is WhitelistRepair.Decision.Unchanged -> { rememberLastGood(key, decision.healthy); healthyKeys++ }
                is WhitelistRepair.Decision.Write -> {
                    val written = runCatching { Settings.System.putString(context.contentResolver, key, decision.value) }
                        .getOrElse { return record(false, "HyperOS chặn ghi khóa $key với targetSdk hiện đại; hãy cài MILLET helper") }
                    val readBack = runCatching { Settings.System.getString(context.contentResolver, key) }
                        .getOrElse { return record(false, "Đã ghi nhưng HyperOS chặn đọc lại khóa $key") }
                    if (!written || !WhitelistRepair.containsAll(readBack, required)) return record(false, "Không xác minh được $key sau khi ghi")
                    rememberLastGood(key, readBack!!)
                    healthyKeys++; repairedKeys++
                }
            }
        }
        return record(healthyKeys > 0, when {
            healthyKeys == 0 -> "ROM không cung cấp whitelist Xiaomi có thể ghi"
            repairedKeys > 0 -> "Đã tự phục hồi $repairedKeys whitelist nền Xiaomi ($reason)"
            else -> "Whitelist Xiaomi vẫn đầy đủ ($reason)"
        })
    }

    private fun rememberLastGood(key: String, value: String) { if (value.isNotBlank()) prefs.edit().putString("last_good_$key", value).apply() }

    private fun record(success: Boolean, message: String): Boolean {
        prefs.edit().putString("last_result", message).putLong("last_run", System.currentTimeMillis()).apply()
        return success
    }

    private companion object {
        const val PREFS = "background_maintenance"
        const val PLAY_STORE = "com.android.vending"
    }
}
