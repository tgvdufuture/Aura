package com.aura.led.update

/**
 * Decides whether Aura may ask about an available update.
 *
 * Pure Kotlin so the throttling rules are unit-testable: at most one GitHub check per day,
 * no repeated prompt for a tag the user dismissed, and a newer "Later" prompt only after
 * three days.
 */
object UpdatePolicy {
    const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    const val RE_PROMPT_INTERVAL_MS = 72 * 60 * 60 * 1000L

    enum class Reason { OK, DISMISSED_TAG, CHECKED_RECENTLY, PROMPTED_RECENTLY }

    data class PromptDecision(val shouldPrompt: Boolean, val reason: Reason)

    /**
     * Whether a GitHub request may be made at all. Keeps the app to one request per day no
     * matter how often the user opens a screen, and treats a backwards clock as "never checked".
     */
    fun shouldCheck(
        nowMs: Long,
        lastCheckMs: Long,
        checkIntervalMs: Long = CHECK_INTERVAL_MS,
    ): Boolean {
        if (lastCheckMs <= 0L) return true
        return nowMs - lastCheckMs !in 0 until checkIntervalMs
    }

    fun decide(
        nowMs: Long,
        lastCheckMs: Long,
        lastPromptMs: Long,
        dismissedTag: String,
        latestTag: String,
        checkIntervalMs: Long = CHECK_INTERVAL_MS,
        rePromptIntervalMs: Long = RE_PROMPT_INTERVAL_MS,
    ): PromptDecision {
        if (dismissedTag.isNotEmpty() && dismissedTag == latestTag) {
            return PromptDecision(false, Reason.DISMISSED_TAG)
        }
        // A negative elapsed time means the device clock moved backwards; treat it as
        // "never checked" instead of locking the user out of updates indefinitely.
        val sinceCheckMs = nowMs - lastCheckMs
        if (lastCheckMs > 0L && sinceCheckMs in 0 until checkIntervalMs) {
            return PromptDecision(false, Reason.CHECKED_RECENTLY)
        }
        val sincePromptMs = nowMs - lastPromptMs
        if (lastPromptMs > 0L && sincePromptMs in 0 until rePromptIntervalMs) {
            return PromptDecision(false, Reason.PROMPTED_RECENTLY)
        }
        return PromptDecision(true, Reason.OK)
    }
}
