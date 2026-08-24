package com.aura.led

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.util.Log
import com.aura.led.data.AppDatabase
import com.aura.led.data.RuleRepository
import com.aura.led.notification.AuraNotificationListener
import com.aura.led.root.RootManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AuraApp : Application() {
    val repository: RuleRepository by lazy {
        RuleRepository(AppDatabase.get(this).ruleDao())
    }

    override fun attachBaseContext(base: Context) {
        val language = LanguageManager.getSavedLanguage(base)
        super.attachBaseContext(language?.let { LanguageManager.applyLanguage(base, it) } ?: base)
    }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch { RootManager.refresh() }
        // Best-effort rebind safety net. The listener auto-binds through the system
        // whenever access is granted, but HyperOS can leave it unbound after a process
        // kill or reboot; requesting a rebind on startup reconnects it without opening
        // the app.
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(this, AuraNotificationListener::class.java),
            )
        }.onFailure { Log.w(TAG, "requestRebind failed", it) }
    }

    private companion object {
        const val TAG = "AuraApp"
    }
}
