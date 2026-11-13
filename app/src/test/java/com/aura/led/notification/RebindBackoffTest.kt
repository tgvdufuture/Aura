package com.aura.led.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #4: the reconnect path asked for a rebind every two seconds, forever, on a phone whose
 * listener connects and drops again. This pins the numbers the listener now waits, so a "fix" that
 * quietly reintroduces a fixed delay fails here.
 */
class RebindBackoffTest {

    @Test
    fun `an isolated drop still waits the two seconds it always did`() {
        assertEquals(2_000L, RebindBackoff.delayMs(0))
    }

    @Test
    fun `each further attempt doubles the wait`() {
        assertEquals(4_000L, RebindBackoff.delayMs(1))
        assertEquals(8_000L, RebindBackoff.delayMs(2))
        assertEquals(16_000L, RebindBackoff.delayMs(3))
    }

    @Test
    fun `a phone that flaps all day is retried every five minutes, not every two seconds`() {
        assertEquals(RebindBackoff.MAX_MS, RebindBackoff.delayMs(8))
        assertEquals(RebindBackoff.MAX_MS, RebindBackoff.delayMs(100))
    }

    @Test
    fun `no attempt is ever retried faster than the first one`() {
        assertTrue(RebindBackoff.delayMs(-7) >= RebindBackoff.BASE_MS)
    }

    @Test
    fun `the wait never shrinks as attempts accumulate`() {
        val delays = (0..12).map { RebindBackoff.delayMs(it) }
        assertEquals(delays.sorted(), delays)
    }
}
