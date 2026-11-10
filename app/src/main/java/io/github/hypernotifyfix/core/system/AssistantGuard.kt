package io.github.hypernotifyfix.core.system

import io.github.hypernotifyfix.core.t
import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.UserManager
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.speech.RecognitionService
import android.util.Log
import io.github.hypernotifyfix.domain.AssistantCandidate
import io.github.hypernotifyfix.domain.AssistantRepair
import io.github.hypernotifyfix.domain.AssistantSwitch

/**
 * HyperOS CN clears `voice_interaction_service` on every boot: a user-installed assistant (Gemini,
 * Claude, ChatGPT…) cannot be bound before first unlock, so the system drops it and never puts it
 * back, and the power-key long press does nothing. Holding WRITE_SECURE_SETTINGS (granted through
 * Shizuku, also by the assistant picker's "Áp dụng") is the on/off switch; the guard then restores
 * the service right after unlock and whenever it changes.
 */
object AssistantGuard {
    private const val TAG = "AssistantGuard"
    private const val PREFS = "assistant_guard"
    private const val KEY_SERVICE = "voice_interaction_service"
    private const val KEY_ASSISTANT = "assistant"
    private const val GOOGLE = "com.google.android.googlequicksearchbox"
    internal const val WATCH_JOB_ID = 4110
    internal const val UNLOCK_JOB_ID = 4111

    data class Status(val enabled: Boolean, val assistant: String?, val service: String?, val lastResult: String?, val lastRun: Long, val setUp: Boolean) {
        val healthy get() = assistant != null && assistant == service
    }

    private fun prefs(context: Context) = context.applicationContext.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun isEnabled(context: Context) = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun status(context: Context) = prefs(context).let {
        Status(isEnabled(context), read(context, KEY_ASSISTANT), read(context, KEY_SERVICE), it.getString("last_result", null), it.getLong("last_run", 0L), isSetUp(context))
    }

    /**
     * After one Shizuku switch the power key, Xiao AI removal and the guard are in place, so switching
     * between non-Xiao assistants only needs the system "Digital assistant app" picker (the role must
     * move: on boot the system resets `assistant` to the role holder) plus Xiaomi's pop-up permission.
     */
    fun isSetUp(context: Context) = prefs(context).getBoolean("set_up", false) && isEnabled(context)
    fun markSetUp(context: Context, packageName: String) = prefs(context).edit().putBoolean("set_up", true).putStringSet("popup_handled", popupHandled(context) + packageName).apply()

    /** Packages whose Xiaomi pop-up permission was granted via Shizuku or whose editor was already opened. */
    fun popupHandled(context: Context): Set<String> = prefs(context).getStringSet("popup_handled", emptySet()).orEmpty()
    fun markPopupHandled(context: Context, packageName: String) = prefs(context).edit().putStringSet("popup_handled", popupHandled(context) + packageName).apply()
    /** The Google app is pre-allowed by HyperOS; Claude, ChatGPT and others were blocked without it. */
    fun needsPopupPermission(context: Context, packageName: String) = packageName != GOOGLE && packageName != AssistantSwitch.XIAO_AI && packageName !in popupHandled(context)

