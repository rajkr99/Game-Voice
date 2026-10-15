package com.gamervoice.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.gamervoice.app.data.SettingsRepository
import com.gamervoice.app.service.CallManager

/** Application entry point: creates notification channels and app-wide singletons. */
class VoiceApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var callManager: CallManager
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        callManager = CallManager(this, settings)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_CALL, "Active call", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, "Call alerts", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    companion object {
        const val CHANNEL_CALL = "call"
        const val CHANNEL_ALERTS = "alerts"
    }
}
