package com.aura.led.service

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #5: the foreground service is started from `MainActivity.onCreate`, and its `startForeground`
 * was the one unguarded call in `Service.onCreate`. On a ROM that refuses the `specialUse` foreground
 * type the exception killed the process, and `START_STICKY` restarted it — Aura was unopenable until
 * uninstalled, which is exactly what the report says. The refusal cannot be reproduced here (no
 * instrumented source set, no such ROM), so the guard is read from the source.
 */
class ForegroundServiceSafetyTest {

    private val service = serviceSource().readText()

    @Test
    fun `a refused foreground start stops the service instead of crashing the process`() {
        val created = service.substringAfter("override fun onCreate()")
            .substringBefore("override fun onStartCommand")

        val guarded = created.indexOf("runCatching { startForeground(")
        assertTrue(
            "startForeground is not guarded: a refused foreground type takes the app down again",
            guarded >= 0,
        )
        assertTrue(
            "the service does not stop itself when the notification is refused",
            created.substringAfter("if (!started)").contains("stopSelf()"),
        )
        assertTrue(
            "the service is marked running before the system accepted the notification",
            created.indexOf("running = true") > guarded,
        )
    }

    private fun serviceSource(): File {
        val path = "app/src/main/java/com/aura/led/service/AuraForegroundService.kt"
        return listOf(File(path), File("../$path"))
            .firstOrNull { it.isFile }
            ?: error("$path not found")
    }
}
