package com.aura.led.notification

import com.aura.led.led.LedCommand
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The removal fallback used to replay the command stored when a notification was posted, which is
 * how an app colour edited since then could light again (report 2026-11-11: a yellow that came back
 * after the app was set to green). The light is now resolved against the status bar; the stored
 * command is only a last resort when that read fails.
 */
class FallbackLightTest {

    /** What the rules said when the notification was posted, and what they say now. */
    private val storedYellow = LedCommand("#ffcc00", null)
    private val greenNow = LedCommand("#0aff10", null)

    @Test
    fun `the rules as they are now win over the command stored at post time`() {
        assertEquals(
            greenNow,
            FallbackLight.command(live = greenNow, statusBarReadable = true, stored = storedYellow),
        )
    }

    @Test
    fun `an unreadable status bar keeps the stored command`() {
        // No other source of truth: replaying the stored light beats leaving the ring dark.
        assertEquals(
            storedYellow,
            FallbackLight.command(live = null, statusBarReadable = false, stored = storedYellow),
        )
    }

    @Test
    fun `a notification gone from the shade is not lit again`() {
        assertNull(
            FallbackLight.command(live = null, statusBarReadable = true, stored = storedYellow),
        )
    }

    @Test
    fun `the fallback asks the status bar instead of replaying the stored command`() {
        val fallback = listenerSource().readText()
            .substringAfter("current notification removed -> falling back to previous")

        assertFalse(
            "the fallback still replays the command captured at post time",
            fallback.contains("emit(next.command)"),
        )
        assertTrue(
            "the fallback does not go through the status-bar decision",
            fallback.contains("FallbackLight.command("),
        )
    }

    private fun listenerSource(): File {
        val path = "app/src/main/java/com/aura/led/notification/AuraNotificationListener.kt"
        return listOf(File(path), File("../$path"))
            .firstOrNull { it.isFile }
            ?: error("$path not found")
    }
}
