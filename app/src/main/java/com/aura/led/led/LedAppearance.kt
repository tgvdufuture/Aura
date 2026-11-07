package com.aura.led.led

import kotlin.math.PI
import kotlin.math.cos

/**
 * How a colour is presented, kept pure so the arithmetic is testable without Android.
 *
 * Brightness scales the RGB channels Aura sends. It deliberately does not touch HyperOS's
 * `lamp_effect_brightness`: that global setting also drives charging, call and rhythm scenes, and
 * changing it makes the ring flash white. Dimming our own colours keeps Aura's light quieter
 * without moving the system's.
 *
 * A fade is a short series of dark-to-full frames through `setColorCommon`, the only lights
 * transaction without an auto-off timer (see [ShizukuLEDController.fade]). A breath uses the same
 * transaction, one frame per [BREATH_FRAME_MS], so it never re-arms the camera auto-off that made
 * it cut.
 */
object LedAppearance {
    const val MIN_BRIGHTNESS_PERCENT = 10
    const val MAX_BRIGHTNESS_PERCENT = 100
    const val DEFAULT_BRIGHTNESS_PERCENT = 100

    /** A fade long enough to read as a fade, short enough to leave the light visibly on. */
    const val FADE_MS = 500L
    private const val MIN_FADE_MS = 100L

    /**
     * One slow inhale + exhale, and the interval between two frames of the emulated breath.
     *
     * Frames go through `setColorCommon`, which has no auto-off timer: one frame every 150 ms makes
     * a calm 3 s breath without the cuts a camera-style call per tick caused.
     */
    const val BREATH_CYCLE_MS = 3_000L
    const val BREATH_FRAME_MS = 150L
    private const val BREATH_FRAMES = (BREATH_CYCLE_MS / BREATH_FRAME_MS).toInt()

    /**
     * The ring never goes fully dark inside a breath: a black trough reads as a cut, not a pause.
     * 5 % keeps a trace of colour at the bottom without flattening the dip (owner feedback: 15 %
     * did not fall far enough).
     */
    const val BREATH_MIN_LEVEL = 0.05f

    fun clampBrightness(percent: Int): Int =
        percent.coerceIn(MIN_BRIGHTNESS_PERCENT, MAX_BRIGHTNESS_PERCENT)

    /**
     * Fade length for a light that lasts [onMs]: a one-second light must not spend all of itself
     * fading, so the fade shrinks with the light instead of swallowing it.
     */
    fun fadeMsFor(onMs: Long): Long =
        minOf(FADE_MS, onMs / 3).coerceAtLeast(MIN_FADE_MS)

    /** Dims the RGB channels, leaving alpha alone; the lights service overwrites alpha anyway. */
    fun dim(color: Int, percent: Int): Int =
        scale(color, clampBrightness(percent) / 100f)

    /**
     * Level of an emulated breath at [frame]: 0 at the trough, 1 at the peak, sine-shaped so the
     * change reads as a breath instead of a step. The camera ring ignores its own breath mode on
     * HyperOS, which is why the pulse is drawn frame by frame (see `ShizukuLEDController`).
     */
    fun breathLevel(frame: Int): Float {
        val phase = ((frame % BREATH_FRAMES) + BREATH_FRAMES) % BREATH_FRAMES
        return ((1 - cos(2 * PI * phase / BREATH_FRAMES)) / 2).toFloat()
    }

    /** The colour of one breath frame: the sine envelope, lifted off black by [BREATH_MIN_LEVEL]. */
    fun breathColor(color: Int, level: Float): Int =
        fadeColor(color, BREATH_MIN_LEVEL + (1f - BREATH_MIN_LEVEL) * level.coerceIn(0f, 1f))

    /** Colour at [progress] through a fade. Zero is a real clear, not "black with alpha". */
    fun fadeColor(color: Int, progress: Float): Int {
        val factor = progress.coerceIn(0f, 1f)
        if (factor == 0f) return 0
        return scale(color, factor)
    }

    private fun scale(color: Int, factor: Float): Int {
        val alpha = color and 0xFF000000.toInt()
        val red = (((color shr 16) and 0xFF) * factor).toInt()
        val green = (((color shr 8) and 0xFF) * factor).toInt()
        val blue = ((color and 0xFF) * factor).toInt()
        return alpha or (red shl 16) or (green shl 8) or blue
    }
}
