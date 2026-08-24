package com.aura.led.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object NewYearScheduler {
    const val ACTION_NEW_YEAR = "com.aura.led.action.NEW_YEAR"

    private const val TAG = "AuraNewYear"
    private const val REQUEST_CODE = 2026

    /** Returns the next local January 1st at 00:00 after [nowMillis]. */
    fun nextNewYearMillis(nowMillis: Long, zoneId: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        return ZonedDateTime.of(
            now.year + 1,
            1,
            1,
            0,
            0,
            0,
            0,
            zoneId,
        ).toInstant().toEpochMilli()
    }

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMillis = nextNewYearMillis(System.currentTimeMillis(), ZoneId.systemDefault())
        val pendingIntent = pendingIntent(context)

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
            Log.d(TAG, "scheduled exact New Year's animation for $triggerAtMillis")
        } catch (error: SecurityException) {
            // Android may require the user to grant exact-alarm access. The fallback still
            // wakes the app around midnight without blocking the rest of the feature.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
            Log.w(TAG, "exact alarm unavailable; scheduled an inexact New Year's alarm", error)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, NewYearCelebrationReceiver::class.java).setAction(ACTION_NEW_YEAR),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
