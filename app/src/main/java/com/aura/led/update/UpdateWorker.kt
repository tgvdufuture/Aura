package com.aura.led.update

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aura.led.analytics.Analytics
import com.aura.led.analytics.AnalyticsEvents
import com.aura.led.analytics.AnalyticsPrefs

/**
 * The daily GitHub check, run by WorkManager so it happens while the app is closed.
 *
 * This is the same check the app performs when a screen opens, against the same one-a-day budget
 * in [UpdatePolicy]: whichever runs first spends the day's request, so the background job can
 * never double a user's traffic. What it adds is the case the in-app check cannot cover — a user
 * who has not opened Aura for weeks still learns that a new version exists.
 *
 * Reporting stays opt-in: [Analytics] does nothing without consent, so a background run can never
 * start collecting on its own.
 */
class UpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!AnalyticsPrefs.isUpdateCheckEnabled(context)) return Result.success()

        val now = System.currentTimeMillis()
        // Read before the check writes its own timestamp: the announcement rule is decided against
        // the previous one (see UpdatePolicy.shouldAnnounce).
        val previousCheckMs = AnalyticsPrefs.lastUpdateCheckMs(context)
        if (!UpdatePolicy.shouldCheck(now, previousCheckMs)) {
            return Result.success()
        }

        Analytics.init(context)
        val installed = installedVersionName(context)
        when (val result = UpdateChecker.check(installed, installed)) {
            is CheckResult.UpToDate -> {
                AnalyticsPrefs.setLastUpdateCheckMs(context, now)
                // Nothing left to announce: the user installed the version the notification pointed
                // at, or a check already decided there is nothing to do.
                UpdateCache.clear(context)
                Analytics.track(
                    AnalyticsEvents.UPDATE_CHECK_RESULT,
                    mapOf(AnalyticsEvents.P_RESULT to "up_to_date"),
                )
            }

            is CheckResult.Failed -> {
                // Throttled like the success path: a broken network must not retry all day.
                AnalyticsPrefs.setLastUpdateCheckMs(context, now)
                UpdateChecker.logFailure(result.reason)
            }

            is CheckResult.UpdateAvailable -> {
                AnalyticsPrefs.setLastUpdateCheckMs(context, now)
                Analytics.track(
                    AnalyticsEvents.UPDATE_CHECK_RESULT,
                    mapOf(
                        AnalyticsEvents.P_RESULT to "update_available",
                        AnalyticsEvents.P_LATEST_TAG to result.tag,
                    ),
                )
                announce(context, result, now, previousCheckMs)
            }
        }

        // The process is about to be handed back to the system: flush what was queued, or the
        // events above die with it and the dashboard never sees the background checks.
        Analytics.flush()
        return Result.success()
    }

    private fun announce(
        context: Context,
        result: CheckResult.UpdateAvailable,
        nowMs: Long,
        previousCheckMs: Long,
    ) {
        val decision = UpdatePolicy.shouldAnnounce(
            nowMs = nowMs,
            previousCheckMs = previousCheckMs,
            lastPromptMs = AnalyticsPrefs.lastPromptMs(context),
            dismissedTag = AnalyticsPrefs.dismissedTag(context),
            notifiedTag = AnalyticsPrefs.notifiedTag(context),
            latestTag = result.tag,
        )
        if (!decision.shouldPrompt) {
            Log.d(TAG, "update ${result.tag} not announced: ${decision.reason}")
            return
        }

        val pending = UpdateCache.remember(context, result)
        UpdateNotifier.notify(context, pending)
        Analytics.track(
            AnalyticsEvents.UPDATE_NOTIFICATION_SHOWN,
            mapOf(AnalyticsEvents.P_LATEST_TAG to result.tag),
        )
    }

    private fun installedVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    private companion object {
        const val TAG = "AuraUpdate"
    }
}
