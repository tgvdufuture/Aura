package com.aura.led.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sanitizer is the mechanism that keeps notification content out of the analytics
 * payload, so these tests assert what is *dropped*, not only what passes through.
 */
class AnalyticsSanitizerTest {

    @Test
    fun `drops every property key that is not in the allow-list`() {
        val result = AnalyticsSanitizer.sanitize(
            mapOf(
                "sender_name" to "Alice",
                "matchKey" to "alice",
                "notification_text" to "see you at 8",
                "target_package" to "com.whatsapp",
            )
        )

        assertEquals(mapOf("target_package" to "com.whatsapp"), result)
    }

    @Test
    fun `drops strings longer than the cap and keeps shorter ones`() {
        val longLabel = "a".repeat(AnalyticsSanitizer.MAX_STRING_LENGTH + 1)
        val atCap = "b".repeat(AnalyticsSanitizer.MAX_STRING_LENGTH)

        val result = AnalyticsSanitizer.sanitize(
            mapOf(
                AnalyticsEvents.P_TARGET_PACKAGE to longLabel,
                AnalyticsEvents.P_ANIMATION_ID to atCap,
            )
        )

        assertEquals(mapOf(AnalyticsEvents.P_ANIMATION_ID to atCap), result)
    }

    @Test
    fun `keeps booleans and numbers and drops nulls`() {
        val result = AnalyticsSanitizer.sanitize(
            mapOf(
                AnalyticsEvents.P_ENABLED to true,
                AnalyticsEvents.P_SECONDS to 10,
                AnalyticsEvents.P_INTERVAL_S to 15L,
                AnalyticsEvents.P_IS_CUSTOM to null,
            )
        )

        assertEquals(
            mapOf(
                AnalyticsEvents.P_ENABLED to true,
                AnalyticsEvents.P_SECONDS to 10,
                AnalyticsEvents.P_INTERVAL_S to 15L,
            ),
            result,
        )
    }

    @Test
    fun `drops values that are neither primitive nor string`() {
        val result = AnalyticsSanitizer.sanitize(
            mapOf(
                AnalyticsEvents.P_TARGET_PACKAGE to listOf("com.whatsapp"),
                AnalyticsEvents.P_MODE to mapOf("nested" to "value"),
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty input stays empty`() {
        assertTrue(AnalyticsSanitizer.sanitize(emptyMap()).isEmpty())
    }

    @Test
    fun `the allow-list contains no key that could carry notification content`() {
        val forbidden = setOf("sender", "sender_name", "title", "text", "message", "matchkey", "label")

        val leaks = AnalyticsEvents.PROPERTY_KEYS.filter { it.lowercase().replace("_", "") in forbidden }

        assertTrue("leaking keys: $leaks", leaks.isEmpty())
    }
}
