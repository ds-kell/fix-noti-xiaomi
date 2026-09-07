package io.github.hypernotifyfix

import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hypernotifyfix.core.AppContainer
import io.github.hypernotifyfix.core.model.*
import io.github.hypernotifyfix.domain.OperationTransaction
import io.github.hypernotifyfix.domain.StandbyBucketParser
import io.github.hypernotifyfix.domain.XiaomiWhitelist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.util.UUID

data class UiState(
    val loading: Boolean = true,
    val dryRun: Boolean = true,
    val advanced: Boolean = false,
    val experimental: Boolean = false,
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
    val message: String? = null
)

class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutable
    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { mutable.update { it.copy(shizuku = "Shizuku đã dừng hoặc mất kết nối", uid = null) } }
    private val permission = Shizuku.OnRequestPermissionResultListener { _, result -> mutable.update { it.copy(shizuku = if (result == PackageManager.PERMISSION_GRANTED) "Đã cấp quyền" else "Đã từ chối quyền") }; refresh() }

    init { Shizuku.addBinderReceivedListenerSticky(binderReceived); Shizuku.addBinderDeadListener(binderDead); Shizuku.addRequestPermissionResultListener(permission); refresh() }
    fun refresh() = viewModelScope.launch {
        val alive = container.executor.binderAlive(); val granted = container.executor.permissionGranted()
        val status = when { !alive -> "Chưa chạy — hãy khởi động Shizuku sau khi bật máy"; !granted -> "Cần cấp quyền"; else -> "Đã kết nối" }
        val uid = if (alive && granted) container.executor.effectiveUid() else null
        val device = runCatching { container.device.read() }.getOrNull()
        val maintained = container.maintenance.packages()
        val history = container.sessions.list()
        if (!container.registry.initialized()) {
            val migrated = history.filter { session -> !session.restored && session.results.isNotEmpty() && session.results.all { it.status == ResultStatus.APPLIED_VERIFIED } }
                .flatMap { it.snapshots }.map { it.packageName }.toSet()
            container.registry.replace(migrated)
        }
        val optimized = container.registry.packages()
        mutable.update { it.copy(loading = false, shizuku = if (uid != null && uid != 2000) "$status (UID không mong đợi: $uid)" else status, uid = uid, device = device, apps = container.catalog.list(), selected = if (it.selected.isEmpty()) maintained - setOf("com.google.android.gms", "com.google.android.gsf") else it.selected, history = history, optimizedPackages = optimized, maintenanceCanWrite = container.maintenance.canWrite(), maintenancePackages = maintained.size, maintenanceStatus = container.maintenance.lastResult()) }
    }
    fun requestShizuku() = container.executor.requestPermission()
    fun select(pkg: String) = mutable.update {
        val selected = it.selected.toMutableSet().apply { if (!add(pkg)) remove(pkg) }
        val support = setOf("com.google.android.gms", "com.google.android.gsf").filter(container.catalog::isInstalled)
        container.maintenance.setPackages(selected + support)
        it.copy(selected = selected, maintenancePackages = (selected + support).size, diagnosticScope = emptySet(), plans = emptyList(), results = emptyList(), diagnosticCompleted = false)
    }
    fun setDryRun(value: Boolean) = mutable.update { it.copy(dryRun = value) }
    fun setAdvanced(value: Boolean) = mutable.update { it.copy(advanced = value, experimental = if (value) it.experimental else false) }
    fun setExperimental(value: Boolean) = mutable.update { it.copy(experimental = value && it.advanced) }
    fun clearMessage() = mutable.update { it.copy(message = null) }
    fun diagnose() = viewModelScope.launch {
        if (!container.executor.isAvailable() || state.value.uid != 2000) { mutable.update { it.copy(message = "Cần Shizuku với quyền shell UID 2000") }; return@launch }
        mutable.update { it.copy(operationInProgress = true, message = null) }
        val plans = mutableListOf<OperationPlan>()
        val supportPackages = setOf("com.google.android.gms", "com.google.android.gsf").filter(container.catalog::isInstalled)
        val scope = state.value.selected + supportPackages
        container.maintenance.setPackages(scope)
        scope.forEach { pkg -> container.operations.values.forEach { op ->
            if (op.probe(container.operationContext, pkg) is CapabilityStatus.Supported) {
                val snapshot = op.read(container.operationContext, pkg)
                if (snapshot.value is SnapshotValue.Present) {
                    val desired = when (op.id) {
                        in setOf("xiaomi_millet_no_restrict", "xiaomi_millet_white", "xiaomi_cloud_lowlatency") -> "true"
                        "doze_whitelist" -> "true"
                        "standby_bucket" -> "10"
                        "inactive_state" -> "false"
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
        mutable.update { it.copy(diagnosticScope = scope, plans = plans, diagnosticCompleted = true, operationInProgress = false, message = if (plans.isEmpty()) "Đã kiểm tra cả ứng dụng và dịch vụ Google: chưa thấy giới hạn hệ thống có thể sửa an toàn." else "Đã tìm thấy ${plans.size} giới hạn trong ${scope.size} package. Chưa có thay đổi nào được áp dụng.") }
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
        mutable.update { it.copy(operationInProgress = false, results = results, optimizedPackages = if (success) it.optimizedPackages - packageName else it.optimizedPackages, message = if (success) "Đã hoàn tác tối ưu cho $packageName" else "Có mục không thể hoàn tác; xem phần Kết quả") }
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
        mutable.update { it.copy(operationInProgress = false, results = results, optimizedPackages = container.registry.packages(), message = if (failed.isEmpty()) "Đã khôi phục cài đặt gốc cho toàn bộ ứng dụng" else "Đã khôi phục một phần; ${failed.size} ứng dụng cần kiểm tra") }
    }
    fun requestMaintenancePermission() = container.settings.modifySystemSettings()
    fun reapplyMaintenance() = viewModelScope.launch {
        val success = container.maintenance.reapply()
        mutable.update { it.copy(maintenanceCanWrite = container.maintenance.canWrite(), maintenancePackages = container.maintenance.packages().size, maintenanceStatus = container.maintenance.lastResult(), message = if (success) "Đã bật tự phục hồi whitelist sau khi khởi động lại" else container.maintenance.lastResult()) }
    }
    override fun onCleared() { Shizuku.removeBinderReceivedListener(binderReceived); Shizuku.removeBinderDeadListener(binderDead); Shizuku.removeRequestPermissionResultListener(permission) }
}
