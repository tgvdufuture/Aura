package com.aura.led.analytics

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A setup step observed before the user has agreed to statistics is not a step to throw away.
 *
 * `trackSetupOnce` wrote its per-install flag before calling `track`, which is a no-op until
 * consent exists. The first screen of a fresh install is the consent screen, and the
 * `MainViewModel` behind it already observes the notification listener, Shizuku and the battery
 * exemption, so those observations spent the flag and then sent nothing. Measured on the live
 * project on 2026-10-18: **14 users** had `setup_notification_access_granted` against 43 who
 * opened the app and 25 who granted the privilege — the funnel read as a mass abandonment of the
 * notification step that never happened.
 *
 * The fix is the one `onForeground` already carries (D-12): check that reporting is possible
 * *before* consuming the flag, so the next `StateFlow` replay (next launch) still reports the
 * step. `Analytics` cannot be unit-tested here (no Robolectric, `PostHog` is a static object), so
 * the order is pinned as text, the technique `AppOpenedSessionTest` uses for the same defect.
 */
class SetupEventsTest {

    private val analytics = source("app/src/main/java/com/aura/led/analytics/Analytics.kt").readText()

    @Test
    fun `a step observed before consent is not marked as reported`() {
        val trackSetupOnce = body("trackSetupOnce")
        val guard = trackSetupOnce.indexOf("!active")
        val read = trackSetupOnce.indexOf("isSetupFlagSet")
        val written = trackSetupOnce.indexOf("setSetupFlag")

        assertTrue("trackSetupOnce has no `!active` guard any more", guard >= 0)
        assertTrue("trackSetupOnce no longer reads the per-install flag", read >= 0)
        assertTrue("trackSetupOnce no longer writes the per-install flag", written >= 0)
        assertTrue(
            "trackSetupOnce spends the step's flag before checking whether reporting is possible: " +
                "every setup step observed on the consent screen is lost for good",
            guard < read && guard < written,
        )
    }

    @Test
    fun `a step that can be reported is still reported once per install`() {
        val trackSetupOnce = body("trackSetupOnce")

        assertTrue(
            "trackSetupOnce no longer sends anything",
            trackSetupOnce.contains("track(event"),
        )
        assertTrue(
            "trackSetupOnce no longer marks the step as sent: it would report it on every launch",
            trackSetupOnce.contains("setSetupFlag"),
        )
        assertTrue(
            "trackSetupOnce no longer reads the flag, so `once` is not enforced at all",
            trackSetupOnce.contains("isSetupFlagSet"),
        )
    }

    /** The body of a function in `Analytics.kt`, up to its closing brace at class-member indent. */
    private fun body(function: String): String {
        val start = analytics.indexOf("fun $function(")
        assertTrue("fun $function not found: the function or the file moved", start >= 0)

        val end = analytics.indexOf("\n    }", start)
        assertTrue("fun $function has no closing brace at member indent", end > start)

        return analytics.substring(start, end)
    }

    /** Resolves a repository path from the module working directory Gradle uses for unit tests. */
    private fun source(path: String): File {
        val candidates = listOf(File(path), File("../$path"))

        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found (looked in ${candidates.joinToString()})")
    }
}
