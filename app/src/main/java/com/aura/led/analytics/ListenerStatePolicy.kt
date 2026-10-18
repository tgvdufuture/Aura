package com.aura.led.analytics

/**
 * How often `listener_state` may be sent.
 *
 * The event reports a real failure — HyperOS unbinding the notification listener, which silently
 * disables every LED — but the same binding is what the app itself tries to repair: a disconnect
 * schedules a `requestRebind` after 2 s, and every process start asks for one too. A system stuck
 * in that loop would emit a disconnect/rebind pair every couple of seconds, which is exactly the
 * volume mistake `led_duration_changed` and `reminder_interval_changed` made before 2026-10-18
 * (91 and 328 events inside a single minute).
 *
 * So the budget keeps a real pair and nothing more: **2 events per rolling minute**, which is one
 * disconnect plus the rebind that follows it, and **5 per process**, so a device that flaps for
 * hours still reports that it flaps without filling the project's storage. A truncated series is
 * a floor — "at least this many transitions" — and the truncation itself is the signal.
 *
 * Pure, so the budget is checked by a unit test rather than pinned as text: the caller owns the
 * counters, like `UpdatePolicy` owns the update-check clock.
 */
internal object ListenerStatePolicy {

    const val MAX_PER_PROCESS = 5
    const val MAX_PER_WINDOW = 2
    const val WINDOW_MS = 60_000L

    /** Transitions this process has reported, and how many of them fell in the current window. */
    data class Counters(
        val perProcess: Int = 0,
        val windowStartMs: Long = 0L,
        val inWindow: Int = 0,
    )

    /**
     * Whether a transition happening at [nowMs] may be reported. [nowMs] is monotonic
     * (`SystemClock.elapsedRealtime`): a wall-clock change must not silence a real failure.
     */
    fun allows(nowMs: Long, counters: Counters): Boolean =
        counters.perProcess < MAX_PER_PROCESS &&
            (nowMs - counters.windowStartMs >= WINDOW_MS || counters.inWindow < MAX_PER_WINDOW)

    /** Folds a transition already allowed by [allows] into the counters. */
    fun record(nowMs: Long, counters: Counters): Counters {
        val windowElapsed = nowMs - counters.windowStartMs >= WINDOW_MS

        return Counters(
            perProcess = counters.perProcess + 1,
            windowStartMs = if (windowElapsed) nowMs else counters.windowStartMs,
            inWindow = if (windowElapsed) 1 else counters.inWindow + 1,
        )
    }
}
