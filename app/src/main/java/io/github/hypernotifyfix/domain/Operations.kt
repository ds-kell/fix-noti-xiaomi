package io.github.hypernotifyfix.domain

import io.github.hypernotifyfix.core.model.*
import io.github.hypernotifyfix.core.shell.PrivilegedCommand
import io.github.hypernotifyfix.core.shell.PrivilegedCommandExecutor

interface PackageValidator { fun isInstalled(packageName: String): Boolean }
data class OperationContext(val executor: PrivilegedCommandExecutor, val packages: PackageValidator)

interface ReversibleOperation {
    val id: String
    val riskLevel: RiskLevel
    suspend fun probe(context: OperationContext, packageName: String): CapabilityStatus
    suspend fun read(context: OperationContext, packageName: String): OperationSnapshot
    fun plan(current: OperationSnapshot, desired: String): OperationPlan
    suspend fun apply(context: OperationContext, plan: OperationPlan): OperationResult
    suspend fun verify(context: OperationContext, plan: OperationPlan): Boolean
    suspend fun rollback(context: OperationContext, snapshot: OperationSnapshot): OperationResult
}

class XiaomiSystemWhitelistOperation(
    override val id: String,
    private val key: String,
) : ReversibleOperation {
    override val riskLevel = RiskLevel.MEDIUM
    private val namespace = "system"
    private fun readCommand() = PrivilegedCommand("$id.read", listOf("settings", "get", namespace, key))

    override suspend fun probe(context: OperationContext, packageName: String): CapabilityStatus =
        when (read(context, packageName).value) {
            is SnapshotValue.Present -> CapabilityStatus.Supported
            is SnapshotValue.Unsupported -> CapabilityStatus.Unsupported("Xiaomi background whitelist unavailable")
            else -> CapabilityStatus.Unknown("Cannot safely parse Xiaomi whitelist")
        }

    override suspend fun read(context: OperationContext, packageName: String): OperationSnapshot {
        if (!context.packages.isInstalled(packageName)) return OperationSnapshot(id, packageName, SnapshotValue.Unsupported("Package not installed"))
        val result = context.executor.execute(readCommand())
        if (!result.successful) return OperationSnapshot(id, packageName, SnapshotValue.Unsupported(result.stderr.ifBlank { "setting unavailable" }))
        val raw = result.stdout.trimEnd('\n', '\r')
        if (raw == "null") return OperationSnapshot(id, packageName, SnapshotValue.Unsupported("Xiaomi whitelist setting absent"))
        return OperationSnapshot(id, packageName, if (XiaomiWhitelist.parse(raw) != null) SnapshotValue.Present(raw) else SnapshotValue.Unreadable("Ambiguous Xiaomi whitelist format"))
    }

    override fun plan(current: OperationSnapshot, desired: String) = OperationPlan(id, current.packageName, current, desired, riskLevel)

    override suspend fun apply(context: OperationContext, plan: OperationPlan): OperationResult {
        if (plan.before.value !is SnapshotValue.Present || plan.desired != "true" || !context.packages.isInstalled(plan.packageName)) {
            return OperationResult(id, ResultStatus.FAILED_BEFORE_CHANGE, "Không có bản sao lưu hợp lệ")
        }
        // Re-read before every write so multiple plans merge instead of overwriting one another.
        val current = (read(context, plan.packageName).value as? SnapshotValue.Present)?.raw
            ?: return OperationResult(id, ResultStatus.FAILED_BEFORE_CHANGE, "Không đọc được whitelist Xiaomi hiện tại")
        val merged = XiaomiWhitelist.merge(current, plan.packageName)
            ?: return OperationResult(id, ResultStatus.FAILED_BEFORE_CHANGE, "Định dạng whitelist Xiaomi không an toàn")
        val result = context.executor.execute(PrivilegedCommand("$id.set", listOf("settings", "put", namespace, key, merged)))
        if (!result.successful) return OperationResult(id, ResultStatus.FAILED_BEFORE_CHANGE, result.stderr.ifBlank { "Áp dụng thất bại" })
        val verified = verify(context, plan)
        return OperationResult(id, if (verified) ResultStatus.APPLIED_VERIFIED else ResultStatus.VERIFICATION_FAILED,
            if (verified) "Đã xác minh whitelist chạy nền Xiaomi" else "Trạng thái đọc lại không khớp", verified)
    }

    override suspend fun verify(context: OperationContext, plan: OperationPlan): Boolean {
        val raw = (read(context, plan.packageName).value as? SnapshotValue.Present)?.raw ?: return false
        return XiaomiWhitelist.parse(raw)?.tokens?.contains(plan.packageName) == true
    }

    override suspend fun rollback(context: OperationContext, snapshot: OperationSnapshot): OperationResult {
        val original = (snapshot.value as? SnapshotValue.Present)?.raw
            ?: return OperationResult(id, ResultStatus.ROLLBACK_FAILED, "Không đọc được whitelist Xiaomi ban đầu")
        if (XiaomiWhitelist.parse(original) == null) return OperationResult(id, ResultStatus.ROLLBACK_FAILED, "Whitelist Xiaomi ban đầu không an toàn")
        val result = context.executor.execute(PrivilegedCommand("$id.restore", listOf("settings", "put", namespace, key, original)))
        val restored = result.successful && (read(context, snapshot.packageName).value as? SnapshotValue.Present)?.raw == original
        return OperationResult(id, if (restored) ResultStatus.ROLLED_BACK else ResultStatus.ROLLBACK_FAILED,
            if (restored) "Đã khôi phục chính xác whitelist Xiaomi" else "Khôi phục thất bại", restored)
    }
}

