package com.aura.led.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The autostart switch cannot be read, so what Aura reports is a deduction from boot instants.
 * These tests are what keeps that deduction from turning into a confident wrong answer: every case
 * where the evidence is missing expects [AutostartWatch.State.UNKNOWN], never a verdict.
 *
 * The two instants are not the same signal, and the device proved it twice (2026-10-14): with
 * autostart off, no broadcast arrives at boot, but one *is* delivered once the user opens the app by
 * hand — 23 ms before that activity's `onCreate`, i.e. inside the process the launch just created. A
 * rule that counted any received broadcast reported "autostart works" with the switch off, and so
 * did a rule that only compared the delivery boot with the user's open. Both are the one thing this
 * must never say.
 */
class AutostartWatchTest {

    private val boot = 1_760_000_000_000L
    private val aDay = 86_400_000L

    @Test
    fun `a boot broadcast for the running boot proves the switch works`() {
        val state = AutostartWatch.state(
            currentBootMs = boot,
            promptDeliveryBootMs = boot,
            lastSeenBootMs = boot,
        )

        assertEquals(AutostartWatch.State.ENABLED, state)
    }

    @Test
    fun `a recorded boot that is not the running one means HyperOS blocked the restart`() {
        // Aura recorded one boot, the phone rebooted, and no broadcast arrived for the new boot.
        val state = AutostartWatch.state(
            currentBootMs = boot + aDay,
            promptDeliveryBootMs = boot,
            lastSeenBootMs = boot,
        )

        assertEquals(AutostartWatch.State.BLOCKED, state)
    }

    @Test
    fun `a fresh install has nothing to compare, so it claims nothing`() {
        val neverRecorded = AutostartWatch.state(
            currentBootMs = boot,
            promptDeliveryBootMs = 0L,
            lastSeenBootMs = 0L,
        )
        // Aura recorded this very boot and no broadcast came: it has just started, which says
        // nothing about a reboot that has not happened.
        val recordedThisBoot = AutostartWatch.state(
            currentBootMs = boot,
            promptDeliveryBootMs = 0L,
            lastSeenBootMs = boot,
        )

        assertEquals(AutostartWatch.State.UNKNOWN, neverRecorded)
        assertEquals(AutostartWatch.State.UNKNOWN, recordedThisBoot)
    }

    @Test
    fun `a boot a minute after the recorded one is still the same boot`() {
        // The tolerance is what stops a clock adjustment from being read as a reboot.
        val state = AutostartWatch.state(
            currentBootMs = boot + 60_000L,
            promptDeliveryBootMs = 0L,
            lastSeenBootMs = boot,
        )

        assertEquals(AutostartWatch.State.UNKNOWN, state)
    }

    @Test
    fun `two readings of one boot stay within the tolerance`() {
        assertTrue(AutostartWatch.isSameBoot(boot, boot))
        assertTrue(AutostartWatch.isSameBoot(boot, boot + AutostartWatch.SAME_BOOT_TOLERANCE_MS))
        assertTrue(AutostartWatch.isSameBoot(boot + AutostartWatch.SAME_BOOT_TOLERANCE_MS, boot))
        assertFalse(AutostartWatch.isSameBoot(boot, boot + AutostartWatch.SAME_BOOT_TOLERANCE_MS + 1))
    }

    @Test
    fun `a clock that moved backwards is not blamed on HyperOS`() {
        // The wall clock was set back, or the user travelled: the running boot looks older than the
        // one recorded. That cannot prove the switch is off, so it must not be reported as blocked.
        val state = AutostartWatch.state(
            currentBootMs = boot - 3_600_000L,
            promptDeliveryBootMs = 0L,
            lastSeenBootMs = boot,
        )

        assertEquals(AutostartWatch.State.UNKNOWN, state)
    }

    @Test
    fun `a missing history is never mistaken for the same boot`() {
        // 0 == 0 would otherwise read as "the same boot", and every fresh install would claim the
        // switch works.
        assertFalse(AutostartWatch.isSameBoot(0L, 0L))
        assertEquals(
            AutostartWatch.State.UNKNOWN,
            AutostartWatch.state(currentBootMs = 0L, promptDeliveryBootMs = 0L, lastSeenBootMs = 0L),
        )
    }

