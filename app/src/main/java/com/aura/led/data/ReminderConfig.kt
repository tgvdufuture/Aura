package com.aura.led.data

/**
 * Pure configuration model for the persistent reminder loop (PRD Phase 1).
 * Kept free of Android dependencies so the clamping and parsing rules are
 * unit-testable (see [ReminderConfigTest]).
 */
data class ReminderConfig(
    val enabled: Boolean = false,
    val intervalMs: Long = DEFAULT_INTERVAL_MS,
    /** How long the loop may run, counted from the first flash; 0 = until dismissed. */
    val durationMs: Long = DEFAULT_DURATION_MS,
    val quietStart: String = DEFAULT_QUIET_START,
    val quietEnd: String = DEFAULT_QUIET_END,
) {
    companion object {
        const val DEFAULT_INTERVAL_MS = 15_000L
        const val DEFAULT_DURATION_MS = 0L
        const val DEFAULT_QUIET_START = "23:00"
        const val DEFAULT_QUIET_END = "07:00"
    }
}

object ReminderConfigLimits {
    const val MIN_INTERVAL_MS = 5_000L
    const val MAX_INTERVAL_MS = 120_000L
    const val STEP_MS = 5_000L
}

object QuietHours {
    /** Minutes parsed out of an "HH:mm" string; null when the input is malformed. */
    fun parseHHmm(value: String): Int? {
        val match = Regex("^(\\d{1,2}):(\\d{2})$").find(value.trim()) ?: return null
        val (h, m) = match.destructured
        val hour = h.toInt()
        val minute = m.toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    /**
     * True when [nowMinutes] falls inside the [startMinutes]-[endMinutes] window.
     * A window that wraps midnight (e.g. 23:00-07:00) is supported; start == end
     * means the quiet hours are disabled.
     */
    fun isInRange(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
        if (startMinutes == endMinutes) return false
        return if (startMinutes < endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }
}

object ReminderInterval {
    /** Rounds to the nearest 5s step and clamps into the 5-120s range (PRD F1.1 AC2). */
    fun clamp(ms: Long): Long =
        ((ms / ReminderConfigLimits.STEP_MS) * ReminderConfigLimits.STEP_MS)
            .coerceIn(ReminderConfigLimits.MIN_INTERVAL_MS, ReminderConfigLimits.MAX_INTERVAL_MS)
}

/** The lifetime of one reminder loop. Zero is a real choice, not an invalid value. */
object ReminderDuration {
    const val UNTIL_DISMISSED = 0L

    /** Shown in Settings, in this order; [UNTIL_DISMISSED] first because it is the default. */
    val OPTIONS_MS = listOf(
        UNTIL_DISMISSED,
        5 * 60_000L,
        15 * 60_000L,
        30 * 60_000L,
        60 * 60_000L,
    )

    const val MIN_MS = 60_000L
    const val MAX_MS = 24 * 60 * 60_000L

    /** Keeps a stored value usable; 0 stays "until dismissed" and is never clamped up. */
    fun clamp(ms: Long): Long =
        if (ms <= UNTIL_DISMISSED) UNTIL_DISMISSED else ms.coerceIn(MIN_MS, MAX_MS)
}

object ReminderTiming {
    /** The ring must stay dark for a moment between two flashes. */
    const val MIN_OFF_GAP_MS = 500L

    /**
     * Pause between the starts of two flashes. It can never be shorter than the time the LED stays
     * on plus a visible off gap: with a 30 s light and a 5 s interval, the reminder used to keep the
     * ring lit across ticks instead of blinking.
     */
    fun effectiveIntervalMs(intervalMs: Long, onMs: Long): Long =
        maxOf(intervalMs, onMs + MIN_OFF_GAP_MS)
}
