package io.github.hypernotifyfix.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhitelistRepairTest {
    private val gms = listOf("com.google.android.gms")

    @Test fun `absent key without history is never created`() {
        assertEquals(WhitelistRepair.Decision.Absent, WhitelistRepair.plan(null, null, gms))
    }

    @Test fun `present package needs no write`() {
        assertEquals(WhitelistRepair.Decision.Unchanged("com.a.app,com.google.android.gms"), WhitelistRepair.plan("com.a.app,com.google.android.gms", null, gms))
    }

    @Test fun `missing package is merged preserving others`() {
        assertEquals(WhitelistRepair.Decision.Write("com.a.app,com.google.android.gms", false), WhitelistRepair.plan("com.a.app", null, gms))
    }

    @Test fun `transient empty value is rebuilt from last good list`() {
        assertEquals(WhitelistRepair.Decision.Write("com.a.app,com.b.app,com.google.android.gms", true), WhitelistRepair.plan("", "com.a.app,com.b.app", gms))
        assertEquals(WhitelistRepair.Decision.Write("com.a.app,com.google.android.gms", true), WhitelistRepair.plan(null, "com.a.app,com.google.android.gms", gms))
    }

    @Test fun `empty value without history starts a new list`() {
        assertEquals(WhitelistRepair.Decision.Write("com.google.android.gms", false), WhitelistRepair.plan("", null, gms))
    }

    @Test fun `ambiguous format is refused`() {
        assertTrue(WhitelistRepair.plan("com.a.app,com.b.app;com.c.app", null, gms) is WhitelistRepair.Decision.Refuse)
    }

    @Test fun `remove drops only the package and keeps the delimiter`() {
        assertEquals("com.a.app,com.b.app", XiaomiWhitelist.remove("com.a.app, com.google.android.gms,com.b.app", "com.google.android.gms"))
        assertEquals("com.a.app;com.b.app", XiaomiWhitelist.remove("com.a.app;com.b.app", "com.x.app"))
        assertEquals("", XiaomiWhitelist.remove("com.google.android.gms", "com.google.android.gms"))
        assertEquals(null, XiaomiWhitelist.remove("com.a.app,com.b.app;com.c.app", "com.a.app"))
    }

    @Test fun `containsAll checks parsed tokens`() {
        assertTrue(WhitelistRepair.containsAll("com.a.app;com.google.android.gms", gms))
        assertEquals(false, WhitelistRepair.containsAll("com.google.android.gmsx", gms))
    }
}
