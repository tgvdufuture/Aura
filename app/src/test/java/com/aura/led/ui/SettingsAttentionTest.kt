package com.aura.led.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The badge's two ways of lying: staying quiet while the app is broken, and shouting while nothing
 * is wrong. Both are invisible in review — the dot is six pixels of colour.
 */
class SettingsAttentionTest {

    private val everythingSetUp = AttentionInputs(
        notificationAccess = true,
        privilege = true,
        batteryExempt = true,
        autostartBlocked = false,
        analyticsGranted = true,
        supportAnswer = "donation",
    )

    private fun attention(
        notificationAccess: Boolean = true,
        privilege: Boolean = true,
        batteryExempt: Boolean = true,
        autostartBlocked: Boolean = false,
        analyticsGranted: Boolean = true,
        supportAnswer: String? = "donation",
    ) = settingsAttention(
        AttentionInputs(
            notificationAccess = notificationAccess,
            privilege = privilege,
            batteryExempt = batteryExempt,
            autostartBlocked = autostartBlocked,
            analyticsGranted = analyticsGranted,
            supportAnswer = supportAnswer,
        ),
    )

    @Test
    fun `a fully set up app shows no dot at all`() {
        val result = settingsAttention(everythingSetUp)

        assertEquals(0, result.total)
        assertNull(result.level)
    }

    @Test
    fun `without notification access nothing lights up, and that is important`() {
        val result = attention(notificationAccess = false)

        assertEquals(AttentionLevel.IMPORTANT, result.level)
        assertEquals(1, result.important)
    }

    @Test
    fun `without Shizuku or root the LED cannot be written, and that is important too`() {
        val result = attention(privilege = false)

        assertEquals(AttentionLevel.IMPORTANT, result.level)
        assertEquals(1, result.important)
    }

    @Test
    fun `the battery exemption and the unanswered question are informational, not failures`() {
        val battery = attention(batteryExempt = false)
        assertEquals(AttentionLevel.INFO, battery.level)
        assertEquals(1, battery.info)
        assertEquals(0, battery.important)

        val question = attention(supportAnswer = null)
        assertEquals(AttentionLevel.INFO, question.level)
        assertEquals(1, question.info)
        assertEquals(0, question.important)
    }

    @Test
    fun `a blocked autostart is a suspicion, not a failure`() {
        val result = attention(autostartBlocked = true)

        assertEquals(AttentionLevel.INFO, result.level)
        assertEquals(1, result.info)
        assertEquals(0, result.important)
    }

    /**
     * Two informative gaps have to count two. Written after getting it wrong: an unparenthesised
     * `if (...) 1 else 0 +` in the sum hands the count to the branch that runs first, so the second
     * gap would have been counted as "one thing to look at" while the dot said otherwise.
     */
    @Test
    fun `two informational gaps count two`() {
        val result = attention(batteryExempt = false, autostartBlocked = true)

        assertEquals(2, result.info)
    }

    /**
     * Seen on the owner's device: with statistics declined the support card is not on the Settings
     * screen at all, so a dot for its unanswered question would point at something that is not
     * there — and would never go away.
     */
    @Test
    fun `a question the app will never ask is not a gap`() {
        val result = attention(analyticsGranted = false, supportAnswer = null)

        assertEquals(0, result.info)
        assertNull(result.level)
    }

    @Test
    fun `an important gap wins the colour over the informative ones`() {
        val result = attention(notificationAccess = false, privilege = false, batteryExempt = false, supportAnswer = null)

        assertEquals(AttentionLevel.IMPORTANT, result.level)
        assertEquals(2, result.important)
        assertEquals(2, result.info)
        assertEquals(4, result.total)
    }
}
