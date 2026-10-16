package com.aura.led.analytics

import android.content.Context
import android.os.Build
import android.util.Log
import com.aura.led.BuildConfig
import com.aura.led.ThemeManager
import com.aura.led.notification.NotificationListenerState
import com.aura.led.root.RootManager
import com.aura.led.shizuku.ShizukuManager
import com.posthog.PersonProfiles
import com.posthog.PostHog
import com.posthog.PostHogPropertiesSanitizer
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

/**
 * The only entry point for usage reporting.
 *
 * Nothing is collected until the user explicitly agrees ([grantConsent]); until then the
 * PostHog SDK is not even initialised, so no request can leave the device. Reporting is
 * anonymous by design: `PersonProfiles.NEVER`, no `identify()` call, no hardware, account
 * or advertising identifier — the SDK's own per-install anonymous id is all there is, and
 * [revokeConsent] resets it immediately.
 *
 * Every call is best-effort: a failure here can never break the LED pipeline or the UI.
 */
object Analytics {
    private const val TAG = "AuraAnalytics"

    /** EU data residency — the app's users are mostly in the EU. */
    private const val HOST = "https://eu.i.posthog.com"

    @Volatile
    private var active = false

    @Volatile
    private var foregroundTracked = false

    /** Initialises the SDK when consent was granted. Safe to call repeatedly. */
    fun init(context: Context) {
        if (active || !AnalyticsPrefs.isGranted(context)) return
        val token = BuildConfig.POSTHOG_PROJECT_TOKEN
        if (token.isBlank()) {
            Log.w(TAG, "no POSTHOG_PROJECT_TOKEN in local.properties -> usage analytics disabled")
            return
        }
        synchronized(this) {
            if (active) return
            runCatching {
                val config = PostHogAndroidConfig(
                    projectToken = token,
                    host = HOST,
                    captureApplicationLifecycleEvents = false,
                    captureDeepLinks = false,
                    captureScreenViews = false,
                    capturePushNotificationSubscriptions = false,
                    capturePushNotificationOpened = false,
                ).apply {
                    personProfiles = PersonProfiles.NEVER
                    preloadFeatureFlags = false
                    optOut = false
                    // Last hook before the queue, on the merged payload: the SDK context (timezone,
                    // carrier, user agent, device name, screen metrics) is dropped unless the
                    // consent screen names it. See AnalyticsEventFilter.
                    propertiesSanitizer = object : PostHogPropertiesSanitizer {
                        override fun sanitize(properties: MutableMap<String, Any>): Map<String, Any> =
                            AnalyticsEventFilter.apply(properties)
                    }
                }
                PostHogAndroid.setup(context.applicationContext, config)
                PostHog.register("aura_version", versionName(context))
                PostHog.register("aura_version_code", versionCode(context))
                PostHog.register("android_sdk", Build.VERSION.SDK_INT)
                PostHog.register("device_model", Build.MODEL)
                active = true
            }.onFailure { Log.w(TAG, "PostHog setup failed", it) }
        }
    }

    /**
     * Records the user's agreement, persists it and starts reporting.
     *
     * Reporting starts *in the session that is already foreground*: the resume that opened the
     * consent screen ran while analytics was still off, so without this call that session serves
     * its whole life with no `app_opened` at all. It is the first session of every install, the one
     * that is counted as an active install and that carries `is_first_run` — losing it is losing
     * new users. See [onForeground] for the other half of the fix.
     */
    fun grantConsent(context: Context) {
        AnalyticsPrefs.setConsent(context, AnalyticsPrefs.CONSENT_GRANTED)
        init(context)
        if (active) runCatching { PostHog.optIn() }
        onForeground(context)
    }

    /** Records the refusal, stops reporting immediately and drops the anonymous identity. */
    fun revokeConsent(context: Context) {
        AnalyticsPrefs.setConsent(context, AnalyticsPrefs.CONSENT_DENIED)
        if (active) {
            runCatching {
                PostHog.optOut()
                PostHog.reset()
            }.onFailure { Log.w(TAG, "PostHog opt-out failed", it) }
        }
    }