    /**
     * The case that was measured wrong on the owner's phone before this rule existed: the broadcast
     * was finally delivered when the app was opened by hand, inside the process that launch created,
     * so the user's open is not yet recorded when the receiver runs.
     * [AutostartWatch.isCarriedByTheLaunch] is what undoes it.
     */
    @Test
    fun `a carried delivery leaves the boot reading blocked, not enabled`() {
        val bootTheUserOpened = boot + aDay

        assertEquals(
            AutostartWatch.State.BLOCKED,
            AutostartWatch.state(
                currentBootMs = bootTheUserOpened,
                promptDeliveryBootMs = 0L,
                lastSeenBootMs = boot,
            ),
        )
    }

    /**
     * A delivery that arrives after the app has already been opened in the same process proves
     * nothing: the app was running because the user brought it up.
     *
     * This used to be decided by comparing boot instants, and the device falsified that: a delivery
     * was rejected as "after the user opened the app" because the *previous* boot was only 3.5 min
     * earlier and fell inside [AutostartWatch.SAME_BOOT_TOLERANCE_MS] — while Aura had in fact just
     * started itself. Both instants now come from one process, so no boot identity is involved.
     */
    @Test
    fun `a delivery after the app was already open is not proof of anything`() {
        assertTrue(AutostartWatch.isLateDelivery(openedUptimeMs = 40_000L, deliveryUptimeMs = 40_023L))
        assertTrue(AutostartWatch.isLateDelivery(openedUptimeMs = 40_000L, deliveryUptimeMs = 40_000L))

        // Delivered before the app was opened in this process: the device's self-start case.
        assertFalse(AutostartWatch.isLateDelivery(openedUptimeMs = 0L, deliveryUptimeMs = 40_023L))
        assertFalse(AutostartWatch.isLateDelivery(openedUptimeMs = 40_100L, deliveryUptimeMs = 40_023L))
    }

    /**
     * Measured on the device: the receiver ran 23 ms before the activity's `onCreate`, in the same
     * process. Without this, a user whose autostart is off reads "it worked" the moment they open
     * the app after a reboot they never noticed.
     */
    @Test
    fun `a delivery landing in the launch that carried it is undone`() {
        val bootUptime = 5_000L

        // The device's sequence: delivery at 40 000 ms, activity at 40 023 ms.
        assertTrue(AutostartWatch.isCarriedByTheLaunch(deliveryUptimeMs = 40_000L, firstOpenUptimeMs = 40_023L))
        assertTrue(
            AutostartWatch.isCarriedByTheLaunch(
                deliveryUptimeMs = 40_000L,
                firstOpenUptimeMs = 40_000L + AutostartWatch.LAUNCH_WINDOW_MS,
            ),
        )

        // A delivery at boot and an app opened minutes later is a real self-start, not a carried one.
        assertFalse(
            AutostartWatch.isCarriedByTheLaunch(
                deliveryUptimeMs = bootUptime,
                firstOpenUptimeMs = bootUptime + AutostartWatch.LAUNCH_WINDOW_MS + 1,
            ),
        )
        // Nothing was delivered in this process, and a delivery later than the open was never
        // recorded anyway.
        assertFalse(AutostartWatch.isCarriedByTheLaunch(deliveryUptimeMs = 0L, firstOpenUptimeMs = 40_023L))
        assertFalse(AutostartWatch.isCarriedByTheLaunch(deliveryUptimeMs = 40_100L, firstOpenUptimeMs = 40_023L))
    }

    @Test
    fun `the carried delivery leaves the boot reading blocked, not enabled`() {
        // What the device showed once the carried delivery is undone: the app was opened by hand in
        // a boot where nothing came back on its own, and the previous boot is on record.
        val bootTheUserOpened = boot + aDay

        assertEquals(
            AutostartWatch.State.BLOCKED,
            AutostartWatch.state(
                currentBootMs = bootTheUserOpened,
                promptDeliveryBootMs = 0L,
                lastSeenBootMs = boot,
            ),
        )
    }

    @Test
    fun `the boot instant is the wall clock minus the time since boot`() {
        assertEquals(
            1_000L,
            AutostartWatch.bootInstantMs(nowMs = 6_000L, elapsedRealtimeMs = 5_000L),
        )
    }
}
