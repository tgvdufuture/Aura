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
        analyticsGranted = true,
        supportAnswer = "donation",
    )

    private fun attention(
        notificationAccess: Boolean = true,
        privilege: Boolean = true,
        batteryExempt: Boolean = true,
        analyticsGranted: Boolean = true,
        supportAnswer: String? = "donation",
    ) = settingsAttention(
        AttentionInputs(notificationAccess, privilege, batteryExempt, analyticsGranted, supportAnswer),
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