class DozeWhitelistOperation : ReversibleOperation {
    override val id = "doze_whitelist"
    override val riskLevel = RiskLevel.MEDIUM
    private fun command(suffix: String? = null) = PrivilegedCommand(id, listOf("cmd", "deviceidle", "whitelist") + listOfNotNull(suffix))
    override suspend fun probe(context: OperationContext, packageName: String) = if (!context.packages.isInstalled(packageName)) CapabilityStatus.Unsupported("Package not installed") else context.executor.execute(command()).let { if (it.successful) CapabilityStatus.Supported else CapabilityStatus.Unsupported("deviceidle whitelist unavailable") }
    override suspend fun read(context: OperationContext, packageName: String): OperationSnapshot {
        if (!context.packages.isInstalled(packageName)) return OperationSnapshot(id, packageName, SnapshotValue.Unsupported("Package not installed"))
        val result = context.executor.execute(command())
        if (!result.successful) return OperationSnapshot(id, packageName, SnapshotValue.Unreadable(result.stderr.ifBlank { "read failed" }))
        return OperationSnapshot(id, packageName, SnapshotValue.Present(DozeParser.parse(result.stdout).contains(packageName).toString()))
    }
    override fun plan(current: OperationSnapshot, desired: String) = OperationPlan(id, current.packageName, current, desired, riskLevel)
    override suspend fun apply(context: OperationContext, plan: OperationPlan): OperationResult {
        if (!context.packages.isInstalled(plan.packageName) || plan.before.value !is SnapshotValue.Present) return OperationResult(id, ResultStatus.FAILED_BEFORE_CHANGE, "Ứng dụng hoặc bản sao lưu không hợp lệ")
        val add = plan.desired.toBooleanStrictOrNull() ?: return OperationResult(id, ResultStatus.FAILED_BEFORE_CHANGE, "Trạng thái đích không hợp lệ")
        val result = context.executor.execute(command((if (add) "+" else "-") + plan.packageName))
        if (!result.successful) return OperationResult(id, ResultStatus.FAILED_BEFORE_CHANGE, result.stderr.ifBlank { "apply failed" })
        val verified = verify(context, plan)
        return OperationResult(id, if (verified) ResultStatus.APPLIED_VERIFIED else ResultStatus.VERIFICATION_FAILED, if (verified) "Đã áp dụng và xác minh" else "Trạng thái đọc lại không khớp", verified)
    }
    override suspend fun verify(context: OperationContext, plan: OperationPlan) = (read(context, plan.packageName).value as? SnapshotValue.Present)?.raw == plan.desired
    override suspend fun rollback(context: OperationContext, snapshot: OperationSnapshot): OperationResult {
        val original = (snapshot.value as? SnapshotValue.Present)?.raw?.toBooleanStrictOrNull() ?: return OperationResult(id, ResultStatus.ROLLBACK_FAILED, "Original state unreadable")
        val current = (read(context, snapshot.packageName).value as? SnapshotValue.Present)?.raw?.toBooleanStrictOrNull()
        if (current == original) return OperationResult(id, ResultStatus.ROLLED_BACK, "Đã ở trạng thái ban đầu", true)
        val result = context.executor.execute(command((if (original) "+" else "-") + snapshot.packageName))
        val restored = result.successful && (read(context, snapshot.packageName).value as? SnapshotValue.Present)?.raw == original.toString()
        return OperationResult(id, if (restored) ResultStatus.ROLLED_BACK else ResultStatus.ROLLBACK_FAILED, if (restored) "Đã khôi phục chính xác" else "Khôi phục thất bại", restored)
    }
}

