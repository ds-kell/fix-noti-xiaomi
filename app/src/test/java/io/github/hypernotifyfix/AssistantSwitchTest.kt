package io.github.hypernotifyfix

import io.github.hypernotifyfix.domain.AssistantCandidate
import io.github.hypernotifyfix.domain.AssistantSwitch
import org.junit.Assert.*
import org.junit.Test

class AssistantSwitchTest {
    private val gemini = AssistantCandidate("com.google.android.googlequicksearchbox", "Gemini (Google)", "com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService", "com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService")
    private val claude = AssistantCandidate("com.anthropic.claude", "Claude", "com.anthropic.claude/com.anthropic.claude.Assist", null)
    private val xiaoAi = AssistantCandidate(AssistantSwitch.XIAO_AI, "Xiao AI", "com.miui.voiceassist/com.xiaomi.voiceassistant.AssistInteractionService", null)

    private fun ids(commands: List<io.github.hypernotifyfix.core.shell.PrivilegedCommand>) = commands.map { it.id.removePrefix("assistant.") }

    @Test fun `switching away from Xiao AI removes it before touching the role`() {
        val steps = ids(AssistantSwitch.commands(claude, setOf(gemini.packageName), xiaoAiInstalled = true, selfPackage = "io.github.hypernotifyfix"))
        assertTrue(steps.indexOf("remove_xiaoai") < steps.indexOf("role_remove"))
        assertTrue(steps.indexOf("role_remove") < steps.indexOf("role_add"))
        assertFalse("restore_xiaoai" in steps)
        assertTrue(AssistantSwitch.REQUIRED.all { it.removePrefix("assistant.") in steps })
    }

    @Test fun `power key opens the chosen assistant and the guard is granted`() {
        val commands = AssistantSwitch.commands(gemini, emptySet(), xiaoAiInstalled = false, selfPackage = "io.github.hypernotifyfix")
        assertEquals(listOf("settings", "put", "system", "long_press_power_key", "launch_google_search"), commands.single { it.id == "assistant.power_key" }.arguments)
        assertEquals(listOf("settings", "put", "secure", "voice_recognition_service", gemini.recognizer), commands.single { it.id == "assistant.set_recognizer" }.arguments)
        assertEquals(listOf("pm", "grant", "io.github.hypernotifyfix", "android.permission.WRITE_SECURE_SETTINGS"), commands.last().arguments)
        assertFalse(commands.any { it.id == "assistant.remove_xiaoai" })
    }

    @Test fun `current holder is not removed and assistant without recognizer skips it`() {
        val steps = ids(AssistantSwitch.commands(claude, setOf(claude.packageName), xiaoAiInstalled = false, selfPackage = "x.y"))
        assertFalse("role_remove" in steps)
        assertFalse("set_recognizer" in steps)
    }

    @Test fun `choosing Xiao AI restores it and the Xiaomi power key action`() {
        val commands = AssistantSwitch.commands(xiaoAi, setOf(gemini.packageName), xiaoAiInstalled = false, selfPackage = "x.y")
        assertEquals("assistant.restore_xiaoai", commands.first().id)
        assertEquals("launch_voice_assistant", commands.single { it.id == "assistant.power_key" }.arguments.last())
        assertEquals("0", commands.single { it.id == "assistant.power_key_aosp" }.arguments.last())
    }

    @Test fun `role holders parse from separators and ignore noise`() {
        assertEquals(setOf("com.a.b", "com.c.d"), AssistantSwitch.parseHolders("com.a.b;com.c.d\n"))
        assertEquals(emptySet<String>(), AssistantSwitch.parseHolders("\n"))
    }
}
