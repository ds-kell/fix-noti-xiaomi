package io.github.hypernotifyfix.toolkit

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hypernotifyfix.core.AppContainer
import io.github.hypernotifyfix.domain.AssistantSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One catalog entry as found on this device. [installed] = installed for the current user. */
data class DebloatItem(val entry: DebloatEntry, val installed: Boolean, val system: Boolean) {
    /** A non-system app removed for the only user is gone for good and must be reinstalled from a store. */
    val restorable get() = system || !installed
}

data class DefaultAppChoice(val packageName: String, val label: String)
data class DefaultAppRole(val role: String, val title: String, val current: String?, val choices: List<DefaultAppChoice>)

data class BankSafeStatus(val developerOptions: Boolean, val usbDebugging: Boolean, val wirelessDebugging: Boolean) {
    val safe get() = !developerOptions && !usbDebugging && !wirelessDebugging
}

data class ToolkitState(
    val loading: Boolean = true,
    val shizukuReady: Boolean = false,
    val canWriteSecure: Boolean = false,
    val bankSafe: BankSafeStatus? = null,
    val debloat: List<DebloatItem> = emptyList(),
    val roles: List<DefaultAppRole> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
)

class ToolkitViewModel(private val container: AppContainer) : ViewModel() {
    private val context: Context = container.appContext
    private val pm: PackageManager = context.packageManager
    private val mutable = MutableStateFlow(ToolkitState())
    val state: StateFlow<ToolkitState> = mutable

    init { refresh() }

    fun clearMessage() = mutable.update { it.copy(message = null) }

    fun refresh() = viewModelScope.launch {
        val ready = container.executor.isAvailable() && container.executor.effectiveUid() == 2000
        val snapshot = withContext(Dispatchers.IO) { Triple(readDebloat(), readRoles(), readBankSafe()) }
        mutable.update {
            it.copy(loading = false, shizukuReady = ready, canWriteSecure = canWriteSecure(), debloat = snapshot.first, roles = snapshot.second, bankSafe = snapshot.third)
        }
    }

    /** Re-reads only what the user can change in system screens (no Shizuku round trip). */
    fun refreshLocal() = mutable.update { it.copy(bankSafe = readBankSafe(), canWriteSecure = canWriteSecure(), roles = readRoles()) }

    // ---------- Debloat ----------

    private fun readDebloat(): List<DebloatItem> = DebloatCatalog.entries.mapNotNull { entry ->
        val info = runCatching { pm.getApplicationInfo(entry.packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES) }.getOrNull() ?: return@mapNotNull null
        DebloatItem(entry, info.flags and ApplicationInfo.FLAG_INSTALLED != 0, info.flags and ApplicationInfo.FLAG_SYSTEM != 0)
    }

    fun remove(packages: Collection<String>) = runPackages(packages, remove = true)
    fun restore(packages: Collection<String>) = runPackages(packages, remove = false)

