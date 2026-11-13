package com.aura.led.led

import android.os.IBinder
import android.os.Parcel
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import com.aura.led.root.RootManager

/** Frozen after Phase 0. */
interface LEDController {
    fun setColor(colorHex: String): Result<Boolean>
    fun startAnimation(animationId: String, colorHex: String): Result<Boolean>
    fun stop(): Result<Boolean>
}

data class LedCommand(
    val colorHex: String,
    val animationId: String?,
)

object Animations {
    const val BREATHING = "breathing"
    const val CHARGING = "charging"
    const val RAINBOW = "rainbow"
    const val POLICE = "police"
}

object ColorMapper {
    private val regex = Regex("#[0-9a-fA-F]{6}")

    fun hexToInt(hex: String): Int? =
        if (regex.matches(hex)) hex.substring(1).toLong(16).toInt() else null

    fun intToHex(value: Int): String =
        "#" + (value and 0xFFFFFF).toString(16).padStart(6, '0')
}

/**
 * Drives the back LED strip through the HyperOS `miui.lights.ILightsManager` service
 * (`setCustomLight`, transaction 4), called through Shizuku or a root shell.
 *
 * The Parcel is written manually to avoid linking against the hidden `miui.lights`
 * platform class (hidden-API enforcement would block the generated AIDL stub).
 */
class ShizukuLEDController : LEDController {

