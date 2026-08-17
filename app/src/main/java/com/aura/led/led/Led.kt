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

/** Frozen after Phase 0 — see phase0/REPORT.md. */
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
        const val PKG = "com.android.camera" // required by setCustomLight
        const val STYLE_CAMERA = 12
        const val MODE_STEADY = 0
        const val MODE_FLASH = 1
        const val MODE_BREATH = 2
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
        return setCustomLight(0, 0, 0, 0)
    }

    private fun cancelAnimation() {
        animationJob?.cancel()
        animationJob = null
    }

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
            setCustomLight(0, 0, 0, 0)
        }
        return Result.success(true)
    }

    private fun rainbowColors(): List<Int> =
        (0 until 360 step 30).map { h -> android.graphics.Color.HSVToColor(floatArrayOf(h.toFloat(), 1f, 1f)) }

    private fun setCustomLight(color: Int, mode: Int, onMs: Int, offMs: Int): Result<Boolean> {
        if (!Shizuku.pingBinder()) {
            return Result.failure(IllegalStateException("Shizuku unavailable"))
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(SecurityException("Shizuku permission denied"))
        }
        return runCatching {
            val service = SystemServiceHelper.getSystemService(SERVICE)
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
}