    fun onBoot(context: Context, action: String?) {
        if (!isEnabled(context)) return
        // LOCKED_BOOT_COMPLETED arrives seconds after boot; HyperOS delivers BOOT_COMPLETED to apps much later.
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) schedule(context, JobInfo.Builder(UNLOCK_JOB_ID, component(context)).setOverrideDeadline(0L))
        else repair(context, "boot")
        watch(context)
    }

    @Synchronized
    fun repair(context: Context, reason: String): Boolean {
        val app = context.applicationContext
        val current = read(app, KEY_SERVICE)
        val unlocked = app.getSystemService(UserManager::class.java).isUserUnlocked
        val (success, message) = when (val decision = AssistantRepair.plan(current, read(app, KEY_ASSISTANT), availableServices(app), unlocked)) {
            AssistantRepair.Decision.Healthy -> true to t("Trợ lý vẫn đúng ($reason)", "Assistant still correct ($reason)")
            is AssistantRepair.Decision.Skip -> false to "${decision.reason} ($reason)"
            is AssistantRepair.Decision.Write -> when {
                !isEnabled(app) -> false to t("Chưa cấp WRITE_SECURE_SETTINGS", "WRITE_SECURE_SETTINGS not granted")
                else -> runCatching { Settings.Secure.putString(app.contentResolver, KEY_SERVICE, decision.value) }.fold(
                    onSuccess = { if (read(app, KEY_SERVICE) == decision.value) true to t("Đã khôi phục dịch vụ trợ lý ($reason)", "Assistant service restored ($reason)") else false to t("HyperOS không giữ giá trị vừa ghi", "HyperOS did not keep the value just written") },
                    onFailure = { false to t("HyperOS chặn ghi dịch vụ trợ lý: ${it.javaClass.simpleName}", "HyperOS blocked writing the assistant service: ${it.javaClass.simpleName}") },
                )
            }
        }
        Log.i(TAG, "reason=$reason success=$success message=$message")
        // Healthy checks are frequent (every app open); keep the last real repair or failure visible.
        if (current != read(app, KEY_SERVICE) || !success) prefs(app).edit().putString("last_result", message).putLong("last_run", System.currentTimeMillis()).apply()
        return success
    }

    /** Event-driven: system_server starts the job only when one of the two keys changes; no polling. */
    fun watch(context: Context) = schedule(
        context,
        JobInfo.Builder(WATCH_JOB_ID, component(context))
            .setTriggerContentUpdateDelay(200L)
            .setTriggerContentMaxDelay(1_000L)
            .addTriggerContentUri(JobInfo.TriggerContentUri(Settings.Secure.getUriFor(KEY_SERVICE), 0))
            .addTriggerContentUri(JobInfo.TriggerContentUri(Settings.Secure.getUriFor(KEY_ASSISTANT), 0)),
    )

    fun cancel(context: Context) = runCatching {
        context.applicationContext.getSystemService(JobScheduler::class.java).apply { cancel(WATCH_JOB_ID); cancel(UNLOCK_JOB_ID) }
    }

    private fun component(context: Context) = ComponentName(context.applicationContext, AssistantGuardJobService::class.java)

    private fun schedule(context: Context, builder: JobInfo.Builder) = runCatching {
        context.applicationContext.getSystemService(JobScheduler::class.java).schedule(builder.build()) == JobScheduler.RESULT_SUCCESS
    }.getOrDefault(false)

    /** Normalised so the short (`pkg/.Cls`) and long forms compare equal. */
    private fun read(context: Context, key: String): String? =
        runCatching { Settings.Secure.getString(context.contentResolver, key) }.getOrNull()
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { ComponentName.unflattenFromString(it)?.flattenToString() ?: it }

    /**
     * Apps that can be the assistant. MATCH_UNINSTALLED_PACKAGES keeps Xiao AI listed after
     * `pm uninstall --user 0`, so it can be chosen again to restore it.
     */
    fun candidates(context: Context): List<AssistantCandidate> {
        val pm = context.packageManager
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES
        val recognizers = pm.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), flags)
            .associate { it.serviceInfo.packageName to ComponentName(it.serviceInfo.packageName, it.serviceInfo.name).flattenToString() }
        return pm.queryIntentServices(Intent(VoiceInteractionService.SERVICE_INTERFACE), flags)
            .map { it.serviceInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName == AssistantSwitch.XIAO_AI || isInstalled(context, it.packageName) }
            .map { info ->
                val label = when (info.packageName) {
                    // The Gemini assistant is hosted by the Google app, which is labelled just "Google".
                    GOOGLE -> "Gemini (Google)"
                    AssistantSwitch.XIAO_AI -> "Xiao AI"
                    else -> info.applicationInfo.loadLabel(pm).toString()
                }
                AssistantCandidate(info.packageName, label, ComponentName(info.packageName, info.name).flattenToString(), recognizers[info.packageName])
            }
            .sortedWith(compareBy<AssistantCandidate> { it.isXiaoAi }.thenBy { it.label.lowercase() })
    }

    fun isInstalled(context: Context, packageName: String) = runCatching {
        context.packageManager.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES).flags and ApplicationInfo.FLAG_INSTALLED != 0
    }.getOrDefault(false)

    /** Before unlock PackageManager lists only direct-boot-aware services: exactly the "can bind now" set. */
    private fun availableServices(context: Context): Set<String> =
        context.packageManager.queryIntentServices(Intent(VoiceInteractionService.SERVICE_INTERFACE), 0)
            .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name).flattenToString() }
            .toSet()
}

class AssistantGuardJobService : JobService() {
    private var unlockReceiver: BroadcastReceiver? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (params.jobId == AssistantGuard.UNLOCK_JOB_ID && !unlocked()) {
            // USER_UNLOCKED only reaches registered receivers, so hold the job open until it arrives.
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = finish(params)
            }
            unlockReceiver = receiver
            registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
            if (unlocked()) finish(params) // Unlock landed between the check and registration.
            return true
        }
        AssistantGuard.repair(this, if (params.jobId == AssistantGuard.WATCH_JOB_ID) "watcher" else "unlock")
        // Content-trigger jobs are one-shot. Our own write re-fires once and then finds nothing to do.
        AssistantGuard.watch(this)
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean {
        unregister()
        // Stopped while still locked (job time limit): reschedule so the unlock is not missed.
        return params.jobId == AssistantGuard.UNLOCK_JOB_ID && !unlocked()
    }

    private fun finish(params: JobParameters) {
        if (unlockReceiver == null) return
        unregister()
        AssistantGuard.repair(this, "unlock")
        AssistantGuard.watch(this)
        jobFinished(params, false)
    }

    private fun unregister() {
        unlockReceiver?.let { runCatching { unregisterReceiver(it) } }
        unlockReceiver = null
    }

    private fun unlocked() = getSystemService(UserManager::class.java).isUserUnlocked
}
