package io.github.hypernotifyfix.domain
import io.github.hypernotifyfix.core.t

/**
 * `assistant` holds the user's choice and survives reboot; `voice_interaction_service` is what the
 * power-key long press actually binds. Restore the latter from the former, but only once the chosen
 * service can be bound (after first unlock for a non-system assistant).
 */
object AssistantRepair {
    sealed interface Decision {
        data object Healthy : Decision
        data class Skip(val reason: String) : Decision
        data class Write(val value: String) : Decision
    }

    fun plan(current: String?, assistant: String?, available: Set<String>, unlocked: Boolean): Decision {
        val target = assistant?.trim().orEmpty()
        if (target.isEmpty()) return Decision.Skip(t("Chưa chọn trợ lý mặc định", "No default assistant chosen"))
        if (current?.trim() == target) return Decision.Healthy
        if (!unlocked) return Decision.Skip(t("Máy chưa mở khóa", "Phone is not unlocked yet"))
        if (target !in available) return Decision.Skip(t("Trợ lý đã chọn không còn VoiceInteractionService", "The chosen assistant no longer has a VoiceInteractionService"))
        return Decision.Write(target)
    }
}