    fun track(event: String, properties: Map<String, Any?> = emptyMap()) {
        if (!active) return
        val safe = AnalyticsSanitizer.sanitize(properties)
        runCatching { PostHog.capture(event = event, properties = safe) }
            .onFailure { Log.w(TAG, "capture failed for $event", it) }
    }

    fun screen(name: String) {
        if (!active) return
        runCatching { PostHog.screen(screenTitle = name) }
            .onFailure { Log.w(TAG, "screen failed for $name", it) }
    }

    /**
     * One `app_opened` per foreground session, with everything the dashboard needs to
     * segment the base: version, OS, locale, theme, setup progress and privileges.
     *
     * The "already reported this session" flag is only consumed once reporting is actually
     * possible. It used to be set before that check, so a resume that happened while analytics was
     * still off spent the session's one event on nothing — and every user who consented on first
     * run was invisible in `app_opened` (the metric G1/G4 of the dashboard are built on) for that
     * whole session, however much they used the app.
     */
    fun onForeground(context: Context) {
        if (!active) return
        if (foregroundTracked) return
        foregroundTracked = true
        val lastSeen = AnalyticsPrefs.lastSeenVersionCode(context)
        val currentCode = versionCode(context)
        track(
            AnalyticsEvents.APP_OPENED,
            mapOf(
                AnalyticsEvents.P_THEME to ThemeManager.getSavedMode(context),
                AnalyticsEvents.P_LOCALE to (context.resources.configuration.locales[0]?.language ?: ""),
                AnalyticsEvents.P_IS_FIRST_RUN to (lastSeen == 0),
                AnalyticsEvents.P_IS_UPDATE to isUpdatedInPlace(context),
                AnalyticsEvents.P_LISTENER_CONNECTED to NotificationListenerState.connected.value,
                AnalyticsEvents.P_PRIVILEGE to privilege(),
            ),
        )
        if (lastSeen != currentCode) AnalyticsPrefs.setLastSeenVersionCode(context, currentCode)
    }

    /**
     * Sends whatever is queued, now. Used before a background job hands the process back to the
     * system: the SDK would otherwise flush on its own timer, which will not run again.
     */
    fun flush() {
        if (active) runCatching { PostHog.flush() }
    }

    /** Ends the foreground session so the next resume reports a new one. */
    fun onBackground() {
        foregroundTracked = false
        if (active) runCatching { PostHog.flush() }
    }

    /**
     * Reports a setup-funnel step at most once per install. [flag] defaults to the event name
     * and only needs to be given when one event covers several paths (Shizuku vs root).
     */
    fun trackSetupOnce(
        context: Context,
        event: String,
        properties: Map<String, Any?> = emptyMap(),
        flag: String = event,
    ) {
        if (AnalyticsPrefs.isSetupFlagSet(context, flag)) return
        AnalyticsPrefs.setSetupFlag(context, flag)
        track(event, properties)
    }

    /**
     * True when the installed APK replaced an earlier one. `lastUpdateTime` differs from
     * `firstInstallTime` exactly when the package was updated in place, which separates
     * "existing user who updated" from "fresh install" — the number the update flow is
     * meant to move.
     */
    private fun isUpdatedInPlace(context: Context): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime != info.firstInstallTime
    }.getOrDefault(false)

    /** Which privilege path is usable, without reporting whether the user rooted their phone beyond that. */
    private fun privilege(): String = when {
        ShizukuManager.state.value.hasPermission -> AnalyticsEvents.KIND_SHIZUKU
        RootManager.state.value.available -> AnalyticsEvents.KIND_ROOT
        else -> "none"
    }

    private fun versionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    private fun versionCode(context: Context): Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    }.getOrDefault(0)
}
