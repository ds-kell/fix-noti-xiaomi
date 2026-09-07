package io.github.hypernotifyfix.core.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

class ShizukuExecutor(private val context: Context) : PrivilegedCommandExecutor {
    @Volatile private var service: IPrivilegedService? = null
    private val args = Shizuku.UserServiceArgs(ComponentName(context, PrivilegedService::class.java)).daemon(false).processNameSuffix("privileged").tag("hypernotify-shell").version(1)
    fun binderAlive() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    fun permissionGranted() = runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
    fun requestPermission(requestCode: Int = 41) { if (binderAlive()) Shizuku.requestPermission(requestCode) }
    private suspend fun connected(): IPrivilegedService = service ?: withTimeout(5_000) {
        suspendCancellableCoroutine { continuation ->
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { service = IPrivilegedService.Stub.asInterface(binder); if (continuation.isActive) continuation.resume(service!!) }
                override fun onServiceDisconnected(name: ComponentName?) { service = null }
            }
            Shizuku.bindUserService(args, connection)
            continuation.invokeOnCancellation { runCatching { Shizuku.unbindUserService(args, connection, false) } }
        }
    }
    override suspend fun isAvailable() = binderAlive() && permissionGranted() && runCatching { connected() }.isSuccess
    override suspend fun effectiveUid() = runCatching { connected().effectiveUid() }.getOrNull()
    override suspend fun execute(command: PrivilegedCommand): CommandResult = withContext(Dispatchers.IO) { Json.decodeFromString<CommandResult>(connected().execute(command.arguments.toTypedArray(), command.timeoutMs)).copy(commandId = command.id) }
}

class FakeCommandExecutor(private val handler: suspend (PrivilegedCommand) -> CommandResult = { CommandResult(it.id, 0, "", "", 1) }, private val available: Boolean = true) : PrivilegedCommandExecutor {
    override suspend fun isAvailable() = available
    override suspend fun effectiveUid() = if (available) 2000 else null
    override suspend fun execute(command: PrivilegedCommand) = handler(command)
}
