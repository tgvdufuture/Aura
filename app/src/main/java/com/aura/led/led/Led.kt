package com.aura.led.led

import android.content.pm.PackageManager
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
 * (`setCustomLight`, transaction 4), called through Shizuku (shell UID is whitelisted).
 *
 * The Parcel is written manually to avoid linking against the hidden `miui.lights`
 * platform class (hidden-API enforcement would block the generated AIDL stub).
 */
class ShizukuLEDController : LEDController {

    companion object {
        /** Shared timeout applied to static colors; auto-extinguishes after this duration. */
        @Volatile
        var ledTimeoutMs: Long = 10_000L

        private const val SERVICE = "miui.lights.ILightsManager"
        const val DESCRIPTOR = "miui.lights.ILightsManager"
        const val TRANSACTION_setCustomLight = 4
        const val PKG = "com.android.camera"
        const val STYLE_CAMERA = 12
        const val MODE_STEADY = 0
        const val MODE_FLASH = 1
        const val MODE_BREATH = 2

        // miui.lights schedules an auto-off timer at onMs on every call. A large onMs
        // while updating quickly makes the ring appear stuck on the first color (see the
        // miui.lights timing note), so the live drag preview uses a short onMs that matches
        // the update rate; the settle step re-sends the same color with a long onMs.
        private const val PREVIEW_DRAG_ON_MS = 150
        private const val PREVIEW_SETTLE_ON_MS = 60_000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var animationJob: Job? = null

    override fun setColor(colorHex: String): Result<Boolean> {
        val color = ColorMapper.hexToInt(colorHex)
            ?: return Result.failure(IllegalArgumentException("Bad color: $colorHex"))
        cancelAnimation()
        return setCustomLight(color, MODE_STEADY, ledTimeoutMs.toInt(), 0)
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
        cancelAnimation()
        return setWithClear(color, MODE_STEADY, PREVIEW_DRAG_ON_MS)
    }

    /**
     * Re-applies the settled preview color with a long on-duration so it stays lit after the
     * user stops dragging. Mirrors [previewColor]'s clear-then-set to avoid the same-priority
     * skip.
     */
    fun settleColor(colorHex: String): Result<Boolean> {
        val color = ColorMapper.hexToInt(colorHex)
            ?: return Result.failure(IllegalArgumentException("Bad color: $colorHex"))
        return setWithClear(color, MODE_STEADY, PREVIEW_SETTLE_ON_MS)
    }

    override fun startAnimation(animationId: String, colorHex: String): Result<Boolean> {
        val color = ColorMapper.hexToInt(colorHex)
            ?: return Result.failure(IllegalArgumentException("Bad color: $colorHex"))
        return when (animationId) {
            Animations.BREATHING -> {
                cancelAnimation()
                setCustomLight(color, MODE_BREATH, 2000, 2000)
            }
            Animations.CHARGING -> {
                cancelAnimation()
                setCustomLight(color, MODE_FLASH, 1000, 1000)
            }
            Animations.RAINBOW -> startEmulated(rainbowColors())
            Animations.POLICE -> startEmulated(listOf(0xFF0000, 0x0000FF))
            else -> setColor(colorHex) // unsupported animation -> static color
        }
    }

    override fun stop(): Result<Boolean> {
        cancelAnimation()
        return turnOffLight()
    }

    /** Extinguishes the camera ring after a preview. */
    fun stopPreview(): Result<Boolean> {
        cancelAnimation()
        return turnOffLight()
    }

    private fun cancelAnimation() {
        animationJob?.cancel()
        animationJob = null
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
        cancelAnimation()
        if (colors.isEmpty()) return Result.failure(IllegalArgumentException("Empty animation"))
        val stepMs = 400L
        animationJob = scope.launch {
            val deadline = System.currentTimeMillis() + ledTimeoutMs
            var i = 0
            while (isActive && System.currentTimeMillis() < deadline) {
                setCustomLight(colors[i % colors.size], MODE_STEADY, stepMs.toInt(), 0)
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
     * Sets a light and reports success/failure. Before applying a non-zero color we
     * clear the strip (color 0) first: HyperLightsService skips a `setCustomLight`
     * whose style/priority matches the currently active strip ("setLedStripLocked SKIP"),
     * which silently drops pure color changes. Clearing resets that state.
     */
    private fun setCustomLight(color: Int, mode: Int, onMs: Int, offMs: Int): Result<Boolean> {
        if (!Shizuku.pingBinder()) {
            return Result.failure(IllegalStateException("Shizuku unavailable"))
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(SecurityException("Shizuku permission denied"))
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
        if (!Shizuku.pingBinder()) {
            return Result.failure(IllegalStateException("Shizuku unavailable"))
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(SecurityException("Shizuku permission denied"))
        }
        val cleared = transactLight(0, MODE_STEADY, onMs, 0)
        if (cleared.isFailure) return cleared
        return transactLight(color, mode, onMs, 0)
    }

    private fun transactLight(color: Int, mode: Int, onMs: Int, offMs: Int): Result<Boolean> {
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
                data.writeInt(STYLE_CAMERA)   // styleType 12 = camera
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
