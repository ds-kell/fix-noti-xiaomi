package io.github.hypernotifyfix.core

import android.content.Context
import io.github.hypernotifyfix.core.shell.ShizukuExecutor
import io.github.hypernotifyfix.core.model.RiskLevel
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
    val packageOperations = listOf(
        XiaomiSystemWhitelistOperation("xiaomi_millet_no_restrict", "MILLET_NO_RESTRICT_APP"),
        XiaomiSystemWhitelistOperation("xiaomi_millet_white", "millet_white"),
        XiaomiSystemWhitelistOperation("xiaomi_cloud_lowlatency", "cloud_lowlatency_whitelist"),
        DozeWhitelistOperation(),
        AppOpsOperation("RUN_ANY_IN_BACKGROUND"),
        AppOpsOperation("RUN_IN_BACKGROUND"),
        AppOpsOperation("10053", RiskLevel.EXPERIMENTAL),
        AppOpsOperation("10008", RiskLevel.EXPERIMENTAL),
        AppOpsOperation("WAKE_LOCK", RiskLevel.EXPERIMENTAL),
        AppOpsOperation("START_FOREGROUND", RiskLevel.EXPERIMENTAL),
        AppOpsOperation("SCHEDULE_EXACT_ALARM", RiskLevel.EXPERIMENTAL),
        NetworkPolicyWhitelistOperation(),
        StandbyBucketOperation(),
        InactiveOperation()
    )
    val systemOperations = listOf(
        GlobalSettingOperation("global_app_standby_off", "app_standby_enabled", "0"),
        GlobalSettingOperation("global_cached_apps_freezer_off", "cached_apps_freezer", "disabled"),
        GlobalSettingOperation("global_mobile_data_always_on", "mobile_data_always_on", "1"),
        GlobalSettingOperation("global_wifi_never_sleep", "wifi_sleep_policy", "2"),
    )
    val operations = (packageOperations + systemOperations).associateBy { it.id }
    val operationContext = OperationContext(executor, catalog)
}
