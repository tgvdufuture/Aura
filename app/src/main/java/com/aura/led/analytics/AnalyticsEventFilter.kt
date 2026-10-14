package com.aura.led.analytics

/**
 * The allow-list applied to the whole outgoing payload, key by key.
 *
 * [AnalyticsSanitizer] guards what Aura's own call sites pass. It cannot guard the rest: the
 * PostHog SDK merges its own context into every event *after* the call site — device name,
 * `$timezone`, `$network_carrier`, `$user_agent` (the full ROM build string), screen metrics and a
 * dozen internal `$sdk_debug_*` fields — none of which the consent screen or the README promises.
 * `PostHogConfig.propertiesSanitizer` is the last hook before the queue and it receives the merged
 * map (checked in the SDK bytecode: `captureStateless` merges the context in `buildProperties`,
 * then `buildEvent` calls `sanitize`), so this object is where the privacy promise is *enforced*
 * instead of trusted.
 *
 * Fail-closed on purpose: a key not named here is dropped, including anything a future SDK version
 * adds. The keys kept are the ones already written down for the user — app version, Android
 * version, device model, language, plus the two flags that encode "no person profile".
 */
object AnalyticsEventFilter {

    /**
     * Super-properties registered once in [Analytics.init]. They are not per-event properties, so
     * they are absent from [AnalyticsEvents.PROPERTY_KEYS], but the consent screen promises them:
     * app and Android version, device model.
     */
    private val APP_KEYS = setOf("aura_version", "aura_version_code", "android_sdk", "device_model")

    /** SDK keys kept, each one covered by the consent screen's "If you agree" list or the README. */
    private val SDK_KEYS = setOf(
        // App version
        "\$app_name", "\$app_version", "\$app_build", "\$app_namespace", "\$lib", "\$lib_version",
        // Device model
        "\$device_model", "\$device_manufacturer",
        // Android version and language
        "\$os_name", "\$os_version", "\$locale",
        // Session grouping, and the screen name that carries our own explicit `$screen` events
        "\$session_id", "\$screen_name",
        // PersonProfiles.NEVER: these two tell PostHog not to build a profile
        "\$is_identified", "\$process_person_profile",
    )

    /** Every property key allowed to leave the device. */
    val ALLOWED_KEYS: Set<String> = AnalyticsEvents.PROPERTY_KEYS + APP_KEYS + SDK_KEYS

    fun apply(properties: Map<String, Any>): Map<String, Any> =
        properties.filterKeys { it in ALLOWED_KEYS }
}
