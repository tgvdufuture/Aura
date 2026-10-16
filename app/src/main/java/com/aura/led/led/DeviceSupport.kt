package com.aura.led.led

/**
 * Whether Aura's LED backend can drive this device, from the two things the app can actually know:
 * the device identity, and the verdict of the LED test itself.
 *
 * The only backend is `miui.lights.ILightsManager`, a HyperOS service ([ShizukuLEDController]), so on
 * any other device family every LED command fails. The v0.6.0 data says exactly that: 12 LED tests
 * run on non-Xiaomi phones, 12 failures, and not one `led_emitted` — while the setup funnel recorded
 * those installs as if they were users. Saying so before the user goes through the Shizuku setup is
 * the point of the identity rule.
 *
 * Identity alone is not enough, and the same data says that too: a Xiaomi-family phone whose ROM has
 * no such service fails every command and gets no notice at all. The test is the second input — it is
 * the phone answering the question itself, so [shouldWarn] accepts it, but only once the attempts
 * that failed are attempts that *ran* ([CONCLUSIVE_FAILURES]).
 *
 * Pure Kotlin, no Android imports: `Build.MANUFACTURER`, `Build.BRAND` and the failure streak are
 * read by the caller, so the rules themselves are unit-testable.
 */
object DeviceSupport {

    /** The families that ship the service Aura drives. Matched against manufacturer *and* brand. */
    private val XIAOMI_FAMILY = setOf("xiaomi", "redmi", "poco")

    enum class Verdict {
        SUPPORTED_FAMILY,

        /** A known device identity outside the family: the light can never work here. */
        UNSUPPORTED_FAMILY,

        /** Nothing usable to decide from. */
        UNKNOWN,
    }

    /**
     * Deliberately lenient: one matching value is enough, and an unknown identity is [Verdict.UNKNOWN]
     * rather than unsupported. Telling a user their phone cannot work when it can is worse than not
     * showing a notice at all, so the rule only ever fires on a positive identification of a
     * non-Xiaomi device.
     */
    fun verdict(manufacturer: String?, brand: String?): Verdict {
        val values = listOfNotNull(manufacturer, brand)
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        if (values.isEmpty()) return Verdict.UNKNOWN
        return if (values.any { it in XIAOMI_FAMILY }) Verdict.SUPPORTED_FAMILY else Verdict.UNSUPPORTED_FAMILY
    }

    /**
     * How many LED attempts that ran and failed, in a row, before the phone is the problem rather
     * than the user's setup.
     *
     * Three, and the number comes from the field rather than from taste. Read on 2026-10-16, before
     * this rule existed, every test the app had ever run is in PostHog, in order:
     *
     * | phone | attempts, in order | outcome |
     * |---|---|---|
     * | Redmi Note 9 (`merlin`) | 80 × failure | never emitted a LED, warned nobody |
     * | Infinix X6725 | 10 × failure | outside the family, warned by [shouldWarn] already |
     * | `23106RN0DA` (Xiaomi) | 6 × failure | never emitted a LED, warned nobody |
     * | POCO X8 Pro `2511FPC34G` | 7 successes | the ring works |
     * | `2602BPC18G` (Xiaomi) | 6 successes, **1 failure**, 1 success | the ring works — and this is
     * the phone the threshold has to spare |
     *
     * A phone that cannot drive the light fails every attempt (80, 10, 6 in a row); a phone that can
     * produced exactly one isolated failure in 8 tests, and the counter is cleared by the next
     * success ([nextFailureStreak]). Three is therefore the smallest number that separates the two,
     * with the whole of the observed fluke on the other side of it.
     */
    const val CONCLUSIVE_FAILURES = 3

    /**
     * The streak after one attempt: a success is proof the phone lights up, a failure one more doubt.
     *
     * Pure arithmetic on purpose — the caller is the one that decides whether the attempt counts at
     * all (see `MainViewModel.testLed`: an attempt made without the privilege never reached the
     * service, so it says nothing about the phone).
     */
    fun nextFailureStreak(current: Int, success: Boolean): Int = if (success) 0 else current + 1

    /** True when the attempts already observed can only mean the service is not there. */
    fun failuresAreConclusive(failuresInARow: Int): Boolean = failuresInARow >= CONCLUSIVE_FAILURES

    /**
     * True when the app should tell the user this device cannot drive the light.
     *
     * Two ways in, and they are different in kind: [verdict] knows the family before any setup, while
     * [failuresInARow] is the test's own verdict — the phone said so itself, which is the only thing
     * that can catch a Xiaomi-family device whose ROM has no `miui.lights` (the case the data named on
     * 2026-10-16: the warning is what stops a user retrying, and one user had reached 80 attempts).
     */
    fun shouldWarn(manufacturer: String?, brand: String?, failuresInARow: Int = 0): Boolean =
        verdict(manufacturer, brand) == Verdict.UNSUPPORTED_FAMILY || failuresAreConclusive(failuresInARow)

    /**
     * True when the full-screen notice should be shown: a device positively identified outside the
     * family, with a user who has not been through it yet. Kept here rather than inside the
     * composable so "shown once, and never on a device that may work" is assertable.
     */
    fun shouldShowScreen(manufacturer: String?, brand: String?, acknowledged: Boolean): Boolean =
        !acknowledged && verdict(manufacturer, brand) == Verdict.UNSUPPORTED_FAMILY
}
