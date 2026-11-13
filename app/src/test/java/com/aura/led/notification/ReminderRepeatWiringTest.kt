package com.aura.led.notification

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule is only a fix if both call sites use it. The arrival flash answered "screen on and
 * unlocked"; the reminder answered "screen on", and that difference is what left the ring dark after
 * the first flash on a phone whose lock screen stays interactive.
 */
class ReminderRepeatWiringTest {

    private val listener = listenerSource().readText()

    @Test
    fun `the reminder tick uses the same gate as the arrival light`() {
        assertTrue(
            "the reminder no longer asks the shared rule",
            listener.contains("ReminderGate.userIsActive(screenOn, locked) || isSuppressedAtNight()"),
        )
        assertFalse(
            "the reminder is gated on the screen alone again: every tick is skipped while the phone " +
                "sits locked with the display on",
            listener.contains("if (isScreenOn() || isSuppressedAtNight())"),
        )
    }

    @Test
    fun `the arrival light goes through the shared rule too`() {
        assertTrue(
            "the arrival light keeps its own copy of the rule",
            listener.contains("if (ReminderGate.userIsActive(screenOn, locked)) return"),
        )
    }

    private fun listenerSource(): File {
        val path = "app/src/main/java/com/aura/led/notification/AuraNotificationListener.kt"
        return listOf(File(path), File("../$path"))
            .firstOrNull { it.isFile }
            ?: error("$path not found")
    }
}
