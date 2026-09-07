package io.github.hypernotifyfix.data

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import io.github.hypernotifyfix.BuildConfig
import io.github.hypernotifyfix.core.model.*
import io.github.hypernotifyfix.core.shell.PrivilegedCommand
import io.github.hypernotifyfix.core.shell.PrivilegedCommandExecutor
import io.github.hypernotifyfix.domain.PackageValidator
import io.github.hypernotifyfix.domain.XiaomiWhitelist
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

data class PublicDiagnostic(val notificationsEnabled: Boolean, val batteryExempt: Boolean, val backgroundRestricted: Boolean?)
class PublicDiagnosticRepository(private val context: Context) {
    fun read(packageName: String): PublicDiagnostic {
        val pm = context.packageManager; val info = pm.getApplicationInfo(packageName, 0)
        val appContext = context.createPackageContext(packageName, 0)
        val notifications = if (Build.VERSION.SDK_INT >= 24) appContext.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() ?: false else true
        val battery = context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) ?: false
        val restricted = if (Build.VERSION.SDK_INT >= 28) context.getSystemService(AppOpsManager::class.java)?.let {
            val mode = if (Build.VERSION.SDK_INT >= 29) it.unsafeCheckOpNoThrow("android:run_any_in_background", info.uid, packageName) else @Suppress("DEPRECATION") it.checkOpNoThrow("android:run_any_in_background", info.uid, packageName)
            mode != AppOpsManager.MODE_ALLOWED
        } else null
        return PublicDiagnostic(notifications, battery, restricted)
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
    fun markRestored(packages: Set<String>) = replace(this.packages() - packages)
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
}

class BackgroundMaintenanceStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("background_maintenance", Context.MODE_PRIVATE)
    private val keys = listOf("MILLET_NO_RESTRICT_APP", "millet_white", "cloud_lowlatency_whitelist")

    fun canWrite(): Boolean = Settings.System.canWrite(context)
    fun packages(): Set<String> = prefs.getStringSet("packages", emptySet()).orEmpty()
    fun setPackages(packages: Set<String>) {
        val safe = packages.filter { it.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) }.toSet()
        prefs.edit().putStringSet("packages", safe).apply()
    }
    fun lastResult(): String? = prefs.getString("last_result", null)

    fun reapply(): Boolean {
        if (!canWrite()) return record(false, "Chưa có quyền sửa cài đặt hệ thống")
        val protected = packages()
        if (protected.isEmpty()) return record(false, "Chưa có ứng dụng cần duy trì")
        var appliedKeys = 0
        for (key in keys) {
            val current = Settings.System.getString(context.contentResolver, key) ?: continue
            var merged = current
            for (pkg in protected.sorted()) merged = XiaomiWhitelist.merge(merged, pkg)
                ?: return record(false, "Định dạng $key không an toàn")
            val written = Settings.System.putString(context.contentResolver, key, merged)
            val verified = written && protected.all { XiaomiWhitelist.parse(Settings.System.getString(context.contentResolver, key).orEmpty())?.tokens?.contains(it) == true }
            if (!verified) return record(false, "Không xác minh được $key sau khi ghi")
            appliedKeys++
        }
        return record(appliedKeys > 0, if (appliedKeys > 0) "Đã tự phục hồi $appliedKeys whitelist nền Xiaomi" else "ROM không cung cấp whitelist Xiaomi có thể ghi")
    }

    private fun record(success: Boolean, message: String): Boolean {
        prefs.edit().putString("last_result", message).putLong("last_run", System.currentTimeMillis()).apply()
        return success
    }
}
