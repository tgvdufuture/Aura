package com.aura.led.analytics

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three events a drag produces are read as decisions, so they must be sent when the gesture
 * ends, not while it happens.
 *
 * They were sent per step. Measured on the live project on 2026-10-18: `led_duration_changed` 531
 * events for 9 users (91 inside one minute, 118 for a single user), `reminder_interval_changed` 520
 * for 7 users (328 inside one minute) and `app_rule_previewed` 539 for 27 users (101 for one user
 * in one day). The breakdown by `seconds` and `interval_s` therefore described the sliders'
 * resolution, not what anybody chose — the PRD asks for "at most one event per committed value"
 * (F2.4, edge cases).
 *
 * The ViewModel cannot be built in a unit test (it needs a Context, a Room database and the
 * singleton LED controller), so the two halves of the fix are pinned as text, the technique
 * `AppOpenedSessionTest` and `UpdateEventsTest` already use.
 */
class SliderEventsTest {

    private val ui = source("app/src/main/java/com/aura/led/ui/Ui.kt").readText()

    /** The two sliders apply every step, and report on the one callback that ends the gesture. */
    @Test
    fun `both sliders report only when the gesture ends`() {
        assertTrue(
            "the LED timeout slider no longer reports on onValueChangeFinished: it is back to one " +
                "event per step",
            ui.contains("onValueChangeFinished = onTimeoutCommit"),
        )
        assertTrue(
            "the reminder interval slider no longer reports on onValueChangeFinished: it is back " +
                "to one event per step",
            ui.contains("onValueChangeFinished = onIntervalCommit"),
        )
        assertTrue(
            "the review screen does not pass the commit callback of the LED timeout slider",
            ui.contains("onTimeoutCommit = viewModel::commitLedTimeout"),
        )
        assertTrue(
            "the review screen does not pass the commit callback of the reminder interval slider",
            ui.contains("onIntervalCommit = viewModel::commitReminderInterval"),
        )
    }

    /** The value has to keep reaching the state per step, or the label and the preview freeze. */
    @Test
    fun `the thumb still follows every step`() {
        val sliders = countOf("onValueChange = {")

        assertTrue(
            "a slider lost its per-step onValueChange: the value it shows would only move at the " +
                "end of the drag",
            sliders >= 2,
        )
    }

    /**
     * A step crossed during a drag must not be reported. The guard is that the setter has no
     * analytics call at all — an event added back there would fire dozens of times per gesture.
     */
    @Test
    fun `moving the slider reports nothing, only the committed value does`() {
        assertFalse(
            "setLedTimeout reports an event: it runs for every step of the drag",
            member("setLedTimeout").contains("Analytics.track"),
        )
        assertFalse(
            "setReminderInterval reports an event: it runs for every step of the drag",
            member("setReminderInterval").contains("Analytics.track"),
        )
    }

    /** And the commit reports the value that was kept, once. */
    @Test
    fun `the committed value is the one that gets reported`() {
        val timeout = member("commitLedTimeout")

        assertTrue(
            "commitLedTimeout no longer sends led_duration_changed",
            timeout.contains("AnalyticsEvents.LED_DURATION_CHANGED"),
        )
        assertTrue(
            "commitLedTimeout must report the applied value, not an argument of its own",
            timeout.contains("_ledSettings.value.timeoutMs"),
        )

        val interval = member("commitReminderInterval")

        assertTrue(
            "commitReminderInterval no longer sends reminder_interval_changed",
            interval.contains("AnalyticsEvents.REMINDER_INTERVAL_CHANGED"),
        )
        assertTrue(
            "commitReminderInterval must report the applied value, not an argument of its own",
            interval.contains("_reminder.value.intervalMs"),
        )
    }

    /**
     * The colour picker previews on every step of the HSV drag, so its event follows the settle the
     * LED already waits for: one per colour the user stopped on.
     */
    @Test
    fun `a colour preview reports the settle, not the drag`() {
        assertFalse(
            "previewLed tracks again: every step of the picker drag becomes an event",
            member("previewLed").contains("trackPreview"),
        )

        val worker = previewWorker()
        val settle = worker.indexOf("delay(SETTLE_DEBOUNCE_MS)")
        val guard = worker.indexOf("previewRequests.value == request")
        val tracked = worker.indexOf("trackPreview(request.color)")

        assertTrue("the preview worker no longer debounces the settle", settle >= 0)
        assertTrue("the preview worker no longer checks the request is still current", guard >= 0)
        assertTrue("no app_rule_previewed is sent from the settle any more", tracked >= 0)
        assertTrue(
            "app_rule_previewed is sent before the drag settles: it is one event per step again",
            settle < tracked && guard < tracked,
        )
    }

    /** The body of a member function of `MainViewModel`, up to its closing brace at member indent. */
    private fun member(name: String): String {
        val start = ui.indexOf("fun $name(")
        assertTrue("fun $name not found: the function or the file moved", start >= 0)

        val end = ui.indexOf("\n    }", start)
        assertTrue("fun $name has no closing brace at member indent", end > start)

        return ui.substring(start, end)
    }

    /** The preview worker that applies and settles a requested colour. */
    private fun previewWorker(): String {
        val start = ui.indexOf("previewRequests.collectLatest")
        assertTrue("the preview worker not found: the block or the file moved", start >= 0)

        val end = ui.indexOf("private companion object", start)
        assertTrue("the preview worker has no end marker", end > start)

        return ui.substring(start, end)
    }

    private fun countOf(needle: String): Int = Regex(Regex.escape(needle)).findAll(ui).count()

    /** Resolves a repository path from the module working directory Gradle uses for unit tests. */
    private fun source(path: String): File {
        val candidates = listOf(File(path), File("../$path"))

        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found (looked in ${candidates.joinToString()})")
    }
}
