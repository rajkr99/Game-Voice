package com.gamervoice.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.gamervoice.app.MainActivity
import com.gamervoice.app.VoiceApp

/**
 * Foreground service (type=microphone) that keeps the mic and network alive while a
 * game is in the foreground or the screen is off. Holds a partial wake lock + Wi-Fi lock
 * only for the duration of the call.
 */
class CallService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = (application as VoiceApp).callManager
        if (!manager.isInCall) {
            // Restarted after process death with no call: nothing to keep alive.
            stopSelf(); return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, build(this), type)
        acquireLocks()
        return START_NOT_STICKY
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        if (wakeLock == null) {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GamerVoice:call").apply { acquire() }
        }
        if (wifiLock == null) {
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager)
                .createWifiLock(mode, "GamerVoice:wifi").apply { acquire() }
        }
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release(); wakeLock = null
        wifiLock?.takeIf { it.isHeld }?.release(); wifiLock = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped the app away: keep the call (that's the point), nothing to do.
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        private const val NOTIF_ID = 1
        private const val DROP_ID = 2
        const val ACTION_MUTE = "com.gamervoice.MUTE"
        const val ACTION_LEAVE = "com.gamervoice.LEAVE"

        fun build(ctx: Context): Notification {
            val s = (ctx.applicationContext as VoiceApp).callManager.state.value
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), flags)
            fun action(a: String, code: Int) = PendingIntent.getBroadcast(
                ctx, code, Intent(ctx, CallActionReceiver::class.java).setAction(a), flags
            )
            return NotificationCompat.Builder(ctx, VoiceApp.CHANNEL_CALL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Room ${s.room}")
                .setContentText("${s.people.size} in call • ${if (s.micOn) "Mic on" else "Muted"}")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(open)
                .addAction(0, if (s.micOn) "Mute" else "Unmute", action(ACTION_MUTE, 1))
                .addAction(0, "Leave", action(ACTION_LEAVE, 2))
                .build()
        }

        @SuppressLint("MissingPermission")
        fun update(ctx: Context) {
            if (!(ctx.applicationContext as VoiceApp).callManager.isInCall) return
            runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, build(ctx)) }
        }

        @SuppressLint("MissingPermission")
        fun notifyDropped(ctx: Context, room: String) {
            val open = PendingIntent.getActivity(
                ctx, 3, Intent(ctx, MainActivity::class.java).putExtra("rejoin", room),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(ctx, VoiceApp.CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Voice chat disconnected")
                .setContentText("Tap to rejoin room $room")
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            runCatching { NotificationManagerCompat.from(ctx).notify(DROP_ID, n) }
        }
    }
}

/** Handles Mute / Leave buttons in the call notification. Also works for headset media button via MainActivity. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val m = (context.applicationContext as VoiceApp).callManager
        when (intent.action) {
            CallService.ACTION_MUTE -> m.toggleMic()
            CallService.ACTION_LEAVE -> m.leave()
        }
    }
}
