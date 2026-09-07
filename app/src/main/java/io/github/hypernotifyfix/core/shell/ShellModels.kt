package io.github.hypernotifyfix.core.shell

import kotlinx.serialization.Serializable

@Serializable data class PrivilegedCommand(val id: String, val arguments: List<String>, val timeoutMs: Long = 8_000) {
    init {
        require(id.matches(Regex("[a-z0-9_.-]+")))
        require(arguments.isNotEmpty())
        require(timeoutMs in 100..30_000)
        require(arguments.none { it.contains('\u0000') })
    }
}
@Serializable data class CommandResult(val commandId: String, val exitCode: Int, val stdout: String, val stderr: String, val durationMs: Long, val timedOut: Boolean = false) { val successful get() = exitCode == 0 && !timedOut }
interface PrivilegedCommandExecutor {
    suspend fun isAvailable(): Boolean
    suspend fun effectiveUid(): Int?
    suspend fun execute(command: PrivilegedCommand): CommandResult
}
