package com.aura.led.analytics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every event the app can send has to be read by an insight in `docs/analytics-dashboard.md`.
 *
 * An event nobody reads costs exactly what an event nobody sends costs: it leaves the phone, it is
 * stored, and it changes no decision. Thirteen had accumulated that way — the whole update half of
 * the dashboard among them — because adding an event and adding its insight are two edits in two
 * files, and only the first one fails to compile when it is forgotten.
 *
 * The guide is the only place these events are read, so it is the only place that can be checked
 * here. `docs/analytics-dashboard.md` is a repository file, like the locale files
 * `StringResourceParityTest` reads.
 */
class DashboardGuideTest {

    private val catalogue = source("app/src/main/java/com/aura/led/analytics/AnalyticsEvents.kt").readText()

    /**
     * The event names the app can send, read from the catalogue.
     *
     * Two rules keep the property keys and the reused values out: a property key is prefixed `P_`,
     * and no event name is a single word — `shizuku`, `root`, `kofi`, `github`, `app`, `contact` and
     * `group` are property *values*.
     */
    private val events: Set<String> =
        Regex("const val ([A-Z][A-Z0-9_]*) = \"([a-z_]+)\"")
            .findAll(catalogue)
            .map { it.groupValues[1] to it.groupValues[2] }
            .filterNot { (name, _) -> name.startsWith("P_") }
            .filter { (_, value) -> value.contains('_') }
            .map { (_, value) -> value }
            .toSet()

    /** Guards the guard: a rule that reads nothing, or reads a property as an event, proves nothing. */
    @Test
    fun `the extraction rule finds events, and neither properties nor values`() {
        assertTrue("nothing was read out of the catalogue: the rule or the file moved", events.size > 30)
        assertTrue(events.contains("app_opened"))
        assertTrue(events.contains("star_clicked"))
        assertFalse("a property key was read as an event", events.contains("interval_s"))
        assertFalse("a reused value was read as an event", events.contains("kofi"))
    }

    @Test
    fun `every event the app can send is read by an insight`() {
        val guide = source("docs/analytics-dashboard.md").readText()

        val unread = events.filterNot { guide.contains(it) }.sorted()

        assertEquals(
            "these events leave the device and no insight in the guide reads them: add the insight " +
                "that reads them, or do not send them",
            emptyList<String>(),
            unread,
        )
    }

    /** Resolves a repository path from the module working directory Gradle uses for unit tests. */
    private fun source(path: String): File {
        val candidates = listOf(File(path), File("../$path"))

        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found (looked in ${candidates.joinToString()})")
    }
}
