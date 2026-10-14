package com.aura.led.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    private val now = 1_000_000_000_000L

    @Test
    fun `the network check itself is allowed only once a day`() {
        assertTrue(UpdatePolicy.shouldCheck(now, lastCheckMs = 0L))
        assertTrue(UpdatePolicy.shouldCheck(now, lastCheckMs = now - UpdatePolicy.CHECK_INTERVAL_MS))
        assertTrue(UpdatePolicy.shouldCheck(now, lastCheckMs = now - UpdatePolicy.CHECK_INTERVAL_MS * 5))

        assertFalse(UpdatePolicy.shouldCheck(now, lastCheckMs = now))
        assertFalse(UpdatePolicy.shouldCheck(now, lastCheckMs = now - UpdatePolicy.CHECK_INTERVAL_MS + 1))
        // A backwards clock must not block the check forever.
        assertTrue(UpdatePolicy.shouldCheck(now, lastCheckMs = now + 1))
    }

    @Test
    fun `prompts when a new tag was never seen or checked`() {
        val decision = UpdatePolicy.decide(
            nowMs = now,
            lastCheckMs = 0L,
            lastPromptMs = 0L,
            dismissedTag = "",
            latestTag = "v0.6.0",
        )

        assertTrue(decision.shouldPrompt)
        assertEquals(UpdatePolicy.Reason.OK, decision.reason)
    }

    @Test
    fun `never prompts again for a tag the user dismissed`() {
        val decision = UpdatePolicy.decide(
            nowMs = now,
            lastCheckMs = now - UpdatePolicy.CHECK_INTERVAL_MS * 10,
            lastPromptMs = now - UpdatePolicy.RE_PROMPT_INTERVAL_MS * 10,
            dismissedTag = "v0.6.0",
            latestTag = "v0.6.0",
        )

        assertFalse(decision.shouldPrompt)
        assertEquals(UpdatePolicy.Reason.DISMISSED_TAG, decision.reason)
    }

    @Test
    fun `a later release prompts again even after a dismissal`() {
        val decision = UpdatePolicy.decide(
            nowMs = now,
            lastCheckMs = now - UpdatePolicy.CHECK_INTERVAL_MS * 2,
            lastPromptMs = now - UpdatePolicy.RE_PROMPT_INTERVAL_MS * 2,
            dismissedTag = "v0.6.0",
            latestTag = "v0.7.0",
        )

        assertTrue(decision.shouldPrompt)
    }

    @Test
    fun `checks are throttled to once a day`() {
        val decision = UpdatePolicy.decide(
            nowMs = now,
            lastCheckMs = now - UpdatePolicy.CHECK_INTERVAL_MS + 1,
            lastPromptMs = 0L,
            dismissedTag = "",
            latestTag = "v0.6.0",
        )

        assertFalse(decision.shouldPrompt)
        assertEquals(UpdatePolicy.Reason.CHECKED_RECENTLY, decision.reason)
    }

    @Test
    fun `a postponed prompt is not repeated before three days`() {
        val decision = UpdatePolicy.decide(
            nowMs = now,
            lastCheckMs = now - UpdatePolicy.CHECK_INTERVAL_MS * 2,
            lastPromptMs = now - UpdatePolicy.RE_PROMPT_INTERVAL_MS + 1,
            dismissedTag = "",
            latestTag = "v0.6.0",
        )

        assertFalse(decision.shouldPrompt)
        assertEquals(UpdatePolicy.Reason.PROMPTED_RECENTLY, decision.reason)
    }

    @Test
    fun `prompts again once both windows have elapsed`() {
        val decision = UpdatePolicy.decide(
            nowMs = now,
            lastCheckMs = now - UpdatePolicy.CHECK_INTERVAL_MS,
            lastPromptMs = now - UpdatePolicy.RE_PROMPT_INTERVAL_MS,
            dismissedTag = "",
            latestTag = "v0.6.0",
        )

        assertTrue(decision.shouldPrompt)
    }

    @Test
    fun `a version is announced by notification once, not every day`() {
        assertTrue(UpdatePolicy.shouldNotify(dismissedTag = "", notifiedTag = "", latestTag = "v0.6.1"))
        // The version the user already ignored: the daily check must not become a daily reminder.
        assertFalse(
            UpdatePolicy.shouldNotify(dismissedTag = "", notifiedTag = "v0.6.1", latestTag = "v0.6.1")
        )
        // A later release is news again.
        assertTrue(
            UpdatePolicy.shouldNotify(dismissedTag = "", notifiedTag = "v0.6.1", latestTag = "v0.7.0")
        )
    }

    @Test
    fun `a version the user already waved away in the app is never announced`() {
        assertFalse(
            UpdatePolicy.shouldNotify(dismissedTag = "v0.6.1", notifiedTag = "", latestTag = "v0.6.1")
        )
        assertTrue(
            UpdatePolicy.shouldNotify(dismissedTag = "v0.6.1", notifiedTag = "", latestTag = "v0.7.0")
        )
    }

    @Test
    fun `an empty tag is never announced`() {
        assertFalse(UpdatePolicy.shouldNotify(dismissedTag = "", notifiedTag = "", latestTag = ""))
    }

    @Test
    fun `a finished background check announces a release it has just found`() {
        val decision = UpdatePolicy.shouldAnnounce(
            nowMs = now,
            previousCheckMs = now - UpdatePolicy.CHECK_INTERVAL_MS,
            lastPromptMs = 0L,
            dismissedTag = "",
            notifiedTag = "",
            latestTag = "v0.5.1",
        )

        assertTrue(decision.shouldPrompt)
        assertEquals(UpdatePolicy.Reason.OK, decision.reason)
    }

    @Test
    fun `the timestamp this check just wrote must not silence its own notification`() {
        // The trap that reached a device build: the check stamps `lastUpdateCheckMs` before asking
        // for a decision, so the decision saw "checked right now" and nothing was ever announced.
        val withFreshTimestamp = UpdatePolicy.shouldAnnounce(
            nowMs = now,
            previousCheckMs = now,
            lastPromptMs = 0L,
            dismissedTag = "",
            notifiedTag = "",
            latestTag = "v0.5.1",
        )
        // `shouldCheck` has already admitted this run, so the value it hands over is either 0
        // (never checked — the fresh install) or at least a day old. This is the second one.
        val withPreviousTimestamp = UpdatePolicy.shouldAnnounce(
            nowMs = now,
            previousCheckMs = now - UpdatePolicy.CHECK_INTERVAL_MS,
            lastPromptMs = 0L,
            dismissedTag = "",
            notifiedTag = "",
            latestTag = "v0.5.1",
        )

        assertFalse(withFreshTimestamp.shouldPrompt)
        assertEquals(UpdatePolicy.Reason.CHECKED_RECENTLY, withFreshTimestamp.reason)
        assertTrue(withPreviousTimestamp.shouldPrompt)
    }

    @Test
    fun `an already announced release is not announced again`() {
        val decision = UpdatePolicy.shouldAnnounce(
            nowMs = now,
            previousCheckMs = now - UpdatePolicy.CHECK_INTERVAL_MS * 2,
            lastPromptMs = 0L,
            dismissedTag = "",
            notifiedTag = "v0.5.1",
            latestTag = "v0.5.1",
        )

        assertFalse(decision.shouldPrompt)
        assertEquals(UpdatePolicy.Reason.ALREADY_NOTIFIED, decision.reason)
    }

    @Test
    fun `a user who answered in the app is not asked again by notification`() {
        val dismissed = UpdatePolicy.shouldAnnounce(
            nowMs = now,
            previousCheckMs = now - UpdatePolicy.CHECK_INTERVAL_MS * 2,
            lastPromptMs = 0L,
            dismissedTag = "v0.5.1",
            notifiedTag = "",
            latestTag = "v0.5.1",
        )
        val promptedAnHourAgo = UpdatePolicy.shouldAnnounce(
            nowMs = now,
            previousCheckMs = now - UpdatePolicy.CHECK_INTERVAL_MS * 2,
            lastPromptMs = now - 60 * 60 * 1000L,
            dismissedTag = "",
            notifiedTag = "",
            latestTag = "v0.5.1",
        )

        assertFalse(dismissed.shouldPrompt)
        assertEquals(UpdatePolicy.Reason.DISMISSED_TAG, dismissed.reason)
        assertFalse(promptedAnHourAgo.shouldPrompt)
        assertEquals(UpdatePolicy.Reason.PROMPTED_RECENTLY, promptedAnHourAgo.reason)
    }

    @Test
    fun `a clock that moved backwards still prompts rather than locking the user out`() {
        val decision = UpdatePolicy.decide(
            nowMs = 0L,
            lastCheckMs = now,
            lastPromptMs = now,
            dismissedTag = "",
            latestTag = "v0.6.0",
        )

        assertTrue(decision.shouldPrompt)
    }
}
