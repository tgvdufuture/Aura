package com.aura.led.analytics

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `app_opened` is the metric the whole dashboard is built on — active installs (G1), stickiness,
 * retention (G4) and the version spread are all reads on it — so it is the one event that must not
 * be silently dropped.
 *
 * It was, for the first session of every install. `MainActivity.onResume` opens the consent screen
 * *before* the user has agreed, so `onForeground` ran while analytics was off; it marked the session
 * as already reported anyway, and the session in which the user then consented reported nothing.
 * Measured on the live project (2026-10-16): 7 of the 27 users who had granted consent had sessions
 * full of events — one of them 66 app-rule toggles — and not a single `app_opened`.
 *
 * `Analytics` cannot be unit-tested here (no Robolectric, and `PostHog` is a static object), so the
 * two halves of the fix are pinned as text, the technique `UpdateEventsTest` and
 * `StringResourceParityTest` already use.
 */
class AppOpenedSessionTest {

    private val analytics = source("app/src/main/java/com/aura/led/analytics/Analytics.kt").readText()

    /**
     * A resume that happens while reporting is impossible must not spend the session's one event.
     * The guard has to come *before* the flag, which is exactly the order the defect had reversed.
     */
    @Test
    fun `a session that cannot report does not consume its app_opened`() {
        val onForeground = body("onForeground")
        val guard = onForeground.indexOf("!active")
        val consumed = onForeground.indexOf("foregroundTracked = true")

        assertTrue("onForeground has no `!active` guard any more", guard >= 0)
        assertTrue("onForeground never marks the session as reported", consumed >= 0)
        assertTrue(
            "onForeground marks the session as reported before checking whether reporting is " +
                "possible: the first session of an install — the consent screen — then reports no " +
                "app_opened at all, whatever the user does in it",
            guard < consumed,
        )
    }

    /**
     * The other half: consent is granted mid-session, and `onResume` will not fire again before the
     * user leaves. Without this call the fix above would only move the loss, not remove it.
     */
    @Test
    fun `granting consent reports the session it happens in`() {
        val grantConsent = body("grantConsent")

        assertTrue(
            "grantConsent does not call onForeground, so the session that granted consent never " +
                "reports app_opened",
            grantConsent.contains("onForeground("),
        )
        assertTrue(
            "grantConsent must report only after the SDK exists",
            grantConsent.indexOf("init(context)") < grantConsent.indexOf("onForeground("),
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
