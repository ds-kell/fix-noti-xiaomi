package io.github.hypernotifyfix.core.system

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Messenger
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

enum class FcmState { CONNECTED, DISCONNECTED, UNKNOWN }
data class FcmStatus(val state: FcmState, val rawStatus: String?, val detail: String? = null)

/**
 * Asks GMS for gcm.googleapis.com/local/status over the legacy GCM SEND channel (same probe as
 * FCM Auto/GMS-Protect). Status "3" means the MCS connection is down; anything else is not
 * treated as a failure, and no answer is UNKNOWN rather than DISCONNECTED.
 */
object FcmProbe {
    private const val ACTION_SEND = "com.google.android.gcm.intent.SEND"
    private const val GTALK_PERMISSION = "com.google.android.gtalkservice.permission.GTALK_SERVICE"
    private const val ACTION_HEARTBEAT_NOW = "com.google.android.gms.gcm.ACTION_HEARTBEAT_NOW"
    private const val ACTION_RECONNECT = "com.google.android.intent.action.GCM_RECONNECT"

    suspend fun status(context: Context): FcmStatus {
        val app = context.applicationContext
        val raw = withTimeoutOrNull(3_000) { request(app) } ?: return FcmStatus(FcmState.UNKNOWN, null, "GMS không phản hồi")
        return FcmStatus(if (raw == "3") FcmState.DISCONNECTED else FcmState.CONNECTED, raw)
    }

    /** Heartbeat first; force GCM_RECONNECT only if GMS still reports disconnected. */
    suspend fun reconnect(context: Context): FcmStatus {
        val app = context.applicationContext
        val initial = status(app)
        if (initial.state != FcmState.DISCONNECTED) return initial
        runCatching { app.sendBroadcast(Intent(ACTION_HEARTBEAT_NOW).setPackage(GmsSignals.GMS)) }
        delay(2_000)
        if (status(app).state == FcmState.DISCONNECTED) { runCatching { app.sendBroadcast(Intent(ACTION_RECONNECT).setPackage(GmsSignals.GMS)) }; delay(2_000) }
        return status(app)
    }

    private suspend fun request(app: Context): String? = suspendCancellableCoroutine { continuation ->
        val messageId = "hypernotify-status-${SystemClock.elapsedRealtime()}"
        val main = Handler(Looper.getMainLooper())
        val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
            val response = message.obj as? Intent
            val replyTo = response?.getStringExtra("In-Reply-To")
            if (response != null && (replyTo == null || replyTo == messageId) && continuation.isActive) continuation.resume(response.getStringExtra("status"))
            true
        })
        // GMS identifies the caller through this PendingIntent's creator package.
        val identity = PendingIntent.getBroadcast(app, 0, Intent().setPackage("com.google.example.invalidpackage"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val intent = Intent(ACTION_SEND).setPackage(GmsSignals.GMS)
            .putExtra("app", identity)
            .putExtra("google.to", "https://gcm.googleapis.com/local/status")
            .putExtra("google.message_id", messageId)
            .putExtra("google.ttl", "0")
            .putExtra("google.messenger", reply)
        runCatching {
            app.sendOrderedBroadcast(intent, GTALK_PERMISSION, object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) = Unit }, main, Activity.RESULT_OK, null, null)
        }.onFailure { if (continuation.isActive) continuation.resume(null) }
    }
}