class AppOpsOperation(private val op: String = "RUN_ANY_IN_BACKGROUND", override val riskLevel: RiskLevel = RiskLevel.MEDIUM) : ReversibleOperation {
    override val id = "appops_${op.lowercase()}"
    private val modes = setOf("allow", "ignore", "deny", "default", "foreground")
    private fun get(pkg: String) = PrivilegedCommand("$id.read", listOf("cmd", "appops", "get", pkg, op))
    override suspend fun probe(context: OperationContext, packageName: String): CapabilityStatus = when (read(context, packageName).value) { is SnapshotValue.Present -> CapabilityStatus.Supported; is SnapshotValue.Unsupported -> CapabilityStatus.Unsupported("AppOp unavailable"); else -> CapabilityStatus.Unknown("Cannot parse AppOps output") }
    override suspend fun read(context: OperationContext, packageName: String): OperationSnapshot {
        if (!context.packages.isInstalled(packageName)) return OperationSnapshot(id, packageName, SnapshotValue.Unsupported("Package not installed"))
        val result = context.executor.execute(get(packageName)); if (!result.successful) return OperationSnapshot(id, packageName, SnapshotValue.Unsupported(result.stderr.ifBlank { "op unavailable" }))
        val mode = AppOpsParser.parse(result.stdout, op)
        return OperationSnapshot(id, packageName, mode?.let { SnapshotValue.Present(it) } ?: SnapshotValue.Unreadable("Unrecognized AppOps output"))
    }
    override fun plan(current: OperationSnapshot, desired: String) = OperationPlan(id, current.packageName, current, desired, riskLevel)
    override suspend fun apply(context: OperationContext, plan: OperationPlan): OperationResult {
        if (plan.before.value !is SnapshotValue.Present || plan.desired !in modes || !context.packages.isInstalled(plan.packageName)) return OperationResult(id, ResultStatus.FAILED_BEFORE_CHANGE, "Không có bản sao lưu hợp lệ")
        val result = context.executor.execute(PrivilegedCommand("$id.set", listOf("cmd", "appops", "set", plan.packageName, op, plan.desired)))
        if (!result.successful) return OperationResult(id, ResultStatus.FAILED_BEFORE_CHANGE, result.stderr.ifBlank { "apply failed" })
        val verified = verify(context, plan); return OperationResult(id, if (verified) ResultStatus.APPLIED_VERIFIED else ResultStatus.VERIFICATION_FAILED, if (verified) "Đã áp dụng và xác minh" else "Trạng thái đọc lại không khớp", verified)
    }
    override suspend fun verify(context: OperationContext, plan: OperationPlan) = (read(context, plan.packageName).value as? SnapshotValue.Present)?.raw == plan.desired
    override suspend fun rollback(context: OperationContext, snapshot: OperationSnapshot): OperationResult {
        val mode = (snapshot.value as? SnapshotValue.Present)?.raw?.takeIf { it in modes } ?: return OperationResult(id, ResultStatus.ROLLBACK_FAILED, "Original mode unreadable")
        val result = context.executor.execute(PrivilegedCommand("$id.restore", listOf("cmd", "appops", "set", snapshot.packageName, op, mode)))
        val restored = result.successful && (read(context, snapshot.packageName).value as? SnapshotValue.Present)?.raw == mode
        return OperationResult(id, if (restored) ResultStatus.ROLLED_BACK else ResultStatus.ROLLBACK_FAILED, if (restored) "Đã khôi phục chính xác chế độ" else "Khôi phục thất bại", restored)
    }
}

