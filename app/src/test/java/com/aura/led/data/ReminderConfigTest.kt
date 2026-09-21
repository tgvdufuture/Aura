package com.aura.led.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderConfigTest {

    // ---- ReminderInterval.clamp ----

    @Test
    fun `clamp rounds down to 5s step and enforces floor`() {
        assertEquals(5_000L, ReminderInterval.clamp(3_000L))
        assertEquals(5_000L, ReminderInterval.clamp(7_999L))
        assertEquals(10_000L, ReminderInterval.clamp(10_000L))
        assertEquals(15_000L, ReminderInterval.clamp(15_000L))
    }

    @Test
    fun `clamp enforces ceiling`() {
        assertEquals(120_000L, ReminderInterval.clamp(500_000L))
        assertEquals(120_000L, ReminderInterval.clamp(121_000L))
    }

    @Test
    fun `clamp keeps in-range values unchanged`() {
        assertEquals(60_000L, ReminderInterval.clamp(60_000L))
        assertEquals(120_000L, ReminderInterval.clamp(120_000L))
        assertEquals(5_000L, ReminderInterval.clamp(5_000L))
    }

    @Test
    fun `defaults match the frozen PRD decisions`() {
        assertEquals(false, ReminderConfig().enabled)
        assertEquals(15_000L, ReminderConfig().intervalMs)
        assertEquals("23:00", ReminderConfig().quietStart)
        assertEquals("07:00", ReminderConfig().quietEnd)
    }

    // ---- QuietHours.parseHHmm ----

    @Test
    fun `parseHHmm accepts valid values`() {
        assertEquals(0, QuietHours.parseHHmm("00:00"))
        assertEquals(23 * 60 + 59, QuietHours.parseHHmm("23:59"))
        assertEquals(7 * 60, QuietHours.parseHHmm("7:00"))
        assertEquals(13 * 60 + 30, QuietHours.parseHHmm("13:30"))
    }

    @Test
    fun `parseHHmm rejects malformed values`() {
        assertNull(QuietHours.parseHHmm(""))
        assertNull(QuietHours.parseHHmm("abc"))
        assertNull(QuietHours.parseHHmm("24:00"))
        assertNull(QuietHours.parseHHmm("12:60"))
        assertNull(QuietHours.parseHHmm("12"))
        assertNull(QuietHours.parseHHmm("-1:00"))
    }

    // ---- QuietHours.isInRange ----

    @Test
    fun `same window bounds means quiet hours disabled`() {
        assertFalse(QuietHours.isInRange(23 * 60, 23 * 60, 23 * 60))
        assertFalse(QuietHours.isInRange(12 * 60, 5 * 60, 5 * 60))
    }

    @Test
    fun `normal daytime window`() {
        val start = 9 * 60
        val end = 12 * 60
        assertFalse(QuietHours.isInRange(8 * 60 + 59, start, end))
        assertTrue(QuietHours.isInRange(9 * 60, start, end))
        assertTrue(QuietHours.isInRange(11 * 60 + 59, start, end))
        assertFalse(QuietHours.isInRange(12 * 60, start, end))
    }

    @Test
    fun `window wrapping midnight`() {
        val start = 23 * 60 // 23:00
        val end = 7 * 60    // 07:00
        assertFalse(QuietHours.isInRange(22 * 60 + 59, start, end))
        assertTrue(QuietHours.isInRange(23 * 60, start, end))
        assertTrue(QuietHours.isInRange(0, start, end))
        assertTrue(QuietHours.isInRange(3 * 60 + 30, start, end))
        assertTrue(QuietHours.isInRange(6 * 60 + 59, start, end))
        assertFalse(QuietHours.isInRange(7 * 60, start, end))
        assertFalse(QuietHours.isInRange(12 * 60, start, end))
    }
}
