package com.aura.led.shizuku

import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

data class ShizukuState(
    val alive: Boolean = false,
    val hasPermission: Boolean = false,
)

object ShizukuManager {
    private val _state = MutableStateFlow(ShizukuState())
    val state: StateFlow<ShizukuState> = _state

    init {
        // Never throw during object init: Shizuku's binder may not be available yet.
        runCatching {
            Shizuku.addBinderReceivedListenerSticky { refresh() }
            Shizuku.addBinderDeadListener { refresh() }
            Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        }
        refresh()
    }

    fun refresh() {
        _state.value = try {
            ShizukuState(alive = Shizuku.pingBinder(), hasPermission = hasPermission())
        } catch (e: Exception) {
            ShizukuState(alive = false, hasPermission = false)
        }
    }

    fun hasPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission() {
        runCatching {
            if (Shizuku.shouldShowRequestPermissionRationale()) return
            Shizuku.requestPermission(1000)
        }
    }
}
