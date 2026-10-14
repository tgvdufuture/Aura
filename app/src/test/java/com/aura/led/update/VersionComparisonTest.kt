package com.aura.led.update

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparisonTest {

    @Test
    fun `normalizes the tag shapes GitHub produces`() {
        assertArrayEquals(intArrayOf(0, 6, 0, 0), VersionComparison.normalize("v0.6.0"))
        assertArrayEquals(intArrayOf(0, 6, 0, 0), VersionComparison.normalize("0.6.0"))
        assertArrayEquals(intArrayOf(0, 6, 0, 0), VersionComparison.normalize("v0.6"))
        assertArrayEquals(intArrayOf(1, 2, 3, 4), VersionComparison.normalize(" 1.2.3.4 "))
    }

    @Test
    fun `rejects anything that is not a plain numeric version`() {
        assertNull(VersionComparison.normalize(""))
        assertNull(VersionComparison.normalize("latest"))
        assertNull(VersionComparison.normalize("v0.6.0-beta"))
        assertNull(VersionComparison.normalize("1.2.3.4.5"))
    }

    @Test
    fun `detects a newer release`() {
        assertTrue(VersionComparison.isNewer("0.5.1", "v0.6.0"))
        assertTrue(VersionComparison.isNewer("0.5.1", "v0.6"))
        assertTrue(VersionComparison.isNewer("0.5.1", "v0.5.2"))
        assertTrue(VersionComparison.isNewer("0.5.1", "v1.0.0"))
    }

    @Test
    fun `the installed version never looks older than itself or an older tag`() {
        assertFalse(VersionComparison.isNewer("0.5.1", "v0.5.1"))
        assertFalse(VersionComparison.isNewer("0.5.1", "0.5.1"))
        assertFalse(VersionComparison.isNewer("0.6.0", "v0.5.1"))
        assertFalse(VersionComparison.isNewer("1.0.0", "v0.9.9"))
    }

    @Test
    fun `an unparsable tag on either side never offers an update`() {
        assertFalse(VersionComparison.isNewer("0.5.1", "v0.6.0-beta"))
        assertFalse(VersionComparison.isNewer("dev", "v0.6.0"))
        assertFalse(VersionComparison.isNewer("", "v0.6.0"))
    }
}
