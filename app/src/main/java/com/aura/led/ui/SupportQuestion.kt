package com.aura.led.ui

import com.aura.led.R

/**
 * The contract of the support question: which answers exist and what one of them does.
 *
 * The values are sent verbatim as the `answer` property of `support_intent`, so they are data, not
 * copy — the dashboard breaks silently if one is renamed. Keeping them outside the composable is
 * what makes them assertable in a unit test.
 */
object SupportQuestion {

    /** Answering this opens the donation page instead of only recording an opinion. */
    const val DONATION_NOW = "donation"

    /**
     * Every answer the question offers, in display order, each with the label that names it.
     *
     * Each label answers the question on its own: one says which payment the user would make, and
     * the last one states the absence of any payment — including the donation — instead of saying
     * "neither". Two rounds of owner feedback (2026-10-14) came from copy that only made sense
     * next to the other options ("Monthly", "Neither", with four chips on screen).
     *
     * The set is fixed by design: no free text, no email (see docs/PRD-usage-analytics-and-updates.md).
     */
    val ANSWERS: List<Pair<String, Int>> = listOf(
        DONATION_NOW to R.string.support_answer_donation,
        "one_time" to R.string.support_answer_one_time,
        "subscription" to R.string.support_answer_subscription,
        "nothing" to R.string.support_answer_nothing,
    )

    /** True when answering [answer] should take the user to the donation page. */
    fun opensDonationPage(answer: String): Boolean = answer == DONATION_NOW

    /**
     * True when picking [answer] is a change and therefore worth reporting. The answer stays
     * editable, so tapping the choice already selected must not send a second event for the
     * same opinion — the dashboard reads the latest answer per user.
     */
    fun shouldRecord(current: String?, answer: String): Boolean = answer != current

    /**
     * Whether the support banner is shown at the top of the app list.
     *
     * The question and its card have only ever lived in Settings, so the users who most need to be
     * asked — the ones who never open Settings — were exactly the ones never asked. The banner is
     * not the question: it is the Settings card's own three lines with its donate button, and the
     * question stays where an answer can be given and changed. What it does is put the ask in front
     * of people who would otherwise never see it.
     *
     * An answer ends it for good, and it is the answer that ends it — not the banner's action and
     * not a dismissal. It is persisted ([com.aura.led.analytics.AnalyticsPrefs.supportAnswer]), so
     * "someone who has answered is never asked again" survives a restart and an update.
     *
     * [dismissed] is per session and **never persisted**: "later" has to mean later, not never, so
     * the banner comes back at the next launch until the question is actually answered. A persisted
     * dismissal would be a silent "no thanks" the user never gave — the same trap the update
     * dialog's `dismissedVersion` is documented to have.
     *
     * Consent is part of the rule for the reason [com.aura.led.ui.settingsAttention] gives: with
     * statistics off the question is not on the Settings screen at all, and an ask whose answer
     * could not be recorded is not the ask this banner exists for.
     */
    fun shouldOfferOnAppList(consentGranted: Boolean, answer: String?, dismissed: Boolean): Boolean =
        consentGranted && answer == null && !dismissed
}