class StandbyBucketOperation : ReversibleOperation {
    override val id = "standby_bucket"
    override val riskLevel = RiskLevel.MEDIUM
    private fun get(pkg: String) = PrivilegedCommand("$id.read", listOf("am", "get-standby-bucket", pkg))

    override suspend fun probe(context: OperationContext, packageName: String): CapabilityStatus =
        when (read(context, packageName).value) {
            is SnapshotValue.Present -> CapabilityStatus.Supported
            is SnapshotValue.Unsupported -> CapabilityStatus.Unsupported("Standby bucket unavailable")
            else -> CapabilityStatus.Unknown("Cannot read standby bucket")
        }

    override suspend fun read(context: OperationContext, packageName: String): OperationSnapshot {
        if (!context.packages.isInstalled(packageName)) return OperationSnapshot(id, packageName, SnapshotValue.Unsupported("Package not installed"))
        val result = context.executor.execute(get(packageName))
        if (!result.successful) return OperationSnapshot(id, packageName, SnapshotValue.Unsupported(result.stderr.ifBlank { "read failed" }))
        return OperationSnapshot(id, packageName, StandbyBucketParser.parse(result.stdout)?.let { SnapshotValue.Present(it) }
            ?: SnapshotValue.Unreadable("Unrecognized standby bucket output"))
    }

    override fun plan(current: OperationSnapshot, desired: String) = OperationPlan(id, current.packageName, current, desired, riskLevel)

    override suspend fun apply(context: OperationContext, plan: OperationPlan): OperationResult {
        if (plan.before.value !is SnapshotValue.Present || plan.desired != "10") return OperationResult(id, ResultStatus.FAILED_BEFORE_CHANGE, "Không có bản sao lưu hợp lệ")
        if (plan.before.value.raw == "5") return OperationResult(id, ResultStatus.APPLIED_VERIFIED, "Đã được miễn trừ; giữ nguyên mức ưu tiên cao hơn", true)
        val result = context.executor.execute(PrivilegedCommand("$id.set", listOf("am", "set-standby-bucket", plan.packageName, "active")))
        if (!result.successful) return OperationResult(id, ResultStatus.FAILED_BEFORE_CHANGE, result.stderr.ifBlank { "apply failed" })
        val verified = verify(context, plan)
        return OperationResult(id, if (verified) ResultStatus.APPLIED_VERIFIED else ResultStatus.VERIFICATION_FAILED, if (verified) "Đã xác minh mức ưu tiên hoạt động" else "Trạng thái đọc lại không khớp", verified)
    }

    override suspend fun verify(context: OperationContext, plan: OperationPlan) =
        (read(context, plan.packageName).value as? SnapshotValue.Present)?.raw?.let(StandbyBucketParser::isOptimal) == true

    override suspend fun rollback(context: OperationContext, snapshot: OperationSnapshot): OperationResult {
        val original = (snapshot.value as? SnapshotValue.Present)?.raw?.takeIf { it in setOf("5", "10", "20", "30", "40", "45", "50") }
            ?: return OperationResult(id, ResultStatus.ROLLBACK_FAILED, "Original bucket unreadable")
        val result = context.executor.execute(PrivilegedCommand("$id.restore", listOf("am", "set-standby-bucket", snapshot.packageName, original)))
        val restored = result.successful && (read(context, snapshot.packageName).value as? SnapshotValue.Present)?.raw == original
        return OperationResult(id, if (restored) ResultStatus.ROLLED_BACK else ResultStatus.ROLLBACK_FAILED, if (restored) "Đã khôi phục chính xác mức ưu tiên" else "Khôi phục thất bại", restored)
    }
}

class InactiveOperation : ReversibleOperation {
    override val id = "inactive_state"
    override val riskLevel = RiskLevel.MEDIUM
    private fun get(pkg: String) = PrivilegedCommand("$id.read", listOf("am", "get-inactive", pkg))

