package com.aura.led.notification

/**
 * How long to wait before asking the system to rebind the listener after a disconnect.
 *
 * The delay used to be a fixed two seconds, with no limit on the number of attempts. HyperOS drops
 * the listener routinely — the 2026-10-18 data reads it bound in 114 of 244 sessions — and on a
 * phone where it connects and drops again the fixed delay becomes a permanent two-second wake-up:
 * a rebind request, a rebind, a disconnect, over and over, for as long as the phone is on. That is
 * a battery and heat cost with nothing to show for it (issue #4).
 *
 * Doubling keeps the quick recovery of a one-off drop (2 s, 4 s, 8 s …) and turns a phone that
 * cannot stay bound into a heartbeat every five minutes. A successful connection resets the count.
 */
object RebindBackoff {
    /** First retry: the same two seconds an isolated drop has always waited. */
    const val BASE_MS = 2_000L

    /** Ceiling: a phone that flaps all day is retried every five minutes, not every two seconds. */
    const val MAX_MS = 5 * 60 * 1000L

    /** [BASE_MS] doubled this many times already passes [MAX_MS], and the shift cannot overflow. */
    private const val MAX_STEPS = 8

    /** Delay for the [attempt]th retry since the last successful connection, `0` being the first. */
    fun delayMs(attempt: Int): Long =
        (BASE_MS shl attempt.coerceIn(0, MAX_STEPS)).coerceAtMost(MAX_MS)
}