    companion object {
        /** Shared timeout applied to static colors; auto-extinguishes after this duration. */
        @Volatile
        var ledTimeoutMs: Long = 10_000L

        /** 10–100: Aura dims its own RGB values, leaving HyperOS's scenes at their brightness. */
        @Volatile
        var ledBrightnessPercent: Int = LedAppearance.DEFAULT_BRIGHTNESS_PERCENT

        /** Steady colours fade in and out instead of switching on. Opt-in. */
        @Volatile
        var fadeEnabled: Boolean = false

        private const val SERVICE = "miui.lights.ILightsManager"
        const val DESCRIPTOR = "miui.lights.ILightsManager"
        const val TRANSACTION_setCustomLight = 4
        const val TRANSACTION_setColorCommon = 2
        const val STYLE_RHYTHM = 3
        const val PKG = "com.android.camera"
        const val STYLE_CAMERA = 12

        const val MODE_STEADY = 0
        const val MODE_FLASH = 1

        // miui.lights schedules an auto-off timer at onMs on every call. A large onMs
        // while updating quickly makes the ring appear stuck on the first color (see the
        // miui.lights timing note), so the live drag preview uses a short onMs that matches
        // the update rate; the settle step re-sends the same color with a long onMs.
        private const val PREVIEW_DRAG_ON_MS = 150
        private const val PREVIEW_SETTLE_ON_MS = 60_000
        // One-shot preview for the default swatches: lights ~1.5s then auto-off.
        private const val PREVIEW_FLASH_ON_MS = 1_500

        /**
         * Settle time required between the clear transaction and the color
         * transaction. Verified by experiment on HyperOS (2026-08-27, model
         * 2511FPC34G): a color sent back-to-back right after the clear is
         * silently dropped by HyperLightsService even though the binder reply
         * reports success (the LED flashes briefly then stays dark). Spacing
         * both transactions by >=~0.4s makes the color stick reliably.
         */
        private const val CLEAR_SETTLE_MS = 350L

        /** 20 frames over 500 ms: 25 ms per step, fast enough for `setColorCommon`. */
        private const val FADE_STEPS = 20
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var animationJob: Job? = null

    /** Runs the fade-in/hold/fade-out frames; cancelled by every new command. */
    @Volatile
    private var fadeJob: Job? = null

    /** True while a breath lights the rhythm strip, which has no timer and must be cleared by us. */
    @Volatile
    private var breathOnRhythmStrip = false

    override fun setColor(colorHex: String): Result<Boolean> {
        val color = ColorMapper.hexToInt(colorHex)
            ?: return Result.failure(IllegalArgumentException("Bad color: $colorHex"))
        cancelEffects()
        val dimmed = LedAppearance.dim(color, ledBrightnessPercent)
        if (fadeEnabled) return fade(dimmed)
        return setWithSpacedClear(dimmed, MODE_STEADY, ledTimeoutMs.toInt(), 0)
    }

    /**
     * Live preview via [setCustomLight] on the camera ring (styleType 12). Stays
     * lit until [stopPreview]. Note: HyperLightsService ignores a color change at
     * the same priority as the active strip, so [setCustomLight] clears (color 0)
     * first — see its doc.
     */
    /**
     * Live preview via [setCustomLight] on the camera ring (styleType 12), using a short
     * on-duration so rapid color changes track instead of appearing stuck. HyperLightsService
     * skips a color change at the same priority as the active strip, so [setWithClear] clears
     * (color 0) first.
     */
    fun previewColor(colorHex: String): Result<Boolean> {
        val color = ColorMapper.hexToInt(colorHex)
            ?: return Result.failure(IllegalArgumentException("Bad color: $colorHex"))
        cancelEffects()
        return setWithClear(LedAppearance.dim(color, ledBrightnessPercent), MODE_STEADY, PREVIEW_DRAG_ON_MS)
    }

    /**
     * Re-applies the settled preview color with a long on-duration so it stays lit after the
     * user stops dragging. Mirrors [previewColor]'s clear-then-set to avoid the same-priority
     * skip.
     */
    fun settleColor(colorHex: String): Result<Boolean> {
        val color = ColorMapper.hexToInt(colorHex)
            ?: return Result.failure(IllegalArgumentException("Bad color: $colorHex"))
        return setWithClear(LedAppearance.dim(color, ledBrightnessPercent), MODE_STEADY, PREVIEW_SETTLE_ON_MS)
    }

    /**
     * Short one-shot preview for the default color swatches: lights the ring for a
     * moment, then the service's own auto-off timer extinguishes it (no settle step).
     */
    fun flashColor(colorHex: String): Result<Boolean> {
        val color = ColorMapper.hexToInt(colorHex)
            ?: return Result.failure(IllegalArgumentException("Bad color: $colorHex"))
        cancelEffects()
        return setWithClear(LedAppearance.dim(color, ledBrightnessPercent), MODE_STEADY, PREVIEW_FLASH_ON_MS)
    }

    override fun startAnimation(animationId: String, colorHex: String): Result<Boolean> {
        val color = ColorMapper.hexToInt(colorHex)
            ?: return Result.failure(IllegalArgumentException("Bad color: $colorHex"))
        return when (animationId) {
            Animations.BREATHING -> startBreathing(color)
            Animations.CHARGING -> {
                cancelEffects()
                setWithSpacedClear(
                    LedAppearance.dim(color, ledBrightnessPercent),
                    MODE_FLASH,
                    1000,
                    1000,
                )
            }
            Animations.RAINBOW -> startEmulated(rainbowColors())
            Animations.POLICE -> startEmulated(listOf(0xFF0000, 0x0000FF))
            else -> setColor(colorHex) // unsupported animation -> static color
        }
    }

    override fun stop(): Result<Boolean> {
        cancelEffects()
        return turnOffLight()
    }

    /** Extinguishes the camera ring after a preview. */
    fun stopPreview(): Result<Boolean> {
        cancelEffects()
        return turnOffLight()
    }

    private fun cancelEffects() {
        animationJob?.cancel()
        animationJob = null
        val wasFading = fadeJob?.isActive == true
        fadeJob?.cancel()
        fadeJob = null
        val wasBreathing = breathOnRhythmStrip
        breathOnRhythmStrip = false
        // A cancelled effect leaves its last frame on the rhythm strip. Clear that zone too: the
        // next command drives the camera style, which would otherwise leave the old colour behind.
        if (wasFading || wasBreathing) transactColorCommon(0)
    }

    /**
     * Draws a breath by recolouring an already-claimed strip, one frame at a time.
     *
     * `setCustomLight` schedules an auto-off timer on every call (see the timing note above), so
     * animation-speed frames through it flicker — and the marker call that carried `onMs = 0`
     * extinguished the strip outright, which is the cut users still saw (report 2026-11-07).
     * `setColorCommon` has no timer at all: one camera-style call takes the strip over for the
     * whole breath, then each frame only changes its colour. That is the same two steps as [fade],
     * which is why the fade stays smooth at 25 ms per step.
     *
     * `flashMode = MODE_BREATH` is still not used: the camera scene ignores it and the effect is
     * identical to Flashing (report 2026-11-06).
     */
    private fun startBreathing(color: Int): Result<Boolean> {
        val useShizuku = hasShizukuAccess()
        if (!useShizuku && !RootManager.isAvailable()) {
            return Result.failure(IllegalStateException("Shizuku and root unavailable"))
        }
        cancelEffects()
        val dimmed = LedAppearance.dim(color, ledBrightnessPercent)
        val onMs = ledTimeoutMs
        // Resolve the backend once: the Shizuku permission check is two IPC round-trips, and
        // running it between two frames would make the breath stutter.
        fun sendFrame(frameColor: Int): Result<Boolean> =
            if (useShizuku) transactColorCommonBinder(frameColor) else transactColorCommonAsRoot(frameColor)
        // Take the strip over for the whole breath: the rhythm transaction has no timer, so the
        // camera auto-off would otherwise extinguish the breath at its own deadline.
        val claimed =
            if (useShizuku) transactLightBinder(0, MODE_STEADY, onMs.toInt(), 0, STYLE_CAMERA)
            else transactLightAsRoot(0, MODE_STEADY, onMs.toInt(), 0, STYLE_CAMERA)
        if (claimed.isFailure) return claimed
        breathOnRhythmStrip = true
        animationJob = scope.launch {
            val deadline = System.currentTimeMillis() + onMs
            var frame = 0
            while (isActive && System.currentTimeMillis() < deadline) {
                sendFrame(LedAppearance.breathColor(dimmed, LedAppearance.breathLevel(frame)))
                    .onFailure { Log.e("AuraLed", "breath frame failed", it) }
                delay(LedAppearance.BREATH_FRAME_MS)
                frame++
            }
            if (isActive) {
                breathOnRhythmStrip = false
                // The rhythm strip has no timer: the last frame stays lit until we clear it.
                sendFrame(0)
                turnOffLight()
            }
        }
        return Result.success(true)
    }

    /**
     * Reliably extinguishes the light: black with a real on-duration. A bare
     * (0,0,0,0) with onMs=0 — or a 1ms duration — is ignored by miui.lights, so use
     * the same timeout as [setColor]; black reads as "off" immediately and the timer
     * fires as a harmless no-op.
     */
    private fun turnOffLight(): Result<Boolean> =
        setCustomLight(0, MODE_STEADY, ledTimeoutMs.toInt(), 0)

    /** Emulates a looping animation by sending stepped colors until the timeout expires. */
    private fun startEmulated(colors: List<Int>): Result<Boolean> {
        cancelEffects()
        if (colors.isEmpty()) return Result.failure(IllegalArgumentException("Empty animation"))
        val stepMs = 400L
        val dimmed = colors.map { LedAppearance.dim(it, ledBrightnessPercent) }
        animationJob = scope.launch {
            val deadline = System.currentTimeMillis() + ledTimeoutMs
            var i = 0
            while (isActive && System.currentTimeMillis() < deadline) {
                setCustomLight(dimmed[i % dimmed.size], MODE_STEADY, stepMs.toInt(), 0)
                delay(stepMs)
                i++
            }
            turnOffLight()
        }
        return Result.success(true)
    }

    private fun rainbowColors(): List<Int> =
        (0 until 360 step 30).map { h -> android.graphics.Color.HSVToColor(floatArrayOf(h.toFloat(), 1f, 1f)) }

    /**
     * Sets a light after clearing the strip, leaving a settle gap between both
     * transactions ([CLEAR_SETTLE_MS]). Used for one-shot notification emissions:
     * back-to-back clear+color are swallowed by HyperLightsService, so without the
     * gap notifications light the LED for an instant and go dark immediately.
     */
    private fun setWithSpacedClear(color: Int, mode: Int, onMs: Int, offMs: Int): Result<Boolean> {
        if (!hasShizukuAccess() && !RootManager.isAvailable()) {
            return Result.failure(IllegalStateException("Shizuku and root unavailable"))
        }
        val cleared = transactLight(0, MODE_STEADY, ledTimeoutMs.toInt(), 0)
        if (cleared.isFailure) return cleared
        // Block briefly; callers run on background dispatcher threads only.
        runCatching { Thread.sleep(CLEAR_SETTLE_MS) }
        return transactLight(color, mode, onMs, offMs)
    }

    /**
     * Sets a light and reports success/failure. Before applying a non-zero color we
     * clear the strip (color 0) first: HyperLightsService skips a `setCustomLight`
     * whose style/priority matches the currently active strip ("setLedStripLocked SKIP"),
     * which silently drops pure color changes. Clearing resets that state.
     */
    private fun setCustomLight(color: Int, mode: Int, onMs: Int, offMs: Int): Result<Boolean> {
        if (!hasShizukuAccess() && !RootManager.isAvailable()) {
            return Result.failure(IllegalStateException("Shizuku and root unavailable"))
        }
        if (color != 0) {
            val cleared = transactLight(0, MODE_STEADY, ledTimeoutMs.toInt(), 0)
            if (cleared.isFailure) return cleared
        }
        return transactLight(color, mode, onMs, offMs)
    }

    /**
     * Clears the camera ring (color 0) then applies [color], both with the same [onMs].
     * The clear resets HyperLightsService's same-priority "SKIP" state and the matching
     * onMs keeps the clear's own auto-off timer from preempting the color.
     */
    private fun setWithClear(color: Int, mode: Int, onMs: Int): Result<Boolean> {
        if (!hasShizukuAccess() && !RootManager.isAvailable()) {
            return Result.failure(IllegalStateException("Shizuku and root unavailable"))
        }
        val cleared = transactLight(0, MODE_STEADY, onMs, 0)
        if (cleared.isFailure) return cleared
        return transactLight(color, mode, onMs, 0)
    }

    /**
     * Fades a steady colour in, holds it, then fades it out through `setColorCommon` (styleType 3).
     *
     * `setCustomLight` re-arms its auto-off timer on every call, which makes animation-speed frames
     * flicker; `setColorCommon` has no timer at all. The price is that the final clear is ours to
     * send, and that a cancelled fade must clear its own zone (see [cancelEffects]).
     */
    private fun fade(color: Int): Result<Boolean> {
        if (!hasShizukuAccess() && !RootManager.isAvailable()) {
            return Result.failure(IllegalStateException("Shizuku and root unavailable"))
        }
        val onMs = ledTimeoutMs
        val fadeMs = LedAppearance.fadeMsFor(onMs)
        val stepMs = (fadeMs / FADE_STEPS).coerceAtLeast(10L)
        // Take the strip over before fading: a previous camera-style light (and its auto-off timer)
        // would otherwise stay lit under the fade and clear the ring at its own deadline.
        val cleared = transactLight(0, MODE_STEADY, onMs.toInt(), 0)
        if (cleared.isFailure) return cleared
        // First frame synchronously: the caller needs to know the command reached the service.
        val first = transactColorCommon(LedAppearance.fadeColor(color, 1f / FADE_STEPS))
        if (first.isFailure) return first
        fadeJob = scope.launch {
            for (step in 2..FADE_STEPS) {
                delay(stepMs)
                transactColorCommon(LedAppearance.fadeColor(color, step.toFloat() / FADE_STEPS))
                    .onFailure { Log.e("AuraLed", "fade-in frame failed", it) }
            }
            delay((onMs - 2 * fadeMs).coerceAtLeast(0L))
            for (step in FADE_STEPS - 1 downTo 0) {
                delay(stepMs)
                transactColorCommon(LedAppearance.fadeColor(color, step.toFloat() / FADE_STEPS))
                    .onFailure { Log.e("AuraLed", "fade-out frame failed", it) }
            }
        }
        return Result.success(true)
    }

    /** `setColorCommon` (transaction 2): the flicker-free path for animation-speed colour changes. */
    private fun transactColorCommon(color: Int): Result<Boolean> =
        if (hasShizukuAccess()) transactColorCommonBinder(color) else transactColorCommonAsRoot(color)

    /** Binder path without the permission re-check, used for the back-to-back breath frames. */
    private fun transactColorCommonBinder(color: Int): Result<Boolean> {
        val service = serviceBinder()
            ?: return Result.failure(IllegalStateException("miui.lights service unavailable"))
        return runCatching {
            val binder: IBinder = ShizukuBinderWrapper(service)
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(color)
                data.writeString(PKG)
                data.writeInt(STYLE_RHYTHM)
                data.writeInt(0)
                binder.transact(TRANSACTION_setColorCommon, data, reply, 0)
                reply.readException()
                true
            } finally {
                data.recycle()
                reply.recycle()
            }
        }.onFailure { Log.e("AuraLed", "setColorCommon failed", it) }
    }

