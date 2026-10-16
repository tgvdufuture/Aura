package com.aura.led.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The LED test has to answer, and after three failures it has to say something about the *phone*.
 *
 * Found in the field data on 2026-10-16, not by a user report: a Redmi Note 9 (`merlin`, Xiaomi, so
 * the identity rule stays silent) ran the LED test **80 times, all 80 failed, and it never emitted a
 * single LED** — and the app said nothing at any point, because pressing the button answered with
 * nothing at all. Two other Xiaomi models did the same (6 of 6, and 1 of 8). The rule and its
 * threshold are unit-tested in `DeviceSupportTest`; what is pinned here is the wiring, which no JVM
 * test can execute: this project has no `compose-ui-test` and no instrumented source set, so the
 * call sites are read as text, the technique the rest of the update/analytics guards already use.
 */
class LedTestWarningTest {

    private val ui = source("app/src/main/java/com/aura/led/ui/Ui.kt").readText()
    private val activity = source("app/src/main/java/com/aura/led/MainActivity.kt").readText()

    /** The LED test's own flow, from the state that carries its result to the call site that records it. */
    private val testLed: String =
        ui.substringAfter("fun testLed()" ).substringBefore("// Latest requested preview")

    @Test
    fun `the card answers with what the test did`() {
        assertTrue(
            "the button answers with nothing again, which is how a phone gets asked eighty times",
            ui.contains("R.string.shizuku_test_ok") &&
                ui.contains("R.string.shizuku_test_failed") &&
                ui.contains("R.string.shizuku_test_unsupported"),
        )
        assertTrue(
            "the conclusive message has to come from the phone's own verdict, not from the tap count " +
                "of this session",
            ui.contains("test.conclusive"),
        )
        assertTrue(
            "a phone that lit up once is a phone that works: the success line has to be able to win",
            ui.indexOf("test.lastResult == true") < ui.indexOf("test.conclusive"),
        )
    }

    /** The standing band, which is what the user sees when they go back to the app list. */
    @Test
    fun `the app list takes the test verdict into the band`() {
        assertTrue(
            "the band is decided from the family alone again, so a Xiaomi phone whose ROM has no " +
                "service is told nothing",
            ui.contains("DeviceSupport.shouldWarn(Build.MANUFACTURER, Build.BRAND, ledTest.streak)"),
        )
        assertTrue(
            "the streak has to reach that call site: without the collected state the band never moves",
            ui.contains("val ledTest by viewModel.ledTest.collectAsState()"),
        )
        assertFalse(
            "the band must not be frozen in a `remember`: the failure it reports happens later, " +
                "after the composition is already running",
            Regex("remember\\s*\\{\\s*DeviceSupport\\.shouldWarn").containsMatchIn(ui),
        )
    }

    /**
     * The privilege check is what keeps the rule honest: an attempt made before Shizuku was granted
     * never reached the service, so counting it would tell a working phone it is broken. Three
     * failed attempts *with* the privilege are the phone's own answer.
     */
    @Test
    fun `only an attempt that ran is counted against the phone`() {
        assertTrue("the test flow was not found: the method moved", testLed.length > 200)
        assertTrue(
            "the privilege is not read before the attempt",
            testLed.contains("ShizukuManager.state.value.hasPermission || RootManager.state.value.available"),
        )
        assertTrue(
            "the streak must be recorded only under that privilege",
            testLed.contains("if (privileged)") && testLed.contains("LedTestStreak.record(getApplication(), success)"),
        )
        assertTrue(
            "the count has to survive the process: a user who closes the app on the message must not " +
                "have to fail three more times to see it again",
            ui.contains("LedTestStreak.failuresInARow(application)"),
        )
    }

    /**
     * The full-screen notice stays the identity rule's alone (PRD `device-compatibility`, D-14: a full
     * screen once, the band forever after). A test that fails is not a takeover trigger: the user is
     * already inside the app, looking at the card that just answered them.
     */
    @Test
    fun `a failed test does not take over the screen`() {
        assertTrue(
            "the full-screen notice is the family rule's call site and must stay that way",
            activity.contains(
                "DeviceSupport.shouldShowScreen(Build.MANUFACTURER, Build.BRAND, compatAcknowledged)",
            ),
        )
        assertFalse(
            "the LED test result reached the full-screen trigger, which would ambush a user who is " +
                "already looking at the card",
            activity.contains("ledTest"),
        )
    }

    /** Resolves a repository path from the module working directory Gradle uses for unit tests. */
    private fun source(path: String): File {
        val candidates = listOf(File(path), File("../$path"))

        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found (looked in ${candidates.joinToString()})")
    }
}
