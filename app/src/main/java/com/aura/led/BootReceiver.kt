package com.aura.led

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aura.led.service.AuraForegroundService

/**
 * Restarts Aura's foreground service after the device boots so the notification
 * listener keeps driving the LED without requiring the app to be opened manually.
 *
 * Also handles MIUI/HyperOS "quick boot" (`QUICKBOOT_POWERON`), which can replace
 * the standard BOOT_COMPLETED broadcast when fast boot is enabled.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != ACTION_QUICKBOOT_POWERON) return

        Log.d(TAG, "boot completed (action=$action) -> starting foreground service")
        runCatching { AuraForegroundService.start(context) }
            .onFailure { Log.w(TAG, "failed to start foreground service", it) }
    }

    private companion object {
        const val TAG = "AuraBoot"
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
