package io.github.hypernotifyfix.domain

/**
 * Decides how to bring an OEM Settings.System package list back to a state that contains every
 * required package without dropping anything the user or ROM already had.
 *
 * HyperOS can briefly expose an empty (or missing) value while PowerKeeper rebuilds its policy
 * database. Writing `required` on top of that would silently discard every other entry, so an
 * empty/missing value is rebuilt from the last list we observed as healthy.
 */
object WhitelistRepair {
    sealed interface Decision {
        /** Every required package is already present; nothing to write. */
        data class Unchanged(val healthy: String) : Decision
        data class Write(val value: String, val restoredFromLastGood: Boolean) : Decision
        /** Key absent and never seen before: OEM does not use it, never create it. */
        data object Absent : Decision
        data class Refuse(val reason: String) : Decision
    }

    fun plan(current: String?, lastGood: String?, required: Collection<String>): Decision {
        val cached = lastGood?.takeIf { it.isNotBlank() && XiaomiWhitelist.parse(it) != null }
        if (current == null && cached == null) return Decision.Absent
        val transient = current.isNullOrBlank() && cached != null
        val base = if (transient) cached!! else current.orEmpty()
        var merged = base
        for (pkg in required.sorted()) merged = XiaomiWhitelist.merge(merged, pkg) ?: return Decision.Refuse("Định dạng whitelist không an toàn")
        return if (!transient && merged == current) Decision.Unchanged(merged) else Decision.Write(merged, transient)
    }

    fun containsAll(raw: String?, required: Collection<String>): Boolean {
        val tokens = XiaomiWhitelist.parse(raw ?: return false)?.tokens ?: return false
        return tokens.containsAll(required)
    }
}
