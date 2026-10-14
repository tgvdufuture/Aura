package com.aura.led.analytics

import android.content.Context

/**
 * Persisted state of the opt-in analytics pipeline and of the in-app update flow.
 *
 * Stored in SharedPreferences (the same `aura_prefs` file as the theme and the language)
 * because it must be readable synchronously from [android.app.Application], where Room
 * is not available yet.
 */
object AnalyticsPrefs {
    const val CONSENT_GRANTED = "granted"
    const val CONSENT_DENIED = "denied"

    private const val PREFS = "aura_prefs"
    private const val KEY_CONSENT = "analyticsConsent"
    private const val KEY_UPDATE_CHECK = "updateCheckEnabled"
    private const val KEY_LAST_CHECK_MS = "lastUpdateCheckMs"
    private const val KEY_LAST_PROMPT_MS = "lastPromptMs"
    private const val KEY_DISMISSED_TAG = "dismissedVersion"
    private const val KEY_NOTIFIED_TAG = "notifiedVersion"
    private const val KEY_PENDING_TAG = "pendingUpdateTag"
    private const val KEY_PENDING_VERSION = "pendingUpdateVersion"
    private const val KEY_PENDING_URL = "pendingUpdateUrl"
    private const val KEY_PENDING_SIZE = "pendingUpdateSize"
    private const val KEY_PENDING_NOTES = "pendingUpdateNotes"
    private const val KEY_SUPPORT_ANSWER = "supportAnswer"
    private const val KEY_LAST_SEEN_VERSION_CODE = "lastSeenVersionCode"
    private const val KEY_SETUP_FLAG = "setupFlag"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** `null` means the user has never been asked. */
    fun consent(context: Context): String? = prefs(context).getString(KEY_CONSENT, null)

    fun isGranted(context: Context): Boolean = consent(context) == CONSENT_GRANTED

    fun setConsent(context: Context, value: String) {
        prefs(context).edit().putString(KEY_CONSENT, value).apply()
    }

    // ---- Update flow ----

    fun isUpdateCheckEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_UPDATE_CHECK, true)

    fun setUpdateCheckEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_UPDATE_CHECK, enabled).apply()
    }

    fun lastUpdateCheckMs(context: Context): Long = prefs(context).getLong(KEY_LAST_CHECK_MS, 0L)

    fun setLastUpdateCheckMs(context: Context, value: Long) {
        prefs(context).edit().putLong(KEY_LAST_CHECK_MS, value).apply()
    }

    fun lastPromptMs(context: Context): Long = prefs(context).getLong(KEY_LAST_PROMPT_MS, 0L)

    fun setLastPromptMs(context: Context, value: Long) {
        prefs(context).edit().putLong(KEY_LAST_PROMPT_MS, value).apply()
    }

    fun dismissedTag(context: Context): String = prefs(context).getString(KEY_DISMISSED_TAG, "") ?: ""

    fun setDismissedTag(context: Context, tag: String) {
        prefs(context).edit().putString(KEY_DISMISSED_TAG, tag).apply()
    }

    /**
     * The last tag the user was told about by notification. A version is announced once: without
     * this, a user who ignores the notification would be told again every single day.
     */
    fun notifiedTag(context: Context): String = prefs(context).getString(KEY_NOTIFIED_TAG, "") ?: ""

    fun setNotifiedTag(context: Context, tag: String) {
        prefs(context).edit().putString(KEY_NOTIFIED_TAG, tag).apply()
    }

    // The release the app has discovered and not installed yet (`PendingUpdate`), written by the
    // background check so the notification can open the update screen without a second GitHub
    // request. Five entries, not one packed value: the release notes are free text.

    fun pendingUpdateTag(context: Context): String = prefs(context).getString(KEY_PENDING_TAG, "").orEmpty()

    fun pendingUpdateVersion(context: Context): String =
        prefs(context).getString(KEY_PENDING_VERSION, "").orEmpty()

    fun pendingUpdateUrl(context: Context): String =
        prefs(context).getString(KEY_PENDING_URL, "").orEmpty()

    fun pendingUpdateSize(context: Context): Long = prefs(context).getLong(KEY_PENDING_SIZE, 0L)

    fun pendingUpdateNotes(context: Context): String =
        prefs(context).getString(KEY_PENDING_NOTES, "").orEmpty()

    fun setPendingUpdate(
        context: Context,
        tag: String,
        versionName: String,
        apkUrl: String,
        sizeBytes: Long,
        notes: String,
    ) {
        prefs(context).edit()
            .putString(KEY_PENDING_TAG, tag)
            .putString(KEY_PENDING_VERSION, versionName)
            .putString(KEY_PENDING_URL, apkUrl)
            .putLong(KEY_PENDING_SIZE, sizeBytes)
            .putString(KEY_PENDING_NOTES, notes)
            .apply()
    }

    fun clearPendingUpdate(context: Context) {
        prefs(context).edit()
            .remove(KEY_PENDING_TAG)
            .remove(KEY_PENDING_VERSION)
            .remove(KEY_PENDING_URL)
            .remove(KEY_PENDING_SIZE)
            .remove(KEY_PENDING_NOTES)
            .apply()
    }

    // ---- Version bookkeeping (first run / update detection) ----

    /** 0 = the app never recorded a run before this one. */
    fun lastSeenVersionCode(context: Context): Int =
        prefs(context).getInt(KEY_LAST_SEEN_VERSION_CODE, 0)

    fun setLastSeenVersionCode(context: Context, code: Int) {
        prefs(context).edit().putInt(KEY_LAST_SEEN_VERSION_CODE, code).apply()
    }

    // ---- Support / monetization intent ----

    /**
     * The answer currently selected in the support card, `null` while the user has not answered.
     * Stored so the choice can be shown back and changed: an answer that cannot be revised is a
     * vote the user regrets and cannot take back.
     */
    fun supportAnswer(context: Context): String? =
        prefs(context).getString(KEY_SUPPORT_ANSWER, null)

    fun setSupportAnswer(context: Context, answer: String) {
        prefs(context).edit().putString(KEY_SUPPORT_ANSWER, answer).apply()
    }

    // ---- One-shot flags for lazily observed setup steps ----
    //
    // Notification access, the battery exemption and autostart are granted in system
    // screens, so Aura only observes the result later. These flags make sure the setup
    // funnel reports each grant once instead of on every app start.

    fun isSetupFlagSet(context: Context, name: String): Boolean =
        prefs(context).getBoolean("$KEY_SETUP_FLAG.$name", false)

    fun setSetupFlag(context: Context, name: String) {
        prefs(context).edit().putBoolean("$KEY_SETUP_FLAG.$name", true).apply()
    }
}
