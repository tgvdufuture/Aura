package com.aura.led.led

/**
 * How a colour is presented, kept pure so the arithmetic is testable without Android.
 *
 * Brightness scales the RGB channels Aura sends. It deliberately does not touch HyperOS's
 * `lamp_effect_brightness`: that global setting also drives charging, call and rhythm scenes, and
 * changing it makes the ring flash white. Dimming our own colours keeps Aura's light quieter
 * without moving the system's.
 *
 * A fade is a short series of dark-to-full frames through `setColorCommon`, the only lights
 * transaction without an auto-off timer (see [ShizukuLEDController.fade]).
 */
object LedAppearance {
    const val MIN_BRIGHTNESS_PERCENT = 10
    const val MAX_BRIGHTNESS_PERCENT = 100
    const val DEFAULT_BRIGHTNESS_PERCENT = 100

    /** A fade long enough to read as a fade, short enough to leave the light visibly on. */
    const val FADE_MS = 500L
    private const val MIN_FADE_MS = 100L

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
