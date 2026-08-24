package com.aura.led.led

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import com.aura.led.root.RootManager

/**
 * Disables the HyperOS default notification LED (`notification_light_pulse`, system namespace)
 * so Aura is the single LED driver (US-05 / F1.5).
 *
 * HyperOS rejects a plain WRITE_SETTINGS write ("You cannot change private secure settings"),
 * so the write goes through Shizuku (`IShizukuService.newProcess`) or a root shell, both of
 * which can run `settings` with the required privilege.
 */
object SystemLedManager {
    private const val KEY = "notification_light_pulse"
    private const val TAG = "AuraSystemLed"

    fun isDisabled(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, KEY, 1) == 0

    /** Whether we can drive the system LED (Shizuku or root). */
    fun canControl(): Boolean = runCatching {
        hasShizukuAccess() || RootManager.state.value.available
    }.getOrDefault(false)

    /** Writes via Shizuku or root. Returns false on any failure. Blocks until the command ends. */
    fun setDisabled(disabled: Boolean): Boolean = runCatching {
        if (hasShizukuAccess()) {
            val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            val proc = service.newProcess(
                arrayOf("settings", "put", "system", KEY, if (disabled) "0" else "1"),
                null,
                null,
            ) ?: return false
            val exit = proc.waitFor()
            Log.d(TAG, "setDisabled($disabled) via Shizuku exit=$exit")
            exit == 0
        } else if (RootManager.isAvailable()) {
            val exit = RootManager.run("settings put system $KEY ${if (disabled) "0" else "1"}")
                .getOrNull()?.exitCode ?: -1
            Log.d(TAG, "setDisabled($disabled) via root exit=$exit")
            exit == 0
        } else {
            false
        }
    }.onFailure { Log.e(TAG, "setDisabled failed", it) }.getOrDefault(false)

    private fun hasShizukuAccess(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
}
