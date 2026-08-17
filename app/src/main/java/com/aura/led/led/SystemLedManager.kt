package com.aura.led.led

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

/**
 * Disables the HyperOS default notification LED (`notification_light_pulse`, system namespace)
 * so Aura is the single LED driver (US-05 / F1.5).
 *
 * HyperOS rejects a plain WRITE_SETTINGS write ("You cannot change private secure settings"),
 * so the write goes through Shizuku (`IShizukuService.newProcess`), which runs `settings` as
 * the shell UID — the same path validated in Phase 0.
 */
object SystemLedManager {
    private const val KEY = "notification_light_pulse"
    private const val TAG = "AuraSystemLed"

    fun isDisabled(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, KEY, 1) == 0

    /** Whether we can drive the system LED (Shizuku alive + permission granted). */
    fun canControl(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** Writes via Shizuku (shell UID). Returns false on any failure. Blocks until the command ends. */
    fun setDisabled(disabled: Boolean): Boolean = runCatching {
        val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
        val proc = service.newProcess(
            arrayOf("settings", "put", "system", KEY, if (disabled) "0" else "1"),
            null,
            null,
        ) ?: return false
        val exit = proc.waitFor()
        Log.d(TAG, "setDisabled($disabled) exit=$exit")
        exit == 0
    }.onFailure { Log.e(TAG, "setDisabled failed", it) }.getOrDefault(false)
}
