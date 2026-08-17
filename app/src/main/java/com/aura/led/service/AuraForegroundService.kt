package com.aura.led.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.aura.led.R

/**
 * Persistent foreground service whose notification keeps Aura's process alive so the
 * NotificationListenerService keeps receiving notifications under HyperOS's aggressive
 * background killing (risk "Service tué par HyperOS").
 */
class AuraForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_led)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "aura_service"
        const val NOTIFICATION_ID = 1

        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context) {
            running = true
            ContextCompat.startForegroundService(
                context,
                Intent(context, AuraForegroundService::class.java),
            )
        }

        fun stop(context: Context) {
            running = false
            context.stopService(Intent(context, AuraForegroundService::class.java))
        }
    }
}
