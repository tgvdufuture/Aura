package com.aura.led.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class NewYearCelebrationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != NewYearScheduler.ACTION_NEW_YEAR) return

        Log.d(TAG, "New Year's animation alarm received")
        ContextCompat.startForegroundService(
            context,
            Intent(context, AuraForegroundService::class.java)
                .setAction(NewYearScheduler.ACTION_NEW_YEAR),
        )
    }

    private companion object {
        const val TAG = "AuraNewYear"
    }
}
