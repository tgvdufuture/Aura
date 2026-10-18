package com.aura.led.analytics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two things have to stay wired, or the listener half of the dashboard silently lies again:
 *
 * - `app_opened` must carry `listener_enabled` beside `listener_connected`. Without it, a `false`
 *   at resume cannot be told apart from "the user never granted notification access" — and at
 *   resume the binding is usually still being established, since the app asks for a rebind on
 *   every process start. Measured on 2026-10-18: 187 of 354 `app_opened` had `false`, which was
 *   read as evidence HyperOS had killed the listener long before this key existed.
 * - Both listener callbacks must report the transition, and the emitter must consult
 *   `ListenerStatePolicy` first. The policy has its own unit test; what is pinned here is that the
 *   throttle is actually in the path and that neither callback can lose its call.
 *
 * `Analytics` and the listener need a device (they extend Android types and `PostHog` is a static
 * object), so the wiring is pinned as text, the technique `AppOpenedSessionTest` uses.
 */
class ListenerStateEventsTest {

    private val analytics = source("app/src/main/java/com/aura/led/analytics/Analytics.kt").readText()
    private val listener =
        source("app/src/main/java/com/aura/led/notification/AuraNotificationListener.kt").readText()

    @Test
    fun `app_opened reports both whether access exists and whether the listener is bound`() {
        val onForeground = body(analytics, "onForeground")

        assertTrue(
            "app_opened no longer carries listener_connected",
            onForeground.contains("P_LISTENER_CONNECTED"),
        )
        assertTrue(
            "app_opened no longer says whether notification access was granted: a false " +
                "listener_connected becomes unreadable again",
            onForeground.contains("P_LISTENER_ENABLED"),
        )
    }

    @Test
    fun `both listener callbacks report the transition`() {
        assertEquals(
            "a listener callback lost its listener_state call: half the transitions disappear",
            2,
            Regex("Analytics\\.listenerStateChanged\\(this, connected = (true|false)\\)")
                .findAll(listener)
                .count(),
        )
        assertTrue(
            "the connected callback never reports",
            listener.contains("Analytics.listenerStateChanged(this, connected = true)"),
        )
        assertTrue(
            "the disconnected callback never reports: the failure this event exists for is invisible",
            listener.contains("Analytics.listenerStateChanged(this, connected = false)"),
        )
    }

    @Test
    fun `the event is rate-limited before it is sent`() {
        val emitter = body(analytics, "listenerStateChanged")
        val policy = emitter.indexOf("ListenerStatePolicy.allows")
        val track = emitter.indexOf("track(")

        assertTrue("listener_state is no longer rate-limited", policy >= 0)
        assertTrue("listener_state is no longer sent at all", track >= 0)
        assertTrue(
            "the event is sent before the budget is checked: a flapping binding floods the project",
            policy < track,
        )
        assertTrue(
            "the allowed transition is never folded into the counters, so the cap never applies",
            emitter.contains("ListenerStatePolicy.record"),
        )
    }

    /** The body of a function, up to its closing brace at member indent. */
    private fun body(source: String, function: String): String {
        val start = source.indexOf("fun $function(")
        assertTrue("fun $function not found: the function or the file moved", start >= 0)

        val end = source.indexOf("\n    }", start)
        assertTrue("fun $function has no closing brace at member indent", end > start)

        return source.substring(start, end)
    }

    /** Resolves a repository path from the module working directory Gradle uses for unit tests. */
    private fun source(path: String): File {
        val candidates = listOf(File(path), File("../$path"))

        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found (looked in ${candidates.joinToString()})")
    }
}
