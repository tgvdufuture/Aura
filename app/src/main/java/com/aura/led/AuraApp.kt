package com.aura.led

import android.app.Application
import android.content.Context
import com.aura.led.data.AppDatabase
import com.aura.led.data.RuleRepository

class AuraApp : Application() {
    val repository: RuleRepository by lazy {
        RuleRepository(AppDatabase.get(this).ruleDao())
    }

    override fun attachBaseContext(base: Context) {
        val language = LanguageManager.getSavedLanguage(base)
        super.attachBaseContext(language?.let { LanguageManager.applyLanguage(base, it) } ?: base)
    }
}
