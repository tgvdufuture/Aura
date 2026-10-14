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
