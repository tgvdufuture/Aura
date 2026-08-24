package com.aura.led.service

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NewYearSchedulerTest {

    @Test
    fun `schedules next local new year`() {
        val zone = ZoneId.of("Europe/Paris")
        val now = ZonedDateTime.of(2026, 6, 15, 12, 30, 0, 0, zone).toInstant().toEpochMilli()

        val result = Instant.ofEpochMilli(NewYearScheduler.nextNewYearMillis(now, zone)).atZone(zone)

        assertEquals(ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, zone), result)
    }
}
