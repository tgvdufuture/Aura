package com.aura.led

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.util.Log
import com.aura.led.analytics.Analytics
import com.aura.led.data.AppDatabase
import com.aura.led.data.RuleRepository
import com.aura.led.notification.AuraNotificationListener
import com.aura.led.root.RootManager
import com.aura.led.update.UpdateScheduler
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
        // No-op unless the user explicitly agreed to anonymous usage statistics. Initialised
        // here so events emitted while the app runs in the background (LED activity) are queued.
        Analytics.init(this)
        // Keeps the daily release check alive between launches (no-op when the user turned it off),
        // so a new version reaches them without the app ever being opened.
        runCatching { UpdateScheduler.sync(this) }
            .onFailure { Log.w(TAG, "update check could not be scheduled", it) }
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
