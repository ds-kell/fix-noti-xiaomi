package io.github.hypernotifyfix

import io.github.hypernotifyfix.domain.AssistantRepair
import io.github.hypernotifyfix.domain.AssistantRepair.Decision
import org.junit.Assert.*
import org.junit.Test

class AssistantRepairTest {
    private val gsa = "com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService"
    private val xiaoai = "com.miui.voiceassist/com.xiaomi.voiceassistant.AssistInteractionService"
    private val both = setOf(gsa, xiaoai)

    @Test fun `matching service needs no write`() {
        assertEquals(Decision.Healthy, AssistantRepair.plan(gsa, gsa, both, unlocked = true))
    }

    @Test fun `wiped service is restored from chosen assistant`() {
        assertEquals(Decision.Write(gsa), AssistantRepair.plan(null, gsa, both, unlocked = true))
        assertEquals(Decision.Write(gsa), AssistantRepair.plan("", gsa, both, unlocked = true))
    }

    @Test fun `boot fallback to Xiao AI is replaced by chosen assistant`() {
        assertEquals(Decision.Write(gsa), AssistantRepair.plan(xiaoai, gsa, both, unlocked = true))
    }

    @Test fun `nothing is written before unlock`() {
        assertTrue(AssistantRepair.plan(null, gsa, both, unlocked = false) is Decision.Skip)
    }

    @Test fun `nothing is written without a chosen assistant`() {
        assertTrue(AssistantRepair.plan(null, null, both, unlocked = true) is Decision.Skip)
        assertTrue(AssistantRepair.plan(null, " ", both, unlocked = true) is Decision.Skip)
    }

    @Test fun `uninstalled assistant is not written`() {
        assertTrue(AssistantRepair.plan(null, gsa, setOf(xiaoai), unlocked = true) is Decision.Skip)
    }
}
