package com.aura.led.led

/**
 * Whether Aura's LED backend can drive this device, decided from the device identity alone.
 *
 * The only backend is `miui.lights.ILightsManager`, a HyperOS service ([ShizukuLEDController]), so on
 * any other device family every LED command fails. The v0.6.0 data says exactly that: 12 LED tests
 * run on non-Xiaomi phones, 12 failures, and not one `led_emitted` — while the setup funnel recorded
 * those installs as if they were users. Saying so before the user goes through the Shizuku setup is
 * the point of this rule.
 *
 * Pure Kotlin, no Android imports: `Build.MANUFACTURER` and `Build.BRAND` are read by the caller, so
 * the matching itself is unit-testable.
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

    /** True when the app should tell the user this device cannot drive the light. */
    fun shouldWarn(manufacturer: String?, brand: String?): Boolean =
        verdict(manufacturer, brand) == Verdict.UNSUPPORTED_FAMILY

    /**
     * True when the full-screen notice should be shown: a device positively identified outside the
     * family, with a user who has not been through it yet. Kept here rather than inside the
     * composable so "shown once, and never on a device that may work" is assertable.
     */
    fun shouldShowScreen(manufacturer: String?, brand: String?, acknowledged: Boolean): Boolean =
        !acknowledged && verdict(manufacturer, brand) == Verdict.UNSUPPORTED_FAMILY
}
