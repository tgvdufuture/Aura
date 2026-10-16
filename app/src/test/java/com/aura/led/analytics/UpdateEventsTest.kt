package com.aura.led.analytics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the update flow against the three mistakes that break it silently.
 *
 * These events are only ever read in the PostHog dashboard, where a wrong property shows up as a
 * series that is simply missing: nothing crashes and nobody is told. Three of those have already
 * happened once — some steps naming the release `0.6.1` and others `v0.6.1`, the download result
 * naming no release at all, and the star ask having no event. `MainViewModel` cannot be unit-tested
 * in this project (no Robolectric), so the call sites are pinned as text, the way
 * `StringResourceParityTest` pins the copy.
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
     * A download that fails on a specific release is the only signal that separates a broken asset
     * from broken networks; without the tag it cannot be attributed to anything.
     */
    @Test
    fun `every download result names the release it downloaded`() {
        val callSites = ui.readText().split("AnalyticsEvents.UPDATE_DOWNLOAD_RESULT").drop(1)

        assertTrue("no update_download_result call site found", callSites.isNotEmpty())
        for (callSite in callSites) {
            assertTrue(
                "an update_download_result without latest_tag cannot say which release failed: " +
                    callSite.take(80),
                callSite.take(220).contains("P_LATEST_TAG"),
            )
        }
    }

    /**
     * A failure the dashboard cannot explain is a failure nobody can fix. On 2026-10-16 the only
     * accepted update in the field failed at the download and the event said `result=error` and
     * nothing else — the cause was on one phone's logcat and nowhere else, so nothing could be done
     * about it before the next release. The failing call site now reports `reason`, and it has to be
     * the classified vocabulary (`DownloadFailure.reasonOf`, unit-tested in `DownloadFailureTest`)
     * rather than the exception's message: a message is free text, and free text is exactly what the
     * sanitizer cannot make safe.
     */
    @Test
    fun `a failed download reports why, from the fixed vocabulary`() {
        val callSites = ui.readText().split("AnalyticsEvents.UPDATE_DOWNLOAD_RESULT").drop(1)
        val failing = callSites.filter { it.take(400).contains("P_RESULT to \"error\"") }

        assertEquals("no failing update_download_result call site found", 1, failing.size)
        assertTrue(
            "the failing download reports no reason, so the next field failure is undiagnosable " +
                "again: " + failing.first().take(400),
            failing.first().take(900).contains("P_REASON to DownloadFailure.reasonOf(error)"),
        )
        assertFalse(
            "an exception message is free text (a package name, a URL) and must never be sent: " +
                "it is on one device's logcat for a reason",
            ui.readText().contains("to error.message"),
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
     * The owner's requirement behind the in-app updater: it must keep feeding the GitHub download
     * counter. That only holds while the URL handed to the downloader is the release asset's own
     * `browser_download_url`, fetched from the GitHub API — a mirror or a bundled copy would work
     * for the user and count nothing.
     */
    @Test
    fun `the in-app download still goes through the GitHub release asset`() {
        val checker = source("app/src/main/java/com/aura/led/update/UpdateChecker.kt").readText()

        assertTrue(
            "the APK URL must stay the asset's browser_download_url, or in-app updates stop " +
                "counting on GitHub",
            checker.contains("\"browser_download_url\""),
        )
        assertTrue(
            "the release must keep coming from the GitHub releases API",
            checker.contains("https://api.github.com/repos/"),
        )
    }

    /** Resolves a repository path from the module working directory Gradle uses for unit tests. */
    private fun source(path: String): File {
        val candidates = listOf(File(path), File("../$path"))

        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found (looked in ${candidates.joinToString()})")
    }
}
