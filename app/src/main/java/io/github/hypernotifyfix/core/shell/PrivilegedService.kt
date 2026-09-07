package io.github.hypernotifyfix.core.shell

import android.content.Context
import android.os.Process
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class PrivilegedService() : IPrivilegedService.Stub() {
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()
    override fun effectiveUid(): Int = Process.myUid()
    override fun execute(arguments: Array<out String>, timeoutMs: Long): String {
        val started = System.nanoTime()
        if (arguments.isEmpty() || arguments.any { it.indexOf('\u0000') >= 0 } || timeoutMs !in 100..30_000) return Json.encodeToString(CommandResult("remote", 126, "", "invalid arguments", 0))
        val process = ProcessBuilder(arguments.toList()).start()
        var stdout = ""; var stderr = ""
        val outThread = thread { stdout = process.inputStream.bufferedReader().use { it.readText().take(262_144) } }
        val errThread = thread { stderr = process.errorStream.bufferedReader().use { it.readText().take(262_144) } }
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        outThread.join(1_000); errThread.join(1_000)
        return Json.encodeToString(CommandResult("remote", if (finished) process.exitValue() else 124, stdout.trimEnd(), stderr.trimEnd(), (System.nanoTime() - started) / 1_000_000, !finished))
    }
    override fun destroy() { System.exit(0) }
}
