package com.aura.led.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The answers are the values of `support_intent`'s `answer` property, so a rename here silently
 * empties a dashboard breakdown. These tests pin the documented set, not the implementation.
 */
class SupportQuestionTest {

    @Test
    fun `offers one answer per payment the user can choose, donation first`() {
        assertEquals(
            listOf("donation", "one_time", "subscription", "nothing"),
            SupportQuestion.ANSWERS.map { it.first },
        )
    }

    @Test
    fun `every answer carries a distinct label`() {
        val labels = SupportQuestion.ANSWERS.map { it.second }

        assertEquals(SupportQuestion.ANSWERS.size, labels.distinct().size)
    }

    @Test
    fun `answer values stay lowercase identifiers the dashboard can be queried with`() {
        val malformed = SupportQuestion.ANSWERS.map { it.first }.filter { !it.matches(Regex("[a-z_]+")) }

        assertTrue("answers not usable as property values: $malformed", malformed.isEmpty())
    }

    @Test
    fun `only the donation answer opens the donation page`() {
        assertTrue(SupportQuestion.opensDonationPage(SupportQuestion.DONATION_NOW))

        val others = SupportQuestion.ANSWERS.map { it.first }.filter { it != SupportQuestion.DONATION_NOW }
        for (answer in others) {
            assertFalse("$answer should not open the donation page", SupportQuestion.opensDonationPage(answer))
        }
    }

    @Test
    fun `the first answer and any later change are recorded`() {
        assertTrue(SupportQuestion.shouldRecord(null, "donation"))
        assertTrue(SupportQuestion.shouldRecord("donation", "subscription"))
        assertTrue(SupportQuestion.shouldRecord("nothing", "one_time"))
    }

    @Test
    fun `re-tapping the selected answer is not a new answer`() {
        for (answer in SupportQuestion.ANSWERS.map { it.first }) {
            assertFalse("$answer re-tapped should not report again", SupportQuestion.shouldRecord(answer, answer))
        }
    }

    /**
     * The app list is the only place the ask reaches someone who never opens Settings, so the three
     * ways this offer goes wrong are pinned here: asking someone who already answered, asking
     * someone whose answer could not be recorded, and letting "later" mean "never".
     */
    @Test
    fun `the question is offered on the app list to whoever has not answered it`() {
        assertTrue(
            SupportQuestion.shouldOfferOnAppList(consentGranted = true, answer = null, dismissed = false),
        )
    }

    @Test
    fun `an answered question is never offered again`() {
        for (answer in SupportQuestion.ANSWERS.map { it.first }) {
            assertFalse(
                "`$answer` is answered: the ask must not come back",
                SupportQuestion.shouldOfferOnAppList(consentGranted = true, answer = answer, dismissed = false),
            )
        }
    }

    @Test
    fun `a question whose answer cannot be recorded is not offered`() {
        assertFalse(
            SupportQuestion.shouldOfferOnAppList(consentGranted = false, answer = null, dismissed = false),
        )
    }

    @Test
    fun `later hides the offer without ending it`() {
        assertFalse(
            SupportQuestion.shouldOfferOnAppList(consentGranted = true, answer = null, dismissed = true),
        )
        assertTrue(
            "the next launch has to offer it again: only an answer ends the ask",
            SupportQuestion.shouldOfferOnAppList(consentGranted = true, answer = null, dismissed = false),
        )
    }

    @Test
    fun `an unknown or differently cased answer never opens the donation page`() {
        assertFalse(SupportQuestion.opensDonationPage("Donation"))
        assertFalse(SupportQuestion.opensDonationPage("donation "))
        assertFalse(SupportQuestion.opensDonationPage(""))
    }
}
