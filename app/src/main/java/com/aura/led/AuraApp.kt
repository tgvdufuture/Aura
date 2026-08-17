package com.aura.led

import android.app.Application
import com.aura.led.data.AppDatabase
import com.aura.led.data.RuleRepository

class AuraApp : Application() {
    val repository: RuleRepository by lazy {
        RuleRepository(AppDatabase.get(this).ruleDao())
    }
}
