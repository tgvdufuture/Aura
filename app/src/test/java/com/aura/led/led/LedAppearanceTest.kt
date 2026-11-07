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
    fun `a breath starts dark, peaks at full, and returns to dark`() {
        assertEquals(0f, LedAppearance.breathLevel(0), 0.001f)
        assertTrue("the first quarter has to rise, not jump", LedAppearance.breathLevel(5) in 0.3f..0.7f)
        assertEquals(1f, LedAppearance.breathLevel(10), 0.001f)
        assertEquals(0f, LedAppearance.breathLevel(20), 0.001f)
        // The envelope repeats, and a negative frame is a trough, not a crash.
        assertEquals(LedAppearance.breathLevel(3), LedAppearance.breathLevel(23), 0.0001f)
        assertEquals(LedAppearance.breathLevel(19), LedAppearance.breathLevel(-1), 0.0001f)
    }

    @Test
    fun `the breath recolours a claimed strip instead of re-arming the camera timer`() {
        val led = source("app/src/main/java/com/aura/led/led/Led.kt").readText()
        val breathing = led.substringAfter("private fun startBreathing")
            .substringBefore("private fun turnOffLight")

        assertTrue(
            "MODE_BREATH is accepted by the service but the camera scene ignores it",
            led.contains("Animations.BREATHING -> startBreathing(color)") &&
                !led.contains("MODE_BREATH,"),
        )
        assertTrue(
            "a camera-style call per frame re-arms the auto-off timer, which is the documented cause of the cuts",
            breathing.contains("transactColorCommonBinder(") &&
                !breathing.contains("STYLE_MARKER_RESET") &&
                !led.contains("STYLE_MARKER_RESET"),
        )
        assertTrue(
            "the strip must be claimed once, for the whole breath, before the frames",
            breathing.indexOf("transactLightBinder(0, MODE_STEADY, onMs.toInt(), 0, STYLE_CAMERA)") in
                0 until breathing.indexOf("animationJob"),
        )
        assertTrue(
            "the envelope runs at the frame interval, with a trough that keeps the ring lit",
            breathing.contains("LedAppearance.BREATH_FRAME_MS") &&
                breathing.contains("LedAppearance.breathColor") &&
                breathing.contains("LedAppearance.breathLevel"),
        )
        assertTrue(
            "a cancelled breath leaves its last frame on the timer-free strip",
            led.contains("breathOnRhythmStrip") &&
                led.contains("if (wasFading || wasBreathing) transactColorCommon(0)"),
        )
        assertTrue(
            "the permission check must run once, not between two frames",
            breathing.indexOf("hasShizukuAccess()") in 0 until breathing.indexOf("animationJob"),
        )
    }

    @Test
    fun `a breath never fully extinguishes the ring`() {
        val red = 0xFFFF0000.toInt()
        // The trough keeps 5 % of the colour: a black bottom reads as a cut, not an exhale.
        assertEquals(0xFF0C0000.toInt(), LedAppearance.breathColor(red, 0f))
        assertEquals(red, LedAppearance.breathColor(red, 1f))
        // Brightness still applies under the envelope.
        assertEquals(0xFF060000.toInt(), LedAppearance.breathColor(LedAppearance.dim(red, 50), 0f))
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
