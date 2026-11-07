package com.aura.led.notification

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reminder loop already skipped its ticks inside quiet hours, but the arrival flash and the
 * removal fallback did not: a notification at 02:00 still lit the ring, which is why the setting
 * read as broken (user report, 2026-11-06). This project has no instrumented source set, so the
 * ordering is read from the listener itself.
 */
class QuietHoursWiringTest {

    @Test
    fun `the arrival light is gated before the notification is tracked or emitted`() {
        val source = listenerSource().readText()
        val gate = source.indexOf("config.silencesAllLightsAt(nowMinutes())")
        val tracked = source.indexOf("recordActive(ActiveNotification(sbn.key, command))")

        assertTrue("the arrival flash is not gated by quiet hours", gate >= 0)
        assertTrue("a silenced notification must not be tracked for the reminder loop", tracked > gate)
    }

    @Test
    fun `falling back to an older notification cannot relight the ring during quiet hours`() {
        val source = listenerSource().readText()
        val fallback = source.substringAfter("current notification removed -> falling back to previous")

        assertTrue(
            "the removal fallback emits without asking quiet hours",
            fallback.contains("silencesAllLightsAt(nowMinutes())"),
        )
    }

    private fun listenerSource(): File {
        val path = "app/src/main/java/com/aura/led/notification/AuraNotificationListener.kt"
        return listOf(File(path), File("../$path"))
            .firstOrNull { it.isFile }
            ?: error("$path not found")
    }
}
