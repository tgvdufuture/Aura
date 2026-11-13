package com.aura.led.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GitHub discussion #1 (2026-11-12): two users on HyperOS 3.0.310.0 report the ring flashing once and
 * never repeating, with every setting checked. The reminder used to skip every tick whenever the
 * screen was interactive, and a lock screen — or the Always-On Display that keeps it interactive —
 * made the phone look "in use" forever.
 */
class ReminderGateTest {

    @Test
    fun `a locked screen with the display on is not the user using the phone`() {
        assertFalse(
            "the reminder goes dark while the phone sits locked with the display on: a lock screen " +
                "(or Always-On Display) must not silence it",
            ReminderGate.userIsActive(screenOn = true, locked = true),
        )
    }

    @Test
    fun `an unlocked, awake phone is in use`() {
        assertTrue(ReminderGate.userIsActive(screenOn = true, locked = false))
    }

    @Test
    fun `a dark screen is never in use`() {
        assertFalse(ReminderGate.userIsActive(screenOn = false, locked = false))
        assertFalse(ReminderGate.userIsActive(screenOn = false, locked = true))
    }
}
