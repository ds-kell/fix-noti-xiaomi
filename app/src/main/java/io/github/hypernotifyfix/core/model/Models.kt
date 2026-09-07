package io.github.hypernotifyfix.core.model

import kotlinx.serialization.Serializable

@Serializable enum class RiskLevel { LOW, MEDIUM, EXPERIMENTAL }

@Serializable sealed interface CapabilityStatus {
    @Serializable data object Supported : CapabilityStatus
    @Serializable data class Unsupported(val reason: String) : CapabilityStatus
    @Serializable data class Unknown(val reason: String) : CapabilityStatus
    @Serializable data class Failed(val error: DiagnosticError) : CapabilityStatus
}

@Serializable data class DiagnosticError(val code: String, val message: String)

@Serializable sealed interface SnapshotValue {
    @Serializable data class Present(val raw: String) : SnapshotValue
    @Serializable data object Absent : SnapshotValue
    @Serializable data class Unreadable(val reason: String) : SnapshotValue
    @Serializable data class Unsupported(val reason: String) : SnapshotValue
}

@Serializable data class OperationSnapshot(val operationId: String, val packageName: String, val value: SnapshotValue)
@Serializable data class OperationPlan(val operationId: String, val packageName: String, val before: OperationSnapshot, val desired: String, val risk: RiskLevel)
@Serializable data class OperationResult(val operationId: String, val status: ResultStatus, val message: String, val verified: Boolean = false, val packageName: String? = null)
@Serializable enum class ResultStatus { APPLIED_VERIFIED, VERIFICATION_FAILED, FAILED_BEFORE_CHANGE, ROLLED_BACK, ROLLBACK_FAILED, UNSUPPORTED, SKIPPED }
@Serializable data class DeviceInfo(val manufacturer: String, val brand: String, val model: String, val device: String, val sdk: Int, val fingerprintHash: String, val securityPatch: String, val hyperOsVersion: String?, val romRegion: String?)
@Serializable data class InstalledApp(val label: String, val packageName: String, val version: String, val system: Boolean, val enabled: Boolean)
@Serializable data class OptimizationSession(val schemaVersion: Int = 1, val sessionId: String, val createdAtEpochMs: Long, val deviceInfo: DeviceInfo, val snapshots: List<OperationSnapshot>, val results: List<OperationResult>, val restored: Boolean = false)
