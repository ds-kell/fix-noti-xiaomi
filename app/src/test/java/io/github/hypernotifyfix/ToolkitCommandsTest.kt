package io.github.hypernotifyfix

import io.github.hypernotifyfix.toolkit.DebloatCatalog
import io.github.hypernotifyfix.toolkit.KeepAlivePresets
import io.github.hypernotifyfix.toolkit.Safety
import io.github.hypernotifyfix.toolkit.ToolkitCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolkitCommandsTest {
    @Test fun removeKeepsDataAndOnlyTargetsUserZero() =
        assertEquals(listOf("pm", "uninstall", "-k", "--user", "0", "com.miui.msa.global"), ToolkitCommands.remove("com.miui.msa.global").arguments)

    @Test fun restoreUsesInstallExisting() =
        assertEquals(listOf("cmd", "package", "install-existing", "com.miui.msa.global"), ToolkitCommands.restore("com.miui.msa.global").arguments)

    @Test(expected = IllegalArgumentException::class) fun rejectsShellText() { ToolkitCommands.remove("com.x; reboot") }
    @Test(expected = IllegalArgumentException::class) fun rejectsOtherRoles() { ToolkitCommands.roleHolders("android.app.role.SMS") }

    @Test fun setRoleRemovesOtherHoldersThenAdds() {
        val args = ToolkitCommands.setRole(ToolkitCommands.ROLE_BROWSER, "com.android.chrome", setOf("com.mi.globalbrowser", "com.android.chrome")).map { it.arguments }
        assertEquals(listOf(
            listOf("cmd", "role", "remove-role-holder", ToolkitCommands.ROLE_BROWSER, "com.mi.globalbrowser"),
            listOf("cmd", "role", "add-role-holder", ToolkitCommands.ROLE_BROWSER, "com.android.chrome"),
        ), args)
    }

    @Test fun bankSafeTurnsOffWirelessDebuggingFirst() {
        val script = ToolkitCommands.bankSafe().arguments.last()
        assertTrue(script.indexOf("adb_wifi_enabled") < script.indexOf("development_settings_enabled"))
    }

    @Test fun catalogHasNoDuplicatesAndNoCorePackages() {
        val names = DebloatCatalog.entries.map { it.packageName }
        assertEquals(names.size, names.toSet().size)
        val core = setOf("com.miui.home", "com.android.systemui", "com.android.settings", "com.miui.securitycenter", "com.android.phone",
            "com.google.android.gms", "com.miui.voiceassist", "com.android.contacts", "com.android.mms", "com.miui.powerkeeper", "com.xiaomi.xmsf")
        assertTrue(names.none { it in core })
        assertTrue(DebloatCatalog.entries.filter { it.safety == Safety.CAUTION }.all { it.note != null && it.note.vi.isNotBlank() && it.note.en.isNotBlank() })
    }

    @Test fun presetsHaveValidPackageNames() =
        assertTrue(KeepAlivePresets.all.flatMap { it.packages }.all { it.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) })
}