    override suspend fun probe(context: OperationContext, packageName: String): CapabilityStatus =
        if (read(context, packageName).value is SnapshotValue.Present) CapabilityStatus.Supported else CapabilityStatus.Unsupported("Inactive state unavailable")

    override suspend fun read(context: OperationContext, packageName: String): OperationSnapshot {
        if (!context.packages.isInstalled(packageName)) return OperationSnapshot(id, packageName, SnapshotValue.Unsupported("Package not installed"))
        val result = context.executor.execute(get(packageName))
        if (!result.successful) return OperationSnapshot(id, packageName, SnapshotValue.Unsupported(result.stderr.ifBlank { "read failed" }))
        return OperationSnapshot(id, packageName, InactiveParser.parse(result.stdout)?.let { SnapshotValue.Present(it.toString()) }
            ?: SnapshotValue.Unreadable("Unrecognized inactive output"))
    }

    override fun plan(current: OperationSnapshot, desired: String) = OperationPlan(id, current.packageName, current, desired, riskLevel)

    override suspend fun apply(context: OperationContext, plan: OperationPlan): OperationResult {
        if (plan.before.value !is SnapshotValue.Present || plan.desired != "false") return OperationResult(id, ResultStatus.FAILED_BEFORE_CHANGE, "Không có bản sao lưu hợp lệ")
        val result = context.executor.execute(PrivilegedCommand("$id.set", listOf("am", "set-inactive", plan.packageName, "false")))
        if (!result.successful) return OperationResult(id, ResultStatus.FAILED_BEFORE_CHANGE, result.stderr.ifBlank { "apply failed" })
        val verified = verify(context, plan)
        return OperationResult(id, if (verified) ResultStatus.APPLIED_VERIFIED else ResultStatus.VERIFICATION_FAILED, if (verified) "Đã xóa trạng thái không hoạt động" else "Trạng thái đọc lại không khớp", verified)
    }

    override suspend fun verify(context: OperationContext, plan: OperationPlan) =
        (read(context, plan.packageName).value as? SnapshotValue.Present)?.raw == plan.desired

    override suspend fun rollback(context: OperationContext, snapshot: OperationSnapshot): OperationResult {
        val original = (snapshot.value as? SnapshotValue.Present)?.raw?.toBooleanStrictOrNull()
            ?: return OperationResult(id, ResultStatus.ROLLBACK_FAILED, "Original state unreadable")
        val result = context.executor.execute(PrivilegedCommand("$id.restore", listOf("am", "set-inactive", snapshot.packageName, original.toString())))
        val restored = result.successful && (read(context, snapshot.packageName).value as? SnapshotValue.Present)?.raw == original.toString()
        return OperationResult(id, if (restored) ResultStatus.ROLLED_BACK else ResultStatus.ROLLBACK_FAILED, if (restored) "Đã khôi phục chính xác trạng thái hoạt động" else "Khôi phục thất bại", restored)
    }
}

class NetworkPolicyWhitelistOperation : ReversibleOperation {
    override val id = "network_background_whitelist"
    override val riskLevel = RiskLevel.MEDIUM

    private suspend fun uid(context: OperationContext, packageName: String): String? {
        val result = context.executor.execute(PrivilegedCommand("$id.uid", listOf("cmd", "package", "list", "packages", "-U", packageName)))
        return if (result.successful) Regex("\\buid:(\\d+)\\b").find(result.stdout)?.groupValues?.get(1) else null
    }

    private suspend fun listed(context: OperationContext, packageName: String): Boolean? {
        val appUid = uid(context, packageName) ?: return null
        val result = context.executor.execute(PrivilegedCommand("$id.read", listOf("cmd", "netpolicy", "list", "restrict-background-whitelist")))
        if (!result.successful) return null
        return Regex("\\b${Regex.escape(appUid)}\\b").containsMatchIn(result.stdout)
    }

    override suspend fun probe(context: OperationContext, packageName: String) =
        if (!context.packages.isInstalled(packageName)) CapabilityStatus.Unsupported("Package not installed")
        else if (listed(context, packageName) != null) CapabilityStatus.Supported else CapabilityStatus.Unsupported("Network policy whitelist unavailable")

