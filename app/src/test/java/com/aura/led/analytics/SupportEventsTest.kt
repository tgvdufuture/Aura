package com.aura.led.analytics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The support funnel is the only part of the dashboard that is about money, and it has exactly two
 * readings: how many users reached the donation page, and — since 2026-10-16 — from where.
 *
 * Two edits make those readings possible, and both fail *silently* when forgotten: an event that
 * drops `source` still compiles and still arrives, it just merges the support card and the answer
 * chip into one row (they were merged, on the first two days of data: 7 users opened the page from
 * two placements and nothing could tell them apart). A donation URL without its tags leaves the app
 * working and every donation unattributable for good, because Ko-fi only ever sees the URL.
 *
 * `MainViewModel` has no automated test in this project (no Robolectric), so the call sites are read
 * as text — the technique `UpdateEventsTest` already uses for the same reason.
 */
class SupportEventsTest {

    private val ui = source("app/src/main/java/com/aura/led/ui/Ui.kt").readText()
    private val guide = source("docs/analytics-dashboard.md").readText()

    /** Each `support_clicked` call site, read as the text that follows the event name. */
    private val callSites: List<String> =
        ui.split("AnalyticsEvents.SUPPORT_CLICKED").drop(1)

    @Test
    fun `every donation click says which placement it came from`() {
        assertTrue("no support_clicked call site found: the file or the event name moved", callSites.isNotEmpty())

        for (callSite in callSites) {
            assertTrue(
                "a support_clicked without `source` merges the support card and the answer chip " +
                    "into one row, which is the only thing this property exists to prevent: " +
                    callSite.take(80),
                callSite.take(260).contains("P_SOURCE"),
            )
        }
    }

    /**
     * Three placements lead to the donation page, and the whole point of `source` is that they stay
     * apart: the app-list banner (the only one that reaches users who never open Settings), the
     * support card in Settings, and the `donation` answer chip.
     */
    @Test
    fun `each placement reports its own source`() {
        assertTrue(
            "the app-list banner must report SOURCE_INVITE",
            ui.contains("openDonationPage(AnalyticsEvents.SOURCE_INVITE)"),
        )
        assertTrue(
            "the support card's donate button must report SOURCE_CARD",
            ui.contains("openDonationPage(AnalyticsEvents.SOURCE_CARD)"),
        )
        assertTrue(
            "the `donation` answer must report SOURCE_ANSWER",
            ui.contains("openDonationPage(AnalyticsEvents.SOURCE_ANSWER)"),
        )

        val sources = listOf(
            AnalyticsEvents.SOURCE_INVITE,
            AnalyticsEvents.SOURCE_CARD,
            AnalyticsEvents.SOURCE_ANSWER,
        )
        assertEquals(
            "two placements sharing one source value makes the breakdown lie",
            sources.size,
            sources.distinct().size,
        )
    }

    /**
     * `channel = kofi` is what the existing insight filters on, so the new property must be additive:
     * both keys have to survive the sanitizer *and* the SDK-context allow-list, or one of them
     * disappears from the payload with no error anywhere.
     */
    @Test
    fun `the source and the channel both survive the two allow-lists`() {
        val reported = AnalyticsSanitizer.sanitize(
            mapOf(
                AnalyticsEvents.P_CHANNEL to AnalyticsEvents.CHANNEL_KOFI,
                AnalyticsEvents.P_SOURCE to AnalyticsEvents.SOURCE_CARD,
            ),
        )

        assertEquals(
            mapOf("channel" to "kofi", "source" to "card"),
            reported,
        )
        assertTrue(
            "AnalyticsEventFilter would drop `source` from the merged payload",
            AnalyticsEvents.P_SOURCE in AnalyticsEventFilter.ALLOWED_KEYS,
        )
        assertTrue(
            "a source longer than the sanitizer's cap would be dropped outright",
            AnalyticsEvents.SOURCE_CARD.length <= AnalyticsSanitizer.MAX_STRING_LENGTH &&
                AnalyticsEvents.SOURCE_ANSWER.length <= AnalyticsSanitizer.MAX_STRING_LENGTH,
        )
    }

    /**
     * The app cannot see a donation — the user leaves for a web page — so the URL is the only carrier
     * that can attribute one, and it has to name the app, the flow and the placement.
     */
    @Test
    fun `the donation URL carries the placement, or no donation is attributable`() {
        assertTrue(
            "the Ko-fi URL has no utm_source, so Ko-fi cannot tell a donation came from Aura",
            ui.contains("utm_source=aura_app"),
        )
        assertTrue("the Ko-fi URL has no utm_campaign", ui.contains("utm_campaign=donation"))
        assertTrue(
            "the placement must travel in utm_content, with the same value as the event's source",
            ui.contains("utm_content=\$source"),
        )
        assertTrue(
            "opening the page must use the tagged URL, not the bare one",
            ui.contains("Uri.parse(donationUrl(source))"),
        )
    }

    /** An insight nobody reads costs what an event nobody sends costs (see `DashboardGuideTest`). */
    @Test
    fun `the guide reads the placement, and says where a donation shows up`() {
        assertTrue(
            "the guide's support row must break support_clicked down by source",
            guide.contains("`support_clicked` broken down by `source`"),
        )
        assertTrue(
            "the guide must say that a donation is only ever visible in Ko-fi, never in PostHog",
            guide.contains("utm_content"),
        )
    }

    /** Resolves a repository path from the module working directory Gradle uses for unit tests. */
    private fun source(path: String): File {
        val candidates = listOf(File(path), File("../$path"))

        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found (looked in ${candidates.joinToString()})")
    }
}
