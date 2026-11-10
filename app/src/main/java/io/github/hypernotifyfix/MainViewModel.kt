package io.github.hypernotifyfix

import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hypernotifyfix.core.AppContainer
import io.github.hypernotifyfix.core.helper.HelperStatus
import io.github.hypernotifyfix.core.shell.PrivilegedCommand
import io.github.hypernotifyfix.core.system.AssistantGuard
import io.github.hypernotifyfix.core.system.AurogonKeepAlive
import io.github.hypernotifyfix.core.system.AutostartState
import io.github.hypernotifyfix.core.system.FcmProbe
import io.github.hypernotifyfix.core.system.FcmState
import io.github.hypernotifyfix.core.system.FcmStatus
import io.github.hypernotifyfix.core.system.SetupState
import io.github.hypernotifyfix.core.model.*
import io.github.hypernotifyfix.domain.AssistantCandidate
import io.github.hypernotifyfix.domain.AssistantSwitch
import io.github.hypernotifyfix.domain.OperationTransaction
import io.github.hypernotifyfix.domain.PackageReset
import io.github.hypernotifyfix.domain.StandbyBucketParser
import io.github.hypernotifyfix.domain.XiaomiWhitelist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.UUID

data class UiState(
    val loading: Boolean = true,
    val dryRun: Boolean = true,
    val advanced: Boolean = false,
    val experimental: Boolean = false,
    val xiaomiAutostartOps: Boolean = false,
    val networkWhitelist: Boolean = false,
    val aggressiveAppOps: Boolean = false,
    val aggressiveSystemProfile: Boolean = false,
    val greezerGmsLimit: Boolean = false,
    val aurogonKeepAlive: Boolean = false,
    val assistantGuard: AssistantGuard.Status? = null,
    val assistantCandidates: List<AssistantCandidate> = emptyList(),
    val helperInstalled: Boolean = false,
    val helperNeedsUpdate: Boolean = false,
    val helperStatus: HelperStatus? = null,
    val fcm: FcmStatus? = null,
    val fcmBusy: Boolean = false,
    val autostart: AutostartState = AutostartState.UNKNOWN,
    val batteryExempt: Boolean = false,
    val shizuku: String = "Đang kiểm tra…",
    val uid: Int? = null,
    val device: DeviceInfo? = null,
    val apps: List<InstalledApp> = emptyList(),
    val selected: Set<String> = emptySet(),
    val diagnosticScope: Set<String> = emptySet(),
    val plans: List<OperationPlan> = emptyList(),
    val results: List<OperationResult> = emptyList(),
    val history: List<OptimizationSession> = emptyList(),
    val diagnosticCompleted: Boolean = false,
    val operationInProgress: Boolean = false,
    val maintenanceCanWrite: Boolean = false,
    val maintenancePackages: Int = 0,
    val maintenanceStatus: String? = null,
    val optimizedPackages: Set<String> = emptySet(),
    val alreadyOptimalPackages: Set<String> = emptySet(),
    val message: String? = null
) {
    /** Restorable (backed up) plus already-optimal packages: what the user sees as "Đã tối ưu". */
    val allOptimized get() = optimizedPackages + alreadyOptimalPackages
}

