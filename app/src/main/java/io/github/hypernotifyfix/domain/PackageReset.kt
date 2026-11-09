package io.github.hypernotifyfix.domain

import io.github.hypernotifyfix.core.shell.PrivilegedCommand

/**
 * Puts one app back to Android/HyperOS defaults when no backup of its original state exists (it was
 * optimised by an earlier install). Standby bucket and inactive state are left to the system, which
 * recomputes them from usage.
 */
object PackageReset {
    val MILLET_KEYS = listOf("MILLET_NO_RESTRICT_APP", "millet_white", "cloud_lowlatency_whitelist")

    /** [milletLists] maps each present MILLET key to its current raw value. */
    fun commands(packageName: String, milletLists: Map<String, String>): List<PrivilegedCommand> = buildList {
        add(PrivilegedCommand("reset.doze", listOf("cmd", "deviceidle", "whitelist", "-$packageName")))
        listOf("RUN_ANY_IN_BACKGROUND", "RUN_IN_BACKGROUND").forEach { op ->
            add(PrivilegedCommand("reset.appops_${op.lowercase()}", listOf("cmd", "appops", "set", packageName, op, "default")))
        }
        milletLists.forEach { (key, raw) ->
            XiaomiWhitelist.remove(raw, packageName)?.takeIf { it != raw }?.let { add(PrivilegedCommand("reset.millet_${key.lowercase()}", listOf("settings", "put", "system", key, it))) }
        }
    }
}
