package com.aura.led.analytics

/**
 * Keeps every analytics call site honest.
 *
 * Only property keys declared in [AnalyticsEvents.PROPERTY_KEYS] survive, and any string
 * longer than [MAX_STRING_LENGTH] is dropped outright. This is the mechanism that prevents
 * notification content, a sender name, a contact name or message text from leaving the
 * device even if a future call site passes one by mistake.
 *
 * Pure Kotlin (no Android dependency) so it is unit-testable.
 */
object AnalyticsSanitizer {
    const val MAX_STRING_LENGTH = 64

    fun sanitize(properties: Map<String, Any?>): Map<String, Any> =
        properties.entries
            .filter { (key, value) -> key in AnalyticsEvents.PROPERTY_KEYS && isAllowed(value) }
            .associate { (key, value) -> key to value!! }

    private fun isAllowed(value: Any?): Boolean = when (value) {
        null -> false
        is String -> value.length <= MAX_STRING_LENGTH
        is Boolean, is Int, is Long, is Float, is Double -> true
        else -> false
    }
}