class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutable
    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { mutable.update { it.copy(shizuku = "Shizuku đã dừng hoặc mất kết nối", uid = null) } }
    private val permission = Shizuku.OnRequestPermissionResultListener { _, result -> mutable.update { it.copy(shizuku = if (result == PackageManager.PERMISSION_GRANTED) "Đã cấp quyền" else "Đã từ chối quyền") }; refresh() }

    // Advanced options used to live only in UiState and reset to off whenever the process restarted.
    private val options = container.appContext.getSharedPreferences("ui_options", android.content.Context.MODE_PRIVATE)

    init {
        mutable.update { it.copy(advanced = options.getBoolean("advanced", false), experimental = options.getBoolean("experimental", false), xiaomiAutostartOps = options.getBoolean("xiaomiAutostartOps", false), networkWhitelist = options.getBoolean("networkWhitelist", false), aggressiveAppOps = options.getBoolean("aggressiveAppOps", false), aggressiveSystemProfile = options.getBoolean("aggressiveSystemProfile", false), greezerGmsLimit = options.getBoolean("greezerGmsLimit", false)) }
        Shizuku.addBinderReceivedListenerSticky(binderReceived); Shizuku.addBinderDeadListener(binderDead); Shizuku.addRequestPermissionResultListener(permission); refresh()
    }
    private fun updateOptions(change: (UiState) -> UiState) {
        val s = mutable.updateAndGet(change)
        options.edit().putBoolean("advanced", s.advanced).putBoolean("experimental", s.experimental).putBoolean("xiaomiAutostartOps", s.xiaomiAutostartOps).putBoolean("networkWhitelist", s.networkWhitelist).putBoolean("aggressiveAppOps", s.aggressiveAppOps).putBoolean("aggressiveSystemProfile", s.aggressiveSystemProfile).putBoolean("greezerGmsLimit", s.greezerGmsLimit).apply()
    }
    fun refresh() = viewModelScope.launch {
        val alive = container.executor.binderAlive(); val granted = container.executor.permissionGranted()
        val status = when { !alive -> "Chưa chạy — hãy khởi động Shizuku sau khi bật máy"; !granted -> "Cần cấp quyền"; else -> "Đã kết nối" }
        val uid = if (alive && granted) container.executor.effectiveUid() else null
        val device = runCatching { container.device.read() }.getOrNull()
        val maintained = container.maintenance.packages()
        // HyperOS may revoke the helper's Autostart again (e.g. after updates); heal it while Shizuku is up.
        if (alive && granted && container.maintenance.helper.isInstalled()) container.maintenance.helper.ensureAccess(container.executor)
        // Like GMS-Protect, re-run the cheap protection pass every time the app is opened.
        if (maintained.isNotEmpty()) withContext(Dispatchers.IO) { container.maintenance.reapply("app_open") }
        if (AssistantGuard.isEnabled(container.appContext)) { AssistantGuard.repair(container.appContext, "app_open"); AssistantGuard.watch(container.appContext) }
        val helper = container.maintenance.helper
        val helperStatus = withContext(Dispatchers.IO) { helper.status() }
        val history = container.sessions.list()
        if (!container.registry.initialized()) {
            val migrated = history.filter { session -> !session.restored && session.results.isNotEmpty() && session.results.all { it.status == ResultStatus.APPLIED_VERIFIED } }
                .flatMap { it.snapshots }.map { it.packageName }.toSet()
            container.registry.replace(migrated)
        }
        val optimized = container.registry.packages()
        mutable.update { it.copy(loading = false, shizuku = if (uid != null && uid != 2000) "$status (UID không mong đợi: $uid)" else status, uid = uid, device = device, apps = container.catalog.list(), selected = if (it.selected.isEmpty()) maintained - setOf("com.google.android.gms", "com.google.android.gsf") else it.selected, history = history, optimizedPackages = optimized, alreadyOptimalPackages = container.registry.alreadyOptimal(), maintenanceCanWrite = container.maintenance.canWrite(), maintenancePackages = maintained.size, maintenanceStatus = container.maintenance.lastResult(), helperInstalled = helper.isInstalled(), helperNeedsUpdate = helper.needsUpdate(), helperStatus = helperStatus, aurogonKeepAlive = AurogonKeepAlive.isEnabled(container.appContext), assistantGuard = AssistantGuard.status(container.appContext), assistantCandidates = AssistantGuard.candidates(container.appContext), autostart = SetupState.autostart(container.appContext), batteryExempt = SetupState.batteryExempt(container.appContext)) }
        if (state.value.fcm == null) checkFcm()
    }
    fun requestShizuku() = container.executor.requestPermission()
    fun select(pkg: String) = mutable.update {
        val selected = it.selected.toMutableSet().apply { if (!add(pkg)) remove(pkg) }
        val support = setOf("com.google.android.gms", "com.google.android.gsf").filter(container.catalog::isInstalled)
        container.maintenance.setPackages(selected + support)
        it.copy(selected = selected, maintenancePackages = (selected + support).size, diagnosticScope = emptySet(), plans = emptyList(), results = emptyList(), diagnosticCompleted = false)
    }
    /** Re-selects optimised apps that lost their selection, so the MILLET helper protects them again after reboots. */
    fun selectAll(packages: Set<String>) = mutable.update {
        val selected = it.selected + packages
        val support = setOf("com.google.android.gms", "com.google.android.gsf").filter(container.catalog::isInstalled)
        container.maintenance.setPackages(selected + support)
        it.copy(selected = selected, maintenancePackages = (selected + support).size, message = "Đã chọn lại ${packages.size} app; HyperNotify sẽ tự bảo vệ chúng sau khi khởi động lại")
    }
    /** Toolkit keep-alive preset: adds the installed apps of one group to the selection. */
    fun selectPreset(title: String, packages: List<String>) = mutable.update {
        val selected = it.selected + packages
        val support = setOf("com.google.android.gms", "com.google.android.gsf").filter(container.catalog::isInstalled)
        container.maintenance.setPackages(selected + support)
        it.copy(selected = selected, maintenancePackages = (selected + support).size, diagnosticScope = emptySet(), plans = emptyList(), results = emptyList(), diagnosticCompleted = false,
            message = "Đã chọn ${packages.size} app nhóm $title. Mở Xem trước để chẩn đoán và áp dụng")
    }
    fun setDryRun(value: Boolean) =mutable.update { it.copy(dryRun = value) }
    fun setAdvanced(value: Boolean) = updateOptions { it.copy(advanced = value, greezerGmsLimit = if (value) it.greezerGmsLimit else false, experimental = if (value) it.experimental else false, xiaomiAutostartOps = if (value) it.xiaomiAutostartOps else false, networkWhitelist = if (value) it.networkWhitelist else false, aggressiveAppOps = if (value) it.aggressiveAppOps else false, aggressiveSystemProfile = if (value) it.aggressiveSystemProfile else false) }
    fun setExperimental(value: Boolean) = updateOptions { it.copy(experimental = value && it.advanced) }
    fun setXiaomiAutostartOps(value: Boolean) = updateOptions { it.copy(xiaomiAutostartOps = value && it.advanced) }
    fun setNetworkWhitelist(value: Boolean) = updateOptions { it.copy(networkWhitelist = value && it.advanced) }
    fun setAggressiveAppOps(value: Boolean) = updateOptions { it.copy(aggressiveAppOps = value && it.advanced) }
    fun setAggressiveSystemProfile(value: Boolean) = updateOptions { it.copy(aggressiveSystemProfile = value && it.advanced) }
    fun setGreezerGmsLimit(value: Boolean) = updateOptions { it.copy(greezerGmsLimit = value && it.advanced) }
    fun clearMessage() = mutable.update { it.copy(message = null) }
    fun diagnose() = viewModelScope.launch {
        if (!container.executor.isAvailable() || state.value.uid != 2000) { mutable.update { it.copy(message = "Cần Shizuku với quyền shell UID 2000") }; return@launch }
        mutable.update { it.copy(operationInProgress = true, message = null) }
        val plans = mutableListOf<OperationPlan>()
        val supportPackages = setOf("com.google.android.gms", "com.google.android.gsf").filter(container.catalog::isInstalled)
        val scope = state.value.selected + supportPackages
        container.maintenance.setPackages(scope)
        val optionalIds = buildSet {
            if (state.value.xiaomiAutostartOps) addAll(setOf("appops_10053", "appops_10008"))
            if (state.value.networkWhitelist) add("network_background_whitelist")
            if (state.value.aggressiveAppOps) addAll(setOf("appops_wake_lock", "appops_start_foreground", "appops_schedule_exact_alarm"))
        }
        val safeIds = setOf("xiaomi_millet_no_restrict", "xiaomi_millet_white", "xiaomi_cloud_lowlatency", "doze_whitelist", "appops_run_any_in_background", "appops_run_in_background", "standby_bucket", "inactive_state")
        val selectedOperations = container.packageOperations.filter { it.id in safeIds || it.id in optionalIds }
        val readable = mutableSetOf<String>()
        scope.forEach { pkg -> selectedOperations.forEach { op ->
            if (op.probe(container.operationContext, pkg) is CapabilityStatus.Supported) {
                val snapshot = op.read(container.operationContext, pkg)
                if (snapshot.value is SnapshotValue.Present) {
                    readable += pkg
                    val desired = when (op.id) {
                        in setOf("xiaomi_millet_no_restrict", "xiaomi_millet_white", "xiaomi_cloud_lowlatency") -> "true"
                        "doze_whitelist" -> "true"
                        "standby_bucket" -> "10"
                        "inactive_state" -> "false"
                        "network_background_whitelist" -> "true"
                        else -> "allow"
                    }
                    val alreadyOptimal = when (op.id) {
                        in setOf("xiaomi_millet_no_restrict", "xiaomi_millet_white", "xiaomi_cloud_lowlatency") -> XiaomiWhitelist.parse(snapshot.value.raw)?.tokens?.contains(pkg) == true
                        // EXEMPTED (5) is more privileged than ACTIVE (10). Never downgrade it.
                        "standby_bucket" -> StandbyBucketParser.isOptimal(snapshot.value.raw)
                        else -> snapshot.value.raw == desired
                    }
                    if (!alreadyOptimal) plans += op.plan(snapshot, desired)
                }
            }
        } }
        if (state.value.aggressiveSystemProfile) container.systemOperations.forEach { op ->
            if (op.probe(container.operationContext, "__device__") is CapabilityStatus.Supported) {
                val snapshot = op.read(container.operationContext, "__device__")
                if (snapshot.value is SnapshotValue.Present) {
                    val desired = when (op.id) {
                        "global_cached_apps_freezer_off" -> "disabled"
                        "global_mobile_data_always_on" -> "1"
                        "global_wifi_never_sleep" -> "2"
                        else -> "0"
                    }
                    if (snapshot.value.raw != desired) plans += op.plan(snapshot, desired)
                }
            }
        }
        if (state.value.greezerGmsLimit) container.greezerOperation.let { op ->
            val snapshot = op.read(container.operationContext, "__device__")
            if (snapshot.value is SnapshotValue.Present && snapshot.value.raw != "false") plans += op.plan(snapshot, "false")
        }
        // Re-judge only the scanned packages: optimal now and not already tracked with a backup.
        val alreadyOptimal = readable - plans.map { it.packageName }.toSet() - container.registry.packages()
        container.registry.replaceAlreadyOptimal(container.registry.alreadyOptimal() - scope + alreadyOptimal)
        mutable.update { it.copy(diagnosticScope = scope, plans = plans, alreadyOptimalPackages = container.registry.alreadyOptimal(), diagnosticCompleted = true, operationInProgress = false, message = if (plans.isEmpty()) "Đã kiểm tra cả ứng dụng và dịch vụ Google: chưa thấy giới hạn hệ thống có thể sửa an toàn." else "Đã tìm thấy ${plans.size} giới hạn trong ${scope.size} package. Chưa có thay đổi nào được áp dụng.") }
    }
    fun applyConfirmed() = viewModelScope.launch {
        mutable.update { it.copy(operationInProgress = true, message = null) }
        if (state.value.dryRun) { mutable.update { it.copy(operationInProgress = false, results = it.plans.map { p -> OperationResult(p.operationId, ResultStatus.SKIPPED, "Chạy thử", packageName = p.packageName) }, message = "Chạy thử hoàn tất: hệ thống không bị thay đổi.") }; return@launch }
        val sessionId = UUID.randomUUID().toString(); val device = state.value.device ?: return@launch
        val transaction = OperationTransaction(container.operationContext, container.operations)
        val results = transaction.apply(state.value.plans) { snapshots -> container.sessions.save(OptimizationSession(sessionId = sessionId, createdAtEpochMs = System.currentTimeMillis(), deviceInfo = device, snapshots = snapshots, results = emptyList())) }
        container.sessions.save(OptimizationSession(sessionId = sessionId, createdAtEpochMs = System.currentTimeMillis(), deviceInfo = device, snapshots = state.value.plans.map { it.before }, results = results))
        if (results.isNotEmpty() && results.all { it.status == ResultStatus.APPLIED_VERIFIED }) container.registry.markOptimized(state.value.plans.map { it.packageName }.toSet())
        mutable.update { it.copy(operationInProgress = false, results = results, message = if (results.all { result -> result.status == ResultStatus.APPLIED_VERIFIED }) "Áp dụng và xác minh thành công." else "Đã hoàn tất nhưng có mục cần kiểm tra trong kết quả.") }; refresh()
    }
    fun restore(session: OptimizationSession) = viewModelScope.launch {
        mutable.update { it.copy(operationInProgress = true, message = null) }
        val results = session.snapshots.map { snap -> (container.operations[snap.operationId]?.rollback(container.operationContext, snap) ?: OperationResult(snap.operationId, ResultStatus.UNSUPPORTED, "Operation unavailable")).copy(packageName = snap.packageName) }
        if (results.all { it.status == ResultStatus.ROLLED_BACK }) container.registry.markRestored(session.snapshots.map { it.packageName }.toSet())
        mutable.update { it.copy(operationInProgress = false, results = results, message = "Khôi phục hoàn tất. Hãy kiểm tra trạng thái từng mục.") }
    }
    fun revokePackage(packageName: String) = viewModelScope.launch {
        if (!container.executor.isAvailable() || state.value.uid != 2000) { mutable.update { it.copy(message = "Cần Shizuku để hoàn tác tối ưu") }; return@launch }
        mutable.update { it.copy(operationInProgress = true, message = null) }
        val applicable = state.value.history
            .filter { !it.restored && it.results.isNotEmpty() && it.results.all { result -> result.status == ResultStatus.APPLIED_VERIFIED } }
            .sortedBy { it.createdAtEpochMs }
            .flatMap { it.snapshots }
            .filter { it.packageName == packageName }
            .distinctBy { it.operationId }
        if (applicable.isEmpty()) { mutable.update { it.copy(operationInProgress = false, message = "Không có bản sao lưu an toàn cho ứng dụng này") }; return@launch }
        val results = applicable.asReversed().map { snapshot -> (container.operations[snapshot.operationId]?.rollback(container.operationContext, snapshot) ?: OperationResult(snapshot.operationId, ResultStatus.UNSUPPORTED, "Thao tác không còn được hỗ trợ")).copy(packageName = snapshot.packageName) }
        val success = results.all { it.status == ResultStatus.ROLLED_BACK }
        if (success) {
            val support = setOf("com.google.android.gms", "com.google.android.gsf").filter(container.catalog::isInstalled)
            container.maintenance.setPackages(container.maintenance.packages() - packageName + support)
            container.registry.markRestored(setOf(packageName))
        }
        mutable.update { it.copy(operationInProgress = false, results = results, selected = if (success) it.selected - packageName else it.selected, optimizedPackages = container.registry.packages(), alreadyOptimalPackages = container.registry.alreadyOptimal(), message = if (success) "Đã hoàn tác tối ưu cho $packageName" else "Có mục không thể hoàn tác; xem phần Kết quả") }
    }
    /** "Khôi phục" in the optimised list: the exact backup when one exists, otherwise Android defaults. */
    fun restorePackage(packageName: String) {
        if (packageName in container.registry.packages()) { revokePackage(packageName); return }
        viewModelScope.launch {
            if (!container.executor.isAvailable() || state.value.uid != 2000) { mutable.update { it.copy(message = "Cần Shizuku để khôi phục") }; return@launch }
            mutable.update { it.copy(operationInProgress = true, message = null) }
            // Stop protecting it first, or the MILLET helper's watcher would add it straight back.
            container.maintenance.setPackages(container.maintenance.packages() - packageName)
            withContext(Dispatchers.IO) { container.maintenance.reapply("reset") }
            val millet = PackageReset.MILLET_KEYS.mapNotNull { key ->
                val result = container.executor.execute(PrivilegedCommand("reset.read_${key.lowercase()}", listOf("settings", "get", "system", key)))
                result.stdout.trim().takeIf { result.successful && it != "null" }?.let { key to it }
            }.toMap()
            val failed = PackageReset.commands(packageName, millet).filterNot { container.executor.execute(it).successful }.map { it.id.removePrefix("reset.") }
            container.registry.markRestored(setOf(packageName))
            mutable.update { it.copy(operationInProgress = false, selected = it.selected - packageName, maintenancePackages = container.maintenance.packages().size, optimizedPackages = container.registry.packages(), alreadyOptimalPackages = container.registry.alreadyOptimal(),
                message = if (failed.isEmpty()) "Đã đưa $packageName về mặc định Android" else "Đã đưa $packageName về mặc định; bỏ qua: ${failed.joinToString()}") }
        }
    }
    fun restoreAll() = viewModelScope.launch {
        if (!container.executor.isAvailable() || state.value.uid != 2000) { mutable.update { it.copy(message = "Cần Shizuku để khôi phục toàn bộ") }; return@launch }
        val targets = container.registry.packages()
        val snapshots = state.value.history
            .filter { !it.restored && it.results.isNotEmpty() && it.results.all { result -> result.status == ResultStatus.APPLIED_VERIFIED } }
            .sortedBy { it.createdAtEpochMs }
            .flatMap { it.snapshots }
            .filter { it.packageName in targets }
            .distinctBy { "${it.packageName}:${it.operationId}" }
        if (snapshots.isEmpty()) { mutable.update { it.copy(message = "Không có bản sao lưu để khôi phục") }; return@launch }
        mutable.update { it.copy(operationInProgress = true, message = null) }
        val results = snapshots.asReversed().map { snapshot -> (container.operations[snapshot.operationId]?.rollback(container.operationContext, snapshot) ?: OperationResult(snapshot.operationId, ResultStatus.UNSUPPORTED, "Thao tác không còn được hỗ trợ")).copy(packageName = snapshot.packageName) }
        val restored = results.filter { it.status == ResultStatus.ROLLED_BACK }.mapNotNull { it.packageName }.toSet()
        val failed = results.filterNot { it.status == ResultStatus.ROLLED_BACK }.mapNotNull { it.packageName }.toSet()
        val fullyRestored = restored - failed
        container.registry.markRestored(fullyRestored)
        container.maintenance.setPackages(container.maintenance.packages() - fullyRestored)
        mutable.update { it.copy(operationInProgress = false, results = results, optimizedPackages = container.registry.packages(), alreadyOptimalPackages = container.registry.alreadyOptimal(), message = if (failed.isEmpty()) "Đã khôi phục cài đặt gốc cho toàn bộ ứng dụng" else "Đã khôi phục một phần; ${failed.size} ứng dụng cần kiểm tra") }
    }
    fun requestMaintenancePermission() = container.settings.modifySystemSettings()
    fun reapplyMaintenance() = viewModelScope.launch {
        val success = withContext(Dispatchers.IO) { container.maintenance.reapply("manual") }
        val helper = container.maintenance.helper
        val helperStatus = withContext(Dispatchers.IO) { helper.status() }
        mutable.update { it.copy(helperInstalled = helper.isInstalled(), helperStatus = helperStatus, maintenanceCanWrite = container.maintenance.canWrite(), maintenancePackages = container.maintenance.packages().size, maintenanceStatus = container.maintenance.lastResult(), message = if (success) "Đã bật tự phục hồi whitelist sau khi khởi động lại" else container.maintenance.lastResult()) }
    }
    fun installHelper() = viewModelScope.launch {
        if (container.maintenance.packages().isEmpty()) { mutable.update { it.copy(message = "Hãy chọn ít nhất một ứng dụng trước khi cài helper") }; return@launch }
        mutable.update { it.copy(operationInProgress = true, message = null) }
        val error = container.maintenance.helper.install(container.executor)
        mutable.update { it.copy(operationInProgress = false, message = error ?: "Đã cài MILLET helper") }
        if (error == null) reapplyMaintenance()
    }
    fun uninstallHelper() = viewModelScope.launch {
        mutable.update { it.copy(operationInProgress = true, message = null) }
        val error = container.maintenance.helper.uninstall(container.executor)
        mutable.update { it.copy(operationInProgress = false, helperInstalled = container.maintenance.helper.isInstalled(), helperStatus = null, message = error ?: "Đã gỡ MILLET helper. Danh sách MILLET hiện tại được giữ nguyên.") }
    }
    fun checkFcm() = viewModelScope.launch {
        mutable.update { it.copy(fcmBusy = true) }
        val status = FcmProbe.status(container.appContext)
        mutable.update { it.copy(fcmBusy = false, fcm = status) }
    }
    fun reconnectFcm() = viewModelScope.launch {
        mutable.update { it.copy(fcmBusy = true) }
        val status = FcmProbe.reconnect(container.appContext)
        mutable.update { it.copy(fcmBusy = false, fcm = status, message = when (status.state) { FcmState.CONNECTED -> "FCM đang kết nối"; FcmState.DISCONNECTED -> "FCM vẫn mất kết nối; kiểm tra mạng hoặc thử lại sau"; FcmState.UNKNOWN -> "GMS không trả lời trạng thái FCM" }) }
    }
    fun setAurogonKeepAlive(value: Boolean) {
        val ok = AurogonKeepAlive.setEnabled(container.appContext, value)
        mutable.update { it.copy(aurogonKeepAlive = AurogonKeepAlive.isEnabled(container.appContext), message = if (ok) null else "Không khởi động được dịch vụ Aurogon") }
    }
    fun applyAssistant(target: AssistantCandidate) = viewModelScope.launch {
        val app = container.appContext
        if (!container.executor.isAvailable() || state.value.uid != 2000) {
            if (target.isXiaoAi || !AssistantGuard.isSetUp(app)) { mutable.update { it.copy(message = "Lần thiết lập đầu và khi đổi về Xiao AI cần Shizuku") }; return@launch }
            // Without Shizuku the role can only move through the system picker; the guard then keeps it after reboots.
            val popup = AssistantGuard.needsPopupPermission(app, target.packageName)
            val opened = container.settings.assistantPicker(target.packageName.takeIf { popup })
            if (opened && popup) AssistantGuard.markPopupHandled(app, target.packageName)
            mutable.update { it.copy(message = when {
                !opened -> "Không mở được cài đặt trợ lý của hệ thống; hãy bật Shizuku rồi thử lại"
                popup -> "Trong “Quyền khác”, bật “Hiển thị cửa sổ bật lên khi chạy nền”, rồi Back và chọn ${target.label} ở “Digital assistant app”"
                else -> "Chọn ${target.label} ở “Digital assistant app”"
            }) }
            return@launch
        }
        mutable.update { it.copy(operationInProgress = true, message = null) }
        val holders = AssistantSwitch.parseHolders(container.executor.execute(PrivilegedCommand("assistant.holders", listOf("cmd", "role", "get-role-holders", "android.app.role.ASSISTANT"))).stdout)
        val failed = AssistantSwitch.commands(target, holders, AssistantGuard.isInstalled(app, AssistantSwitch.XIAO_AI), app.packageName)
            .filterNot { container.executor.execute(it).successful }.map { it.id }
        // The guard is on from here: it re-applies the choice after every reboot.
        if (AssistantGuard.isEnabled(app)) { AssistantGuard.watch(app); AssistantGuard.repair(app, "switch") }
        val status = AssistantGuard.status(app)
        val switched = status.assistant == target.service && status.service == target.service && failed.none { it in AssistantSwitch.REQUIRED }
        if (switched && !target.isXiaoAi) AssistantGuard.markSetUp(app, target.packageName)
        val message = when {
            !switched -> "Chưa đổi được sang ${target.label} (lỗi: ${failed.joinToString { it.removePrefix("assistant.") }.ifBlank { "hệ thống không giữ giá trị" }})"
            failed.isNotEmpty() -> "Đã đặt ${target.label} cho nút nguồn; bỏ qua: ${failed.joinToString { it.removePrefix("assistant.") }}"
            else -> "Đã đặt ${target.label} cho nút nguồn"
        }
        mutable.update { it.copy(operationInProgress = false, assistantGuard = AssistantGuard.status(app), assistantCandidates = AssistantGuard.candidates(app), message = message) }
    }
    fun setAssistantGuard(enabled: Boolean) = viewModelScope.launch {
        if (!container.executor.isAvailable() || state.value.uid != 2000) { mutable.update { it.copy(message = "Cần Shizuku để cấp hoặc thu hồi quyền") }; return@launch }
        mutable.update { it.copy(operationInProgress = true, message = null) }
        val app = container.appContext
        val result = container.executor.execute(PrivilegedCommand("assistant_guard.${if (enabled) "grant" else "revoke"}", listOf("pm", if (enabled) "grant" else "revoke", app.packageName, "android.permission.WRITE_SECURE_SETTINGS")))
        val message = when {
            AssistantGuard.isEnabled(app) != enabled -> result.stderr.ifBlank { "HyperOS từ chối; bật \"Gỡ lỗi USB (Cài đặt bảo mật)\" rồi thử lại" }
            enabled -> { AssistantGuard.watch(app); if (AssistantGuard.repair(app, "manual")) "Đã bật giữ trợ lý cho nút nguồn" else AssistantGuard.status(app).lastResult }
            else -> { AssistantGuard.cancel(app); "Đã tắt giữ trợ lý" }
        }
        mutable.update { it.copy(operationInProgress = false, assistantGuard = AssistantGuard.status(app), message = message) }
    }
    fun requestBatteryExemption() =SetupState.requestBatteryExemption(container.appContext)
    fun openAutostart() = container.settings.xiaomiAutostart(container.appContext.packageName)
    fun refreshSetupState() = mutable.update { it.copy(autostart = SetupState.autostart(container.appContext), batteryExempt = SetupState.batteryExempt(container.appContext), maintenanceCanWrite = container.maintenance.canWrite(), assistantGuard = AssistantGuard.status(container.appContext), assistantCandidates = AssistantGuard.candidates(container.appContext)) }
    override fun onCleared() { Shizuku.removeBinderReceivedListener(binderReceived); Shizuku.removeBinderDeadListener(binderDead); Shizuku.removeRequestPermissionResultListener(permission) }
}
