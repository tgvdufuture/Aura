package com.aura.led.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aura.led.analytics.AnalyticsPrefs
import java.util.concurrent.TimeUnit

/**
 * Keeps the scheduled daily check in step with the "Check for new versions" setting.
 *
 * Safe to call on every process start: an existing schedule is kept rather than restarted, so
 * opening the app cannot push the check further away. Turning the setting off cancels the job
 * outright — a user who wants no network request at all gets none, from the app or from the
 * background.
 */
object UpdateScheduler {
    const val WORK_NAME = "aura_update_check"
    const val INTERVAL_HOURS = 24L

    fun sync(context: Context) {
        val workManager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return

        if (!AnalyticsPrefs.isUpdateCheckEnabled(context)) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<UpdateWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()

        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
