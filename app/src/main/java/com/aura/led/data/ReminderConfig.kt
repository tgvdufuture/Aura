package com.aura.led.data

/**
 * Pure configuration model for the persistent reminder loop (PRD Phase 1).
 * Kept free of Android dependencies so the clamping and parsing rules are
 * unit-testable (see [ReminderConfigTest]).
 */
data class ReminderConfig(
    val enabled: Boolean = false,
    val intervalMs: Long = DEFAULT_INTERVAL_MS,
    val quietStart: String = DEFAULT_QUIET_START,
    val quietEnd: String = DEFAULT_QUIET_END,
) {
    companion object {
        const val DEFAULT_INTERVAL_MS = 15_000L
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
