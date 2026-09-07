package io.github.hypernotifyfix.core

import android.content.Context
import io.github.hypernotifyfix.core.shell.ShizukuExecutor
import io.github.hypernotifyfix.data.*
import io.github.hypernotifyfix.domain.*

class AppContainer(context: Context) {
    val executor = ShizukuExecutor(context)
    val catalog = AppCatalog(context)
    val device = DeviceRepository(executor)
    val publicDiagnostics = PublicDiagnosticRepository(context)
    val sessions = SessionStore(context)
    val registry = OptimizationRegistry(context)
    val reports = ReportExporter(context)
    val settings = SettingsNavigator(context)
    val maintenance = BackgroundMaintenanceStore(context)
    val operations = listOf(
        XiaomiSystemWhitelistOperation("xiaomi_millet_no_restrict", "MILLET_NO_RESTRICT_APP"),
        XiaomiSystemWhitelistOperation("xiaomi_millet_white", "millet_white"),
        XiaomiSystemWhitelistOperation("xiaomi_cloud_lowlatency", "cloud_lowlatency_whitelist"),
        DozeWhitelistOperation(),
        AppOpsOperation("RUN_ANY_IN_BACKGROUND"),
        AppOpsOperation("RUN_IN_BACKGROUND"),
        StandbyBucketOperation(),
        InactiveOperation()
    ).associateBy { it.id }
    val operationContext = OperationContext(executor, catalog)
}
