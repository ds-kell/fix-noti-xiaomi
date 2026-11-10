package io.github.hypernotifyfix.core.system

import io.github.hypernotifyfix.core.t
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import io.github.hypernotifyfix.MainActivity
import io.github.hypernotifyfix.R

/**
 * Optional, off by default. HyperOS' GMS limiter runs roughly 30 s after screen-off, so while the
 * screen is off Aurogon is asked to reload its allowlist at ~40 s, ~2 min and then every 10 min.
 * Needs a foreground service to keep the screen receiver alive; costs a little battery.
 */
object AurogonKeepAlive {
    private const val PREFS = "aurogon_keep_alive"
    internal const val EXTRA_STAGE = "stage"
    internal const val STAGE_FIRST = 1
    internal const val STAGE_SECOND = 2
    internal const val STAGE_PERIODIC = 3
    private const val FIRST_DELAY_MS = 40_000L
    private const val SECOND_DELAY_MS = 80_000L
    private const val PERIODIC_DELAY_MS = 10 * 60_000L

    private fun prefs(context: Context) = context.applicationContext.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun isEnabled(context: Context) = prefs(context).getBoolean("enabled", false)

    fun setEnabled(context: Context, enabled: Boolean): Boolean = runCatching {
        prefs(context).edit().putBoolean("enabled", enabled).commit()
        val app = context.applicationContext
        if (enabled) { GmsSignals.reloadAurogon(app); app.startForegroundService(Intent(app, AurogonKeepAliveService::class.java)) }
        else { cancelAlarm(app); app.stopService(Intent(app, AurogonKeepAliveService::class.java)) }
        true
    }.getOrElse { prefs(context).edit().putBoolean("enabled", false).apply(); false }

    fun startIfEnabled(context: Context) {
        if (isEnabled(context)) runCatching { context.applicationContext.startForegroundService(Intent(context.applicationContext, AurogonKeepAliveService::class.java)) }
    }

    internal fun onScreenOff(context: Context) { cancelAlarm(context); scheduleAlarm(context, STAGE_FIRST, FIRST_DELAY_MS) }
    internal fun onScreenOn(context: Context) { cancelAlarm(context); GmsSignals.reloadAurogon(context) }

    internal fun onAlarm(context: Context, stage: Int) {
        if (!isEnabled(context) || context.getSystemService(PowerManager::class.java)?.isInteractive == true) { cancelAlarm(context); return }
        GmsSignals.reloadAurogon(context)
        val (next, delay) = if (stage == STAGE_FIRST) STAGE_SECOND to SECOND_DELAY_MS else STAGE_PERIODIC to PERIODIC_DELAY_MS
        scheduleAlarm(context, next, delay)
    }

    private fun scheduleAlarm(context: Context, stage: Int, delayMs: Long) = runCatching {
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delayMs, alarmIntent(context, stage))
    }

    private fun cancelAlarm(context: Context) = runCatching { context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, STAGE_FIRST)) }

    // One request code for every stage: scheduling a stage replaces the previous one, and cancel matches any stage.
    private fun alarmIntent(context: Context, stage: Int) = PendingIntent.getBroadcast(
        context.applicationContext, 4108,
        Intent(context.applicationContext, AurogonAlarmReceiver::class.java).putExtra(EXTRA_STAGE, stage),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

class AurogonAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AurogonKeepAlive.onAlarm(context, intent.getIntExtra(AurogonKeepAlive.EXTRA_STAGE, AurogonKeepAlive.STAGE_PERIODIC))
}

class AurogonKeepAliveService : Service() {
    private var registered = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> AurogonKeepAlive.onScreenOff(context)
                Intent.ACTION_SCREEN_ON -> AurogonKeepAlive.onScreenOn(context)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!AurogonKeepAlive.isEnabled(this)) { stopSelf(); return }
        val notification = notification()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(NOTIFICATION_ID, notification)
        registerReceiver(screenReceiver, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) })
        registered = true
        if (getSystemService(PowerManager::class.java)?.isInteractive == false) AurogonKeepAlive.onScreenOff(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!AurogonKeepAlive.isEnabled(this)) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onDestroy() {
        if (registered) runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, t("Giữ allowlist Aurogon", "Keeping the Aurogon allowlist"), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon_monochrome)
            .setContentTitle(t("Đang giữ Google Play services khỏi bị giới hạn", "Keeping Google Play services from being limited"))
            .setContentText(t("Làm mới allowlist Aurogon khi màn hình tắt", "Refreshes the Aurogon allowlist while the screen is off"))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "aurogon_keep_alive"
        const val NOTIFICATION_ID = 4107
    }
}