    override suspend fun read(context: OperationContext, packageName: String): OperationSnapshot {
        if (!context.packages.isInstalled(packageName)) return OperationSnapshot(id, packageName, SnapshotValue.Unsupported("Package not installed"))
        val value = listed(context, packageName) ?: return OperationSnapshot(id, packageName, SnapshotValue.Unreadable("Không đọc được whitelist dữ liệu nền"))
        return OperationSnapshot(id, packageName, SnapshotValue.Present(value.toString()))
    }

    override fun plan(current: OperationSnapshot, desired: String) = OperationPlan(id, current.packageName, current, desired, riskLevel)

    private suspend fun write(context: OperationContext, packageName: String, enabled: Boolean): Boolean {
        val appUid = uid(context, packageName) ?: return false
        val action = if (enabled) "add" else "remove"
        return context.executor.execute(PrivilegedCommand("$id.$action", listOf("cmd", "netpolicy", action, "restrict-background-whitelist", appUid))).successful
    }

    override suspend fun apply(context: OperationContext, plan: OperationPlan): OperationResult {
        val desired = plan.desired.toBooleanStrictOrNull()
            ?: return OperationResult(id, ResultStatus.FAILED_BEFORE_CHANGE, "Trạng thái đích không hợp lệ")
        if (plan.before.value !is SnapshotValue.Present || !write(context, plan.packageName, desired)) return OperationResult(id, ResultStatus.FAILED_BEFORE_CHANGE, "Không thể đổi whitelist dữ liệu nền")
        val verified = verify(context, plan)
        return OperationResult(id, if (verified) ResultStatus.APPLIED_VERIFIED else ResultStatus.VERIFICATION_FAILED, if (verified) "Đã xác minh dữ liệu nền" else "UID hoặc trạng thái đọc lại không khớp", verified)
    }

    override suspend fun verify(context: OperationContext, plan: OperationPlan) = listed(context, plan.packageName)?.toString() == plan.desired

    override suspend fun rollback(context: OperationContext, snapshot: OperationSnapshot): OperationResult {
        val original = (snapshot.value as? SnapshotValue.Present)?.raw?.toBooleanStrictOrNull()
            ?: return OperationResult(id, ResultStatus.ROLLBACK_FAILED, "Không đọc được trạng thái dữ liệu nền ban đầu")
        val restored = write(context, snapshot.packageName, original) && listed(context, snapshot.packageName) == original
        return OperationResult(id, if (restored) ResultStatus.ROLLED_BACK else ResultStatus.ROLLBACK_FAILED, if (restored) "Đã khôi phục whitelist dữ liệu nền" else "Khôi phục whitelist dữ liệu nền thất bại", restored)
    }
}

class GlobalSettingOperation(
    override val id: String,
    private val key: String,
    private val target: String,
) : ReversibleOperation {
    override val riskLevel = RiskLevel.EXPERIMENTAL
    private val deviceTarget = "__device__"
    private fun get() = PrivilegedCommand("$id.read", listOf("settings", "get", "global", key))

    override suspend fun probe(context: OperationContext, packageName: String) =
        if (context.executor.execute(get()).successful) CapabilityStatus.Supported else CapabilityStatus.Unsupported("Global setting unavailable")

    override suspend fun read(context: OperationContext, packageName: String): OperationSnapshot {
        val result = context.executor.execute(get())
        return OperationSnapshot(id, deviceTarget, if (result.successful) SnapshotValue.Present(result.stdout.trim()) else SnapshotValue.Unreadable(result.stderr.ifBlank { "Không đọc được $key" }))
    }

    override fun plan(current: OperationSnapshot, desired: String) = OperationPlan(id, deviceTarget, current, desired, riskLevel)

    override suspend fun apply(context: OperationContext, plan: OperationPlan): OperationResult {
        if (plan.before.value !is SnapshotValue.Present || plan.desired != target) return OperationResult(id, ResultStatus.FAILED_BEFORE_CHANGE, "Không có bản sao lưu global hợp lệ")
        val result = context.executor.execute(PrivilegedCommand("$id.set", listOf("settings", "put", "global", key, target)))
        if (!result.successful) return OperationResult(id, ResultStatus.FAILED_BEFORE_CHANGE, result.stderr.ifBlank { "Không thể thay đổi $key" })
        val verified = verify(context, plan)
        return OperationResult(id, if (verified) ResultStatus.APPLIED_VERIFIED else ResultStatus.VERIFICATION_FAILED, if (verified) "Đã xác minh thiết lập toàn hệ thống" else "Trạng thái global đọc lại không khớp", verified)
    }

    override suspend fun verify(context: OperationContext, plan: OperationPlan) = (read(context, deviceTarget).value as? SnapshotValue.Present)?.raw == target

    override suspend fun rollback(context: OperationContext, snapshot: OperationSnapshot): OperationResult {
        val original = (snapshot.value as? SnapshotValue.Present)?.raw
            ?: return OperationResult(id, ResultStatus.ROLLBACK_FAILED, "Không có giá trị global ban đầu")
        val args = if (original == "null" || original.isBlank()) listOf("settings", "delete", "global", key) else listOf("settings", "put", "global", key, original)
        val result = context.executor.execute(PrivilegedCommand("$id.restore", args))
        val now = (read(context, deviceTarget).value as? SnapshotValue.Present)?.raw
        val restored = result.successful && if (original == "null" || original.isBlank()) now == "null" else now == original
        return OperationResult(id, if (restored) ResultStatus.ROLLED_BACK else ResultStatus.ROLLBACK_FAILED, if (restored) "Đã khôi phục thiết lập toàn hệ thống" else "Khôi phục $key thất bại", restored)
    }
}