    private fun transactColorCommonAsRoot(color: Int): Result<Boolean> {
        val command = "service call $SERVICE $TRANSACTION_setColorCommon " +
            "i32 $color s16 $PKG i32 $STYLE_RHYTHM i32 0"
        // mapCatching, not map: a throw inside `map` escapes the Result and reaches the caller
        // unwrapped, which crashed the app on devices without the service (issue #5).
        return RootManager.run(command).mapCatching { result ->
            if (result.exitCode != 0) {
                throw IllegalStateException("service call exited with ${result.exitCode}: ${result.stdout}")
            }
            true
        }.onFailure { Log.e("AuraLed", "root setColorCommon failed", it) }
    }

    private fun transactLight(
        color: Int,
        mode: Int,
        onMs: Int,
        offMs: Int,
        styleType: Int = STYLE_CAMERA,
    ): Result<Boolean> =
        if (hasShizukuAccess()) {
            transactLightBinder(color, mode, onMs, offMs, styleType)
        } else {
            transactLightAsRoot(color, mode, onMs, offMs, styleType)
        }

    /** Binder path without the permission re-check, used for the back-to-back breath frames. */
    private fun transactLightBinder(
        color: Int,
        mode: Int,
        onMs: Int,
        offMs: Int,
        styleType: Int = STYLE_CAMERA,
    ): Result<Boolean> {
        val service = serviceBinder()
            ?: return Result.failure(IllegalStateException("miui.lights service unavailable"))
        return runCatching {
            val binder: IBinder = ShizukuBinderWrapper(service)

            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(color)          // color (ARGB, low 24 bits used)
                data.writeInt(mode)           // flashMode: 0 steady, 1 flash, 2 breath
                data.writeInt(onMs)           // on duration
                data.writeInt(offMs)          // off duration
                data.writeInt(0)              // brightnessMode
                data.writeString(PKG)         // must be com.android.camera
                data.writeInt(styleType)      // 12 = camera (force); -1 = marker reset
                data.writeInt(0)              // userId
                binder.transact(TRANSACTION_setCustomLight, data, reply, 0)
                reply.readException()
                true
            } finally {
                data.recycle()
                reply.recycle()
            }
        }.onFailure { Log.e("AuraLed", "setCustomLight failed", it) }
    }

    private fun hasShizukuAccess(): Boolean = runCatching {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private fun transactLightAsRoot(
        color: Int,
        mode: Int,
        onMs: Int,
        offMs: Int,
        styleType: Int = STYLE_CAMERA,
    ): Result<Boolean> {
        val command = "service call $SERVICE $TRANSACTION_setCustomLight " +
            "i32 $color i32 $mode i32 $onMs i32 $offMs i32 0 " +
            "s16 $PKG i32 $styleType i32 0"
        // mapCatching, not map: a throw inside `map` escapes the Result and reaches the caller
        // unwrapped, which crashed the app on devices without the service (issue #5).
        return RootManager.run(command).mapCatching { result ->
            if (result.exitCode != 0) {
                throw IllegalStateException("service call exited with ${result.exitCode}: ${result.stdout}")
            }
            true
        }.onFailure { Log.e("AuraLed", "root setCustomLight failed", it) }
    }

    /**
     * Cached miui.lights binder. Re-fetching it on every command costs a Shizuku IPC
     * round-trip (~30ms), which shows up as a visible "off" gap between the clear and
     * the new color during previews. Caching keeps that gap to a few ms.
     */
    @Volatile
    private var cachedService: IBinder? = null

    private fun serviceBinder(): IBinder? =
        cachedService ?: runCatching { SystemServiceHelper.getSystemService(SERVICE) }
            .getOrNull()
            ?.also { cachedService = it }
}
