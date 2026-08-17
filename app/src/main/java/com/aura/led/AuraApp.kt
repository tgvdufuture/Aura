package com.aura.led

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.util.Log
import com.aura.led.data.AppDatabase
import com.aura.led.data.RuleRepository
import com.aura.led.notification.AuraNotificationListener

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
        // The listener is declared with default_autobind=false, so we control when it binds
        // (HyperOS doesn't reliably rebind notification listeners after a reboot). Request a
        // rebind whenever the process starts so it reconnects without opening the app.
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
