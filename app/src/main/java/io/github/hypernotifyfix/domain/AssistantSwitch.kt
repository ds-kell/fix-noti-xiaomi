package io.github.hypernotifyfix.domain

import io.github.hypernotifyfix.core.shell.PrivilegedCommand

/** An app that can be the system assistant, i.e. it declares a VoiceInteractionService. */
data class AssistantCandidate(val packageName: String, val label: String, val service: String, val recognizer: String?) {
    val isXiaoAi get() = packageName == AssistantSwitch.XIAO_AI
}

/**
 * Shell steps behind "Áp dụng" in the assistant picker: the community Gemini .bat generalised to any
 * assistant, plus what HyperOS 3 CN needs on top (see decision 009).
 */
object AssistantSwitch {
    const val XIAO_AI = "com.miui.voiceassist"
    private const val ROLE = "android.app.role.ASSISTANT"

    /** Steps whose failure means the switch did not happen; the rest are best effort. */
    val REQUIRED = setOf("assistant.role_add", "assistant.set_service", "assistant.power_key")

    fun commands(target: AssistantCandidate, currentHolders: Set<String>, xiaoAiInstalled: Boolean, selfPackage: String): List<PrivilegedCommand> = buildList {
        fun cmd(step: String, vararg args: String) = add(PrivilegedCommand("assistant.$step", args.toList()))
        if (target.isXiaoAi) cmd("restore_xiaoai", "cmd", "package", "install-existing", XIAO_AI)
        // Before the role change: removing Xiao AI afterwards made the system clear `assistant` as well.
        else if (xiaoAiInstalled) cmd("remove_xiaoai", "pm", "uninstall", "-k", "--user", "0", XIAO_AI)
        cmd("grant_mic", "pm", "grant", target.packageName, "android.permission.RECORD_AUDIO")
        (currentHolders - target.packageName).forEach { cmd("role_remove", "cmd", "role", "remove-role-holder", ROLE, it) }
        cmd("role_add", "cmd", "role", "add-role-holder", ROLE, target.packageName)
        cmd("set_service", "settings", "put", "secure", "voice_interaction_service", target.service)
        cmd("set_assistant", "settings", "put", "secure", "assistant", target.service)
        target.recognizer?.let { cmd("set_recognizer", "settings", "put", "secure", "voice_recognition_service", it) }
        // HyperOS ignores the AOSP key: launch_google_search opens whichever assistant is set, launch_voice_assistant opens Xiao AI.
        cmd("power_key", "settings", "put", "system", "long_press_power_key", if (target.isXiaoAi) "launch_voice_assistant" else "launch_google_search")
        cmd("power_key_aosp", "settings", "put", "global", "power_button_long_press", if (target.isXiaoAi) "0" else "5")
        cmd("grant_guard", "pm", "grant", selfPackage, "android.permission.WRITE_SECURE_SETTINGS")
    }

    /** `cmd role get-role-holders` prints holders separated by `;` or whitespace. */
    fun parseHolders(output: String): Set<String> =
        output.split(';', ' ', '\n', '\r', '\t').map(String::trim).filter { it.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) }.toSet()
}