    private fun runPackages(packages: Collection<String>, remove: Boolean) = viewModelScope.launch {
        val targets = packages.filter { it in DebloatCatalog.byPackage }
        if (targets.isEmpty() || !requireShizuku()) return@launch
        mutable.update { it.copy(busy = true) }
        var ok = 0
        val failed = mutableListOf<String>()
        for (pkg in targets) {
            val result = runCatching { container.executor.execute(if (remove) ToolkitCommands.remove(pkg) else ToolkitCommands.restore(pkg)) }.getOrNull()
            val installedNow = runCatching { pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES).flags and ApplicationInfo.FLAG_INSTALLED != 0 }.getOrDefault(false)
            if (result?.successful == true && installedNow != remove) ok++ else failed += DebloatCatalog.byPackage[pkg]?.label ?: pkg
        }
        val verb = if (remove) "Đã gỡ" else "Đã khôi phục"
        mutable.update {
            it.copy(busy = false, debloat = readDebloat(), message = if (failed.isEmpty()) "$verb $ok app" else "$verb $ok app; lỗi: ${failed.joinToString()}")
        }
    }

    // ---------- Default apps ----------

    private fun readRoles(): List<DefaultAppRole> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val browser = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        return listOf(
            DefaultAppRole(ToolkitCommands.ROLE_BROWSER, "Trình duyệt", currentDefault(browser), choices(browser)),
            DefaultAppRole(ToolkitCommands.ROLE_HOME, "Màn hình chính (launcher)", currentDefault(home), choices(home)),
        )
    }

    private fun currentDefault(intent: Intent): String? =
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.takeIf { it != "android" }

    private fun choices(intent: Intent): List<DefaultAppChoice> =
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).map { it.activityInfo.packageName }.distinct()
            .filter { it != context.packageName && it != "com.android.settings" }
            .map { DefaultAppChoice(it, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }.getOrDefault(it)) }
            .sortedBy { it.label.lowercase() }

    fun setDefault(role: String, packageName: String) = viewModelScope.launch {
        val roleInfo = state.value.roles.firstOrNull { it.role == role } ?: return@launch
        if (roleInfo.choices.none { it.packageName == packageName } || !requireShizuku()) return@launch
        mutable.update { it.copy(busy = true) }
        val holders = runCatching { AssistantSwitch.parseHolders(container.executor.execute(ToolkitCommands.roleHolders(role)).stdout) }.getOrDefault(emptySet())
        val results = ToolkitCommands.setRole(role, packageName, holders).map { runCatching { container.executor.execute(it) }.getOrNull() }
        val nowHolders = runCatching { AssistantSwitch.parseHolders(container.executor.execute(ToolkitCommands.roleHolders(role)).stdout) }.getOrDefault(emptySet())
        val ok = results.lastOrNull()?.successful == true && packageName in nowHolders
        val label = roleInfo.choices.first { it.packageName == packageName }.label
        mutable.update {
            it.copy(busy = false, roles = readRoles(), message = if (ok) "${roleInfo.title}: đã đặt $label" else "Không đặt được $label, hãy thử trong Cài đặt hệ thống")
        }
    }

    fun openDefaultAppsSettings(launch: (Intent) -> Unit) = launch(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))

    // ---------- Bank-safe mode ----------

    private fun readBankSafe(): BankSafeStatus {
        val resolver = context.contentResolver
        fun global(name: String) = runCatching { Settings.Global.getInt(resolver, name, 0) == 1 }.getOrDefault(false)
        return BankSafeStatus(global(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED), global(Settings.Global.ADB_ENABLED), global("adb_wifi_enabled"))
    }

    private fun canWriteSecure() = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /**
     * Prefers the app's own WRITE_SECURE_SETTINGS (granted with the assistant guard) so it works without
     * Shizuku; otherwise asks Shizuku as its very last command; otherwise the caller opens the system screen.
     * @return false when the user has to switch it off by hand.
     */
    fun enableBankSafe(): Boolean {
        if (canWriteSecure()) {
            val resolver = context.contentResolver
            val done = ToolkitCommands.BANK_SAFE_SETTINGS.all { runCatching { Settings.Global.putInt(resolver, it, 0) }.getOrDefault(false) }
            mutable.update { it.copy(bankSafe = readBankSafe(), message = if (done) "Đã tắt Tùy chọn nhà phát triển. App ngân hàng có thể mở bình thường" else "Chưa tắt hết, hãy tắt tay trong Cài đặt") }
            return done
        }
        if (!state.value.shizukuReady) return false
        viewModelScope.launch {
            // adb going down takes Shizuku with it, so the command may never report back.
            runCatching { container.executor.execute(ToolkitCommands.bankSafe()) }
            mutable.update { it.copy(shizukuReady = false, bankSafe = readBankSafe(), message = "Đã tắt Tùy chọn nhà phát triển. Shizuku sẽ dừng, các thiết lập đã áp dụng vẫn giữ nguyên") }
        }
        return true
    }

    private fun requireShizuku(): Boolean {
        if (state.value.shizukuReady) return true
        mutable.update { it.copy(message = "Cần Shizuku đang chạy: bật Gỡ lỗi không dây, khởi động Shizuku rồi mở lại tab này") }
        return false
    }
}
