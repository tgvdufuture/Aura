package com.aura.led.led

import com.aura.led.led.DeviceSupport.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSupportTest {

    @Test
    fun `the xiaomi family is supported`() {
        assertEquals(Verdict.SUPPORTED_FAMILY, DeviceSupport.verdict("Xiaomi", "Xiaomi"))
        assertEquals(Verdict.SUPPORTED_FAMILY, DeviceSupport.verdict("Xiaomi", "POCO"))
        assertEquals(Verdict.SUPPORTED_FAMILY, DeviceSupport.verdict("Xiaomi", "Redmi"))
    }

    @Test
    fun `the non-xiaomi phones that installed Aura never lit a LED`() {
        // The exact manufacturer/brand pairs read from PostHog for the external installs of v0.6.0:
        // three Infinix, one Samsung, one OnePlus, plus the Android emulator. Their LED test failed
        // every single time it ran, which is what this rule is meant to say out loud before they try.
        assertEquals(Verdict.UNSUPPORTED_FAMILY, DeviceSupport.verdict("INFINIX", "Infinix"))
        assertEquals(Verdict.UNSUPPORTED_FAMILY, DeviceSupport.verdict("samsung", "samsung"))
        assertEquals(Verdict.UNSUPPORTED_FAMILY, DeviceSupport.verdict("OnePlus", "OnePlus"))
        assertEquals(Verdict.UNSUPPORTED_FAMILY, DeviceSupport.verdict("Google", "sdk_gphone64_x86_64"))
    }

    @Test
    fun `an unknown identity is never reported as unsupported`() {
        // A missing or unreadable value must not produce a notice: the app has nothing to base it on.
        assertEquals(Verdict.UNKNOWN, DeviceSupport.verdict(null, null))
        assertEquals(Verdict.UNKNOWN, DeviceSupport.verdict("", ""))
        assertEquals(Verdict.UNKNOWN, DeviceSupport.verdict("   ", null))
        assertFalse(DeviceSupport.shouldWarn(null, null))
    }

    @Test
    fun `one matching value is enough`() {
        // Lenient on purpose: a non-Xiaomi brand on Xiaomi hardware (or the reverse) must not hide a
        // device Aura can drive, since a wrong notice is worse than a missing one.
        assertEquals(Verdict.SUPPORTED_FAMILY, DeviceSupport.verdict("Xiaomi", "not-a-brand"))
        assertEquals(Verdict.SUPPORTED_FAMILY, DeviceSupport.verdict("Some ODM", "POCO"))
    }

    @Test
    fun `casing and surrounding spaces do not change the verdict`() {
        assertEquals(Verdict.SUPPORTED_FAMILY, DeviceSupport.verdict("  xIaOmI ", null))
        assertEquals(Verdict.UNSUPPORTED_FAMILY, DeviceSupport.verdict("SAMSUNG ", null))
        assertTrue(DeviceSupport.shouldWarn(" infInix", null))
    }

    @Test
    fun `shouldWarn fires only for a known non-xiaomi device`() {
        assertTrue(DeviceSupport.shouldWarn("Infinix", "Infinix"))
        assertFalse(DeviceSupport.shouldWarn("Xiaomi", "POCO"))
        assertFalse(DeviceSupport.shouldWarn(null, ""))
    }

    /**
     * The second input, and the reason it exists: the field sequences read on 2026-10-16, replayed.
     *
     * A Xiaomi-family phone whose ROM has no `miui.lights` fails every attempt forever and the family
     * rule says nothing (`merlin`, a Redmi Note 9, 80 attempts, no warning, no LED ever); the phone
     * that works produced one isolated failure in eight tests. The counter has to tell those apart,
     * so the test replays both sequences through the same arithmetic the app uses.
     */
    @Test
    fun `a working phone never reaches the threshold, and one that cannot work is warned at three`() {
        var streak = 0
        var warned = false
        for (success in listOf(true, true, true, true, true, true, false, true)) {
            streak = DeviceSupport.nextFailureStreak(streak, success)
            if (DeviceSupport.failuresAreConclusive(streak)) warned = true
        }
        assertFalse("a phone that lit up six times was declared broken by one fluke", warned)

        streak = 0
        repeat(3) { streak = DeviceSupport.nextFailureStreak(streak, success = false) }
        assertTrue(
            "three failures in a row did not reach the threshold",
            DeviceSupport.failuresAreConclusive(streak),
        )
    }

    @Test
    fun `a success clears the count, because a phone that lit up works`() {
        assertEquals(0, DeviceSupport.nextFailureStreak(7, success = true))
        assertEquals(1, DeviceSupport.nextFailureStreak(0, success = false))
        assertEquals(2, DeviceSupport.nextFailureStreak(1, success = false))
    }

    @Test
    fun `three is the threshold, and the smallest number the data allows`() {
        assertEquals(3, DeviceSupport.CONCLUSIVE_FAILURES)
        // Below it: the isolated failure a working phone produced is never read as a verdict.
        assertFalse(DeviceSupport.failuresAreConclusive(2))
        // At it: the phones that never emitted anything (80, 10 and 6 in a row) are warned at once.
        assertTrue(DeviceSupport.failuresAreConclusive(3))
        assertTrue(DeviceSupport.failuresAreConclusive(80))
    }

    @Test
    fun `the standing band takes the test's own verdict as well as the family`() {
        // The case the family rule cannot see, and the one the owner asked for: a Xiaomi phone with
        // no service behind it gets the band once its own test has said so three times.
        assertFalse(DeviceSupport.shouldWarn("Xiaomi", "POCO", failuresInARow = 0))
        assertFalse(DeviceSupport.shouldWarn("Xiaomi", "POCO", failuresInARow = 2))
        assertTrue(DeviceSupport.shouldWarn("Xiaomi", "POCO", failuresInARow = 3))
        // A non-Xiaomi phone needs no test at all, and one attempt does not change the verdict.
        assertTrue(DeviceSupport.shouldWarn("INFINIX", "Infinix", failuresInARow = 0))
        assertTrue(DeviceSupport.shouldWarn("INFINIX", "Infinix", failuresInARow = 1))
        // AC5 still holds: an identity that could not be read is never reported as unsupported.
        assertFalse(DeviceSupport.shouldWarn(null, "", failuresInARow = 0))
    }

    @Test
    fun `the full-screen notice appears once, and never on a device that may work`() {
        // First launch on a phone whose identity is known to be outside the family.
        assertTrue(DeviceSupport.shouldShowScreen("Infinix", "Infinix", acknowledged = false))
        // "Continue anyway" was tapped: the standing band stays, the screen does not come back.
        assertFalse(DeviceSupport.shouldShowScreen("Infinix", "Infinix", acknowledged = true))
        // A Xiaomi-family device is never shown it.
        assertFalse(DeviceSupport.shouldShowScreen("Xiaomi", "POCO", acknowledged = false))
        // Nor is a device whose identity could not be read: it may well have the ring, and a wrong
        // full-screen stop on a working phone would be the exact failure this rule exists to avoid.
        assertFalse(DeviceSupport.shouldShowScreen(null, "", acknowledged = false))
        assertFalse(DeviceSupport.shouldShowScreen("  ", null, acknowledged = false))
    }
}
