package com.aura.led.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The filter is what stands between the SDK's own context and the user, so these tests run on the
 * property map captured from a real device payload (2026-10-14, `app_opened` posted to the EU
 * project) rather than on a hand-written example.
 *
 * The oracle is the promise, not the implementation: the consent screen and the README name the app
 * version, the Android version, the device model, the language and the theme, and say nothing about
 * a timezone, an operator, a ROM build string or internal debug counters.
 */
class AnalyticsEventFilterTest {

    /** Literal properties of one captured `app_opened`, SDK context included. */
    private val capturedPayload = mapOf(
        // What Aura itself sends
        "aura_version" to "0.6.0",
        "aura_version_code" to 10,
        "android_sdk" to 36,
        "device_model" to "2511FPC34G",
        "theme" to "auto",
        "locale" to "fr",
        "is_first_run" to false,
        "is_update" to true,
        "listener_connected" to false,
        "listener_enabled" to true,
        "privilege" to "shizuku",
        // SDK context the promise covers
        "\$app_name" to "Aura",
        "\$app_version" to "0.6.0",
        "\$app_build" to 10,
        "\$app_namespace" to "com.aura.led",
        "\$device_model" to "2511FPC34G",
        "\$device_manufacturer" to "Xiaomi",
        "\$os_name" to "Android",
        "\$os_version" to "16",
        "\$locale" to "fr-",
        "\$lib" to "posthog-android",
        "\$lib_version" to "3.72.2",
        "\$session_id" to "01a13a46-4063-74e1-b4a0-f6e4cb646114",
        "\$is_identified" to false,
        "\$process_person_profile" to false,
        // SDK context the promise does not cover
        "\$device_name" to "klee",
        "\$device_type" to "Mobile",
        "\$is_emulator" to false,
        "\$timezone" to "Europe/Paris",
        "\$network_carrier" to "SFR",
        "\$network_wifi" to true,
        "\$user_agent" to "Dalvik/2.1.0 (Linux; U; Android 16; 2511FPC34G Build/BP2A.250605.031.A3)",
        "\$raw_user_agent" to "Dalvik/2.1.0 (Linux; U; Android 16; 2511FPC34G Build/BP2A.250605.031.A3)",
        "\$referrer" to "android-app://com.example",
        "\$screen_width" to 390,
        "\$screen_height" to 848,
        "\$screen_density" to 3.25,
        "\$recording_status" to "disabled",
        "\$sdk_debug_replay_capture_mode" to "wireframe",
        "\$sdk_debug_pending_queue_size" to 0,
    )

    /** Nothing here may leave the device: it says where the user is, who their operator is, or which ROM they run. */
    private val neverPromised = setOf(
        "\$device_name", "\$device_type", "\$is_emulator", "\$timezone", "\$network_carrier",
        "\$network_wifi", "\$user_agent", "\$raw_user_agent", "\$referrer",
        "\$screen_width", "\$screen_height", "\$screen_density", "\$recording_status",
        "\$sdk_debug_replay_capture_mode", "\$sdk_debug_pending_queue_size",
    )

    /** What the consent screen and the README promise, key by key. */
    private val promised = setOf(
        "aura_version", "aura_version_code", "android_sdk", "device_model", "theme", "locale",
        "is_first_run", "is_update", "listener_connected", "listener_enabled", "privilege",
        "\$app_name", "\$app_version", "\$app_build", "\$app_namespace", "\$device_model",
        "\$device_manufacturer", "\$os_name", "\$os_version", "\$locale", "\$lib", "\$lib_version",
        "\$session_id", "\$is_identified", "\$process_person_profile",
    )

    @Test
    fun `drops the SDK context the promise never made`() {
        val leaked = AnalyticsEventFilter.apply(capturedPayload).keys intersect neverPromised

        assertTrue("fields the privacy screen never mentions: $leaked", leaked.isEmpty())
    }

    @Test
    fun `keeps every field the promise covers`() {
        val missing = promised - AnalyticsEventFilter.apply(capturedPayload).keys

        assertTrue("promised fields the filter would drop: $missing", missing.isEmpty())
    }

    @Test
    fun `drops an undeclared key instead of passing it on`() {
        val undeclared = mapOf(
            "\$sdk_debug_added_by_a_future_version" to 1,
            "notification_text" to "see you at 8",
            "sender_name" to "Alice",
            "matchKey" to "alice",
        )

        assertEquals(emptyMap<String, Any>(), AnalyticsEventFilter.apply(undeclared))
    }

    @Test
    fun `passes a kept value through untouched`() {
        val kept = AnalyticsEventFilter.apply(
            mapOf("device_model" to "2511FPC34G", "\$is_emulator" to false, "seconds" to 10)
        )

        assertEquals(mapOf("device_model" to "2511FPC34G", "seconds" to 10), kept)
    }

    @Test
    fun `an empty payload stays empty`() {
        assertTrue(AnalyticsEventFilter.apply(emptyMap()).isEmpty())
    }

    @Test
    fun `the allow-list holds no key that could carry content`() {
        val forbidden = setOf(
            "notification", "sender", "text", "message", "contact", "group", "match",
            "title", "email", "phone",
        )
        val leaks = AnalyticsEventFilter.ALLOWED_KEYS.filter { key ->
            val flat = key.lowercase().removePrefix("\$").replace("_", "")

            forbidden.any { flat.contains(it) }
        }

        assertTrue("keys that could carry notification content: $leaks", leaks.isEmpty())
    }
}
