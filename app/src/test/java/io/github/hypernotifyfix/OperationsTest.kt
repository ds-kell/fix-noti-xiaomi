package io.github.hypernotifyfix

import io.github.hypernotifyfix.core.model.*
import io.github.hypernotifyfix.core.shell.*
import io.github.hypernotifyfix.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OperationsTest {
    @Test fun `standby bucket parser accepts plain and package output`() {
        assertEquals("10", StandbyBucketParser.parse("10\n"))
        assertEquals("40", StandbyBucketParser.parse("com.discord: 40\n"))
        assertTrue(StandbyBucketParser.isOptimal("5"))
        assertTrue(StandbyBucketParser.isOptimal("10"))
        assertFalse(StandbyBucketParser.isOptimal("40"))
    }

    @Test fun `inactive parser reads activity manager output`() {
        assertEquals(false, InactiveParser.parse("Idle=false\n"))
        assertEquals(true, InactiveParser.parse("Inactive=true\n"))
    }
    private val installed = object : PackageValidator { override fun isInstalled(packageName: String) = packageName == "com.example.chat" }

    @Test fun packageValidationRejectsProcessName() { assertFalse(installed.isInstalled("com.example.chat.persistent")); assertTrue(installed.isInstalled("com.example.chat")) }
    @Test fun commandUsesArgumentListAndRejectsNul() { assertEquals(listOf("cmd", "x", "a b"), PrivilegedCommand("safe", listOf("cmd", "x", "a b")).arguments); assertThrows(IllegalArgumentException::class.java) { PrivilegedCommand("safe", listOf("cmd", "bad\u0000arg")) } }
    @Test fun timeoutIsRepresented() { assertFalse(CommandResult("x", 124, "", "", 100, true).successful) }
    @Test fun parsesDozeFormats() { assertEquals(setOf("com.a.app", "com.b.app", "com.c.app"), DozeParser.parse("system,com.a.app,10001\nuser,com.b.app,10234\ncom.c.app\nsystem-excidle,com.not.full,1000\n")) }
    @Test fun parsesAppOpsFormats() { assertEquals("ignore", AppOpsParser.parse("RUN_ANY_IN_BACKGROUND: ignore; time=+1h", "RUN_ANY_IN_BACKGROUND")); assertEquals("foreground", AppOpsParser.parse("  RUN_ANY_IN_BACKGROUND (default): foreground", "RUN_ANY_IN_BACKGROUND")) }
    @Test fun whitelistMergePreservesAndDeduplicates() { assertEquals("com.a.app; com.b.app;com.c.app  ", XiaomiWhitelist.merge("com.a.app; com.b.app  ", "com.c.app")); assertEquals("com.a.app,com.b.app", XiaomiWhitelist.merge("com.a.app,com.b.app", "com.b.app")) }
    @Test fun whitelistMergePreservesXiaomiTrailingDelimiter() { assertEquals("com.a.app;com.b.app;", XiaomiWhitelist.merge("com.a.app;", "com.b.app")); assertEquals("com.a.app:com.b.app:com.c.app", XiaomiWhitelist.merge("com.a.app:com.b.app", "com.c.app")) }
    @Test fun whitelistRejectsAmbiguousFormat() { assertNull(XiaomiWhitelist.merge("com.a.app,com.b.app;com.c.app", "com.d.app")); assertNull(XiaomiWhitelist.merge("unknown token", "com.d.app")) }

    @Test fun xiaomiOperationMergesLatestListAndRestoresExactBackup() = runBlocking {
        var setting = "com.existing.app"
        val fake = FakeCommandExecutor(handler = { command -> when {
            command.arguments.take(4) == listOf("settings", "get", "system", "MILLET_NO_RESTRICT_APP") ->
                CommandResult(command.id, 0, setting + "\n", "", 1)
            command.arguments.take(4) == listOf("settings", "put", "system", "MILLET_NO_RESTRICT_APP") -> {
                setting = command.arguments[4]
                CommandResult(command.id, 0, "", "", 1)
            }
            else -> CommandResult(command.id, 1, "", "unexpected", 1)
        } })
        val op = XiaomiSystemWhitelistOperation("xiaomi_millet_no_restrict", "MILLET_NO_RESTRICT_APP")
        val context = OperationContext(fake, installed)
        val before = op.read(context, "com.example.chat")
        val applied = op.apply(context, op.plan(before, "true"))
        assertEquals(ResultStatus.APPLIED_VERIFIED, applied.status)
        assertEquals("com.existing.app,com.example.chat", setting)
        val restored = op.rollback(context, before)
        assertEquals(ResultStatus.ROLLED_BACK, restored.status)
        assertEquals("com.existing.app", setting)
    }

    @Test fun xiaomiOperationRefusesAmbiguousExistingValue() = runBlocking {
        val fake = FakeCommandExecutor(handler = { command -> CommandResult(command.id, 0, "com.a.app,com.b.app;com.c.app\n", "", 1) })
        val snapshot = XiaomiSystemWhitelistOperation("xiaomi_millet_no_restrict", "MILLET_NO_RESTRICT_APP").read(OperationContext(fake, installed), "com.example.chat")
        assertTrue(snapshot.value is SnapshotValue.Unreadable)
    }
    @Test fun snapshotDistinguishesAbsentEmptyUnreadable() { assertNotEquals(SnapshotValue.Absent, SnapshotValue.Present("")); assertNotEquals(SnapshotValue.Absent, SnapshotValue.Unreadable("denied")) }

    @Test fun dozeDoesNotRemovePreexistingWhitelistOnRollback() = runBlocking {
        var whitelisted = true; val fake = FakeCommandExecutor(handler = { command -> when { command.arguments.lastOrNull() == "-com.example.chat" -> { whitelisted = false; CommandResult(command.id, 0, "", "", 1) }; else -> CommandResult(command.id, 0, if (whitelisted) "user,com.example.chat" else "", "", 1) } })
        val op = DozeWhitelistOperation(); val snap = op.read(OperationContext(fake, installed), "com.example.chat"); val result = op.rollback(OperationContext(fake, installed), snap)
        assertTrue(whitelisted); assertEquals(ResultStatus.ROLLED_BACK, result.status)
    }

    @Test fun noApplyWhenBackupFails() = runBlocking {
        var invoked = false; val fake = FakeCommandExecutor(handler = { c -> invoked = true; CommandResult(c.id, 0, "", "", 1) }); val op = AppOpsOperation(); val snap = OperationSnapshot(op.id, "com.example.chat", SnapshotValue.Present("ignore")); val results = OperationTransaction(OperationContext(fake, installed), mapOf(op.id to op)).apply(listOf(op.plan(snap, "allow"))) { false }
        assertFalse(invoked); assertEquals(ResultStatus.FAILED_BEFORE_CHANGE, results.single().status)
    }

    @Test fun partialFailureTriggersAutomaticRollback() = runBlocking {
        var mode = "ignore"; var failDoze = false
        val fake = FakeCommandExecutor(handler = { c -> when {
            c.arguments.takeLast(2) == listOf("RUN_ANY_IN_BACKGROUND", "allow") -> { mode = "allow"; CommandResult(c.id, 0, "", "", 1) }
            c.arguments.takeLast(2) == listOf("RUN_ANY_IN_BACKGROUND", "ignore") -> { mode = "ignore"; CommandResult(c.id, 0, "", "", 1) }
            c.arguments.contains("appops") && c.arguments.contains("get") -> CommandResult(c.id, 0, "RUN_ANY_IN_BACKGROUND: $mode", "", 1)
            c.arguments.lastOrNull() == "+com.example.chat" -> { failDoze = true; CommandResult(c.id, 1, "", "denied", 1) }
            else -> CommandResult(c.id, 0, "", "", 1)
        } })
        val appOps = AppOpsOperation(); val doze = DozeWhitelistOperation(); val context = OperationContext(fake, installed)
        val plans = listOf(appOps.plan(OperationSnapshot(appOps.id, "com.example.chat", SnapshotValue.Present("ignore")), "allow"), doze.plan(OperationSnapshot(doze.id, "com.example.chat", SnapshotValue.Present("false")), "true"))
        val results = OperationTransaction(context, mapOf(appOps.id to appOps, doze.id to doze)).apply(plans) { true }
        assertTrue(failDoze); assertEquals("ignore", mode); assertTrue(results.any { it.status == ResultStatus.ROLLED_BACK })
    }

    @Test fun verificationFailureRollsBackTheOperationThatMayHaveChanged() = runBlocking {
        var whitelisted = false
        var readsAfterApply = 0
        val fake = FakeCommandExecutor(handler = { command -> when {
            command.arguments.lastOrNull() == "+com.example.chat" -> { whitelisted = true; CommandResult(command.id, 0, "Added", "", 1) }
            command.arguments.lastOrNull() == "-com.example.chat" -> { whitelisted = false; CommandResult(command.id, 0, "Removed", "", 1) }
            else -> {
                if (whitelisted) readsAfterApply++
                val output = if (whitelisted && readsAfterApply > 1) "user,com.example.chat,10123" else ""
                CommandResult(command.id, 0, output, "", 1)
            }
        } })
        val op = DozeWhitelistOperation()
        val before = OperationSnapshot(op.id, "com.example.chat", SnapshotValue.Present("false"))
        val results = OperationTransaction(OperationContext(fake, installed), mapOf(op.id to op)).apply(listOf(op.plan(before, "true"))) { true }
        assertFalse(whitelisted)
        assertTrue(results.any { it.status == ResultStatus.VERIFICATION_FAILED })
        assertTrue(results.any { it.status == ResultStatus.ROLLED_BACK })
    }
}
