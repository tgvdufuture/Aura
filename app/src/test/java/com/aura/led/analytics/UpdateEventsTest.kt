package com.aura.led.analytics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the update flow against the mistakes that break it silently.
 *
 * These events are only ever read in the PostHog dashboard, where a wrong property shows up as a
 * series that is simply missing: nothing crashes and nobody is told. One of those has already
 * happened once — some steps naming the release `0.6.1` and others `v0.6.1`. `MainViewModel` cannot
 * be unit-tested in this project (no Robolectric), so the call sites are pinned as text, the way
 * `StringResourceParityTest` pins the copy.
 *
 * The download and install events are gone with the in-app installer: the update action opens the
 * release page, so nothing in Aura can observe what the browser does next.
 */
class UpdateEventsTest {

    private val ui = source("app/src/main/java/com/aura/led/ui/Ui.kt")

    /** Every `P_LATEST_TAG to <x>` in the update flow, `<x>` read as a property path. */
    private val reportedTags: List<String> =
        Regex("AnalyticsEvents\\.P_LATEST_TAG to ([A-Za-z_][A-Za-z0-9_.]*)")
            .findAll(ui.readText())
            .map { it.groupValues[1] }
            .toList()

    /**
     * A `latest_tag` that reads `0.6.1` on one step and `v0.6.1` on the next splits a single release
     * into two rows in the conversion funnel, which is exactly how the tag is meant to be read.
     */
    @Test
    fun `the update flow reports the release tag, never the version name`() {
        assertTrue("no latest_tag call site found: the file or the event name moved", reportedTags.isNotEmpty())

        val wrong = reportedTags.filterNot { it == "pendingTag" || it.endsWith(".tag") }

        assertEquals(
            "these call sites name the release with something other than its GitHub tag, so one " +
                "release becomes two series in the funnel",
            emptyList<String>(),
            wrong,
        )
    }

    /**
     * The star ask is the only thing in the app that can move the repository's star count, so the
     * event has to survive the sanitizer and be read in the dashboard — otherwise it is sent and
     * never looked at.
     */
    @Test
    fun `opening the repository reports a github channel the dashboard reads`() {
        val reported = AnalyticsSanitizer.sanitize(
            mapOf(AnalyticsEvents.P_CHANNEL to AnalyticsEvents.CHANNEL_GITHUB)
        )

        assertEquals("star_clicked", AnalyticsEvents.STAR_CLICKED)
        assertEquals(mapOf("channel" to "github"), reported)
        assertTrue(
            "docs/analytics-dashboard.md has no insight reading star_clicked",
            source("docs/analytics-dashboard.md").readText().contains(AnalyticsEvents.STAR_CLICKED),
        )
    }

    /**
     * The notice is only as good as the check behind it: it has to keep asking the project's own
     * releases API and reading `tag_name`, or a release either goes unnoticed or is announced twice.
     */
    @Test
    fun `the update check reads the GitHub releases API`() {
        val checker = source("app/src/main/java/com/aura/led/update/UpdateChecker.kt").readText()

        assertTrue(
            "the release must keep coming from the GitHub releases API",
            checker.contains("https://api.github.com/repos/"),
        )
        assertTrue(
            "the check must keep reading tag_name, or it cannot tell a newer version from the same one",
            checker.contains("\"tag_name\""),
        )
    }

    /** Resolves a repository path from the module working directory Gradle uses for unit tests. */
    private fun source(path: String): File {
        val candidates = listOf(File(path), File("../$path"))

        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found (looked in ${candidates.joinToString()})")
    }
}
