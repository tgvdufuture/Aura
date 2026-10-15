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
