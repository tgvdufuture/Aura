package com.aura.led.led

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Brightness and fade are arithmetic before they are Binder calls. What can be checked without a
 * phone is here: the clamp the settings UI relies on, the scaling, and the two limits a fade must
 * respect (it always ends exactly on the target, and it never takes a short light entirely).
 *
 * The wiring test reads [Led.kt] as text: this project has no instrumented source set, and the one
 * fact that matters — the fade uses the transaction without an auto-off timer — would otherwise be
 * unverifiable off-device.
 */
class LedAppearanceTest {

    @Test
    fun `brightness stays inside the range the ring can show`() {
        assertEquals(10, LedAppearance.clampBrightness(0))
        assertEquals(10, LedAppearance.clampBrightness(-40))
        assertEquals(55, LedAppearance.clampBrightness(55))
        assertEquals(100, LedAppearance.clampBrightness(140))
    }

    @Test
    fun `dimming scales rgb and keeps the alpha byte`() {
        assertEquals(0xFFFF0000.toInt(), LedAppearance.dim(0xFFFF0000.toInt(), 100))
        assertEquals(0xFF7F0000.toInt(), LedAppearance.dim(0xFFFF0000.toInt(), 50))
        assertEquals(0xFF190000.toInt(), LedAppearance.dim(0xFFFF0000.toInt(), 10))
        // Green and blue scale independently, and alpha is not the brightness channel.
        assertEquals(0xFF0F3F3F.toInt(), LedAppearance.dim(0xFF1F7F7F.toInt(), 50))
    }

    @Test
    fun `a fade starts at a real clear and ends on the target`() {
        val red = 0xFFFF0000.toInt()
        assertEquals(0, LedAppearance.fadeColor(red, 0f))
        assertEquals(0, LedAppearance.fadeColor(red, -1f))
        assertEquals(red, LedAppearance.fadeColor(red, 1f))
        assertEquals(0xFF7F0000.toInt(), LedAppearance.fadeColor(red, 0.5f))
    }

    @Test
    fun `a one second light does not spend all of itself fading`() {
        assertEquals(333L, LedAppearance.fadeMsFor(1_000L))
        assertEquals(500L, LedAppearance.fadeMsFor(10_000L))
        assertEquals(500L, LedAppearance.fadeMsFor(30_000L))
    }

    @Test
    fun `the fade uses the transaction without an auto-off timer`() {
        val led = source("app/src/main/java/com/aura/led/led/Led.kt").readText()
        assertTrue(
            "fading through setCustomLight would re-arm a timer on every frame and flicker",
            led.contains("TRANSACTION_setColorCommon = 2") &&
                led.contains("STYLE_RHYTHM = 3") &&
                led.contains("transactColorCommon("),
        )
        assertTrue(
            "a fade command must still be dimmed and cancelled like every other effect",
            led.contains("LedAppearance.fadeColor") &&
                led.contains("fadeJob?.cancel()") &&
                led.contains("ledBrightnessPercent"),
        )
    }

    /** Resolves a repository path from the module working directory Gradle uses for unit tests. */
    private fun source(path: String): File {
        val candidates = listOf(File(path), File("../$path"))

        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found (looked in ${candidates.joinToString()})")
    }
}
