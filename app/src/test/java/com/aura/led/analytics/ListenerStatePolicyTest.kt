package com.aura.led.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `listener_state` reports a real failure — HyperOS unbinding the notification listener, which
 * silently disables every LED — and the app itself repairs that failure by asking for a rebind two
 * seconds after each disconnect. A system stuck in the loop would therefore emit a disconnect and
 * a reconnect every couple of seconds.
 *
 * The budget has to keep the pair that describes a real kill and drop the rest: that is what these
 * tests pin, rather than the number of events a run happens to produce.
 */
class ListenerStatePolicyTest {

    private var counters = ListenerStatePolicy.Counters()

    /** Mirrors the caller: ask the policy, then fold an allowed transition into the counters. */
    private fun report(nowMs: Long): Boolean {
        if (!ListenerStatePolicy.allows(nowMs, counters)) return false
        counters = ListenerStatePolicy.record(nowMs, counters)
        return true
    }

    @Test
    fun `the first transition of a process is reported`() {
        assertTrue("nothing is reported at all", report(1_000))
    }

    @Test
    fun `a disconnect and the rebind that follows it are both kept`() {
        assertTrue("the disconnect was dropped", report(10_000))
        assertTrue(
            "the rebind was dropped: the series would show a kill that never came back",
            report(12_000),
        )
    }

    @Test
    fun `a third transition inside the same minute is dropped`() {
        report(0)
        report(2_000)

        assertFalse("a flapping binding is not rate-limited any more", report(4_000))
    }

    @Test
    fun `the budget comes back once the window has passed`() {
        report(0)
        report(2_000)
        assertFalse(report(4_000))

        assertTrue("a later, real transition is dropped", report(61_000))
    }

    @Test
    fun `a process reports at most five transitions, however long it flaps`() {
        var reported = 0
        var now = 0L

        repeat(20) {
            if (report(now)) reported++
            now += ListenerStatePolicy.WINDOW_MS + 1
        }

        assertEquals(
            "the per-process cap is gone: a device that flaps for hours fills the project's storage",
            ListenerStatePolicy.MAX_PER_PROCESS,
            reported,
        )
    }
}