object DozeParser {
    private val packagePattern = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
    fun parse(output: String): Set<String> = output.lineSequence().mapNotNull { raw ->
        val parts = raw.trim().split(',').map(String::trim)
        when {
            parts.size >= 2 && parts[0] in setOf("system", "user") && parts[1].matches(packagePattern) -> parts[1]
            parts.size == 1 && parts[0].matches(packagePattern) -> parts[0]
            else -> null // system-excidle is not a full Doze exemption.
        }
    }.toSet()
}
object AppOpsParser {
    fun parse(output: String, op: String): String? {
        val label = if (op.all(Char::isDigit)) "(?:MIUIOP\\()?${Regex.escape(op)}\\)?" else Regex.escape(op)
        return Regex("(?im)^\\s*$label(?:\\s*\\([^)]*\\))?\\s*:\\s*(allow|ignore|deny|default|foreground)\\b")
            .find(output)?.groupValues?.get(1)?.lowercase()
    }
}
object StandbyBucketParser {
    fun parse(output: String): String? = Regex("(?m)(?:^|:)\\s*(5|10|20|30|40|45|50)\\s*$").find(output.trim())?.groupValues?.get(1)
    fun isOptimal(value: String): Boolean = value == "5" || value == "10"
}
object InactiveParser {
    fun parse(output: String): Boolean? = Regex("(?i)\\b(?:idle|inactive)\\s*=\\s*(true|false)\\b").find(output)?.groupValues?.get(1)?.lowercase()?.toBooleanStrictOrNull()
}

class OperationTransaction(private val context: OperationContext, private val operations: Map<String, ReversibleOperation>) {
    suspend fun apply(plans: List<OperationPlan>, backup: suspend (List<OperationSnapshot>) -> Boolean): List<OperationResult> {
        if (!backup(plans.map { it.before })) return plans.map { OperationResult(it.operationId, ResultStatus.FAILED_BEFORE_CHANGE, "Sao lưu thất bại", packageName = it.packageName) }
        val results = mutableListOf<OperationResult>(); val applied = mutableListOf<OperationPlan>()
        for (plan in plans) {
            val operation = operations[plan.operationId]
            if (operation == null) { results += OperationResult(plan.operationId, ResultStatus.UNSUPPORTED, "Thao tác không xác định", packageName = plan.packageName); continue }
            val result = operation.apply(context, plan).copy(packageName = plan.packageName); results += result
            if (result.status == ResultStatus.APPLIED_VERIFIED) applied += plan else {
                if (result.status == ResultStatus.VERIFICATION_FAILED) results += operation.rollback(context, plan.before).copy(packageName = plan.packageName)
                applied.asReversed().forEach { done -> results += operations.getValue(done.operationId).rollback(context, done.before).copy(packageName = done.packageName) }
                break
            }
        }
        return results
    }
}
