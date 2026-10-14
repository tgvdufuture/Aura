package com.aura.led.update

import android.content.Context
import com.aura.led.analytics.AnalyticsPrefs

/**
 * The update Aura knows about but has not installed, kept between processes.
 *
 * The background check runs while the app is closed, so what it finds has to survive the process:
 * that record is what the notification opens, and it is dropped as soon as it is no longer true
 * (the version got installed, or a later check came back up to date).
 */
object UpdateCache {

    /** `null` when nothing was announced, or when the stored record is unusable. */
    fun load(context: Context): PendingUpdate? {
        val pending = PendingUpdate(
            tag = AnalyticsPrefs.pendingUpdateTag(context),
            versionName = AnalyticsPrefs.pendingUpdateVersion(context),
            apkUrl = AnalyticsPrefs.pendingUpdateUrl(context),
            sizeBytes = AnalyticsPrefs.pendingUpdateSize(context),
            notes = AnalyticsPrefs.pendingUpdateNotes(context),
        )
        return pending.takeIf { it.isUsable() }
    }

    /** Records the release and the fact that the user was told about it. */
    fun remember(context: Context, update: CheckResult.UpdateAvailable): PendingUpdate {
        val pending = PendingUpdate.of(update)
        AnalyticsPrefs.setPendingUpdate(
            context = context,
            tag = pending.tag,
            versionName = pending.versionName,
            apkUrl = pending.apkUrl,
            sizeBytes = pending.sizeBytes,
            notes = pending.notes,
        )
        AnalyticsPrefs.setNotifiedTag(context, pending.tag)
        return pending
    }

    /**
     * Drops everything about a version that is no longer relevant, including a notification the
     * user might still be keeping in the shade pointing at an APK they no longer need.
     */
    fun clear(context: Context) {
        AnalyticsPrefs.clearPendingUpdate(context)
        AnalyticsPrefs.setNotifiedTag(context, "")
        UpdateNotifier.cancel(context)
    }
}
