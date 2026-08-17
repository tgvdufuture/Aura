package com.aura.led

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.aura.led.service.AuraForegroundService
import com.aura.led.ui.AuraTheme
import com.aura.led.ui.LanguagePickerScreen
import com.aura.led.ui.MainScreen

class MainActivity : ComponentActivity() {

    private val requestPostNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(newBase: Context) {
        val language = LanguageManager.getSavedLanguage(newBase)
        super.attachBaseContext(language?.let { LanguageManager.applyLanguage(newBase, it) } ?: newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the process alive in the background so notifications keep driving the LEDs.
        AuraForegroundService.start(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AuraTheme {
                val savedLanguage = LanguageManager.getSavedLanguage(this@MainActivity)
                if (savedLanguage == null) {
                    LanguagePickerScreen(
                        onSelect = { language ->
                            LanguageManager.setLanguage(this@MainActivity, language)
                            recreate()
                        },
                    )
                } else {
                    MainScreen(
                        currentLanguage = savedLanguage,
                        onLanguageChange = { language ->
                            LanguageManager.setLanguage(this@MainActivity, language)
                            recreate()
                        },
                    )
                }
            }
        }
    }
}
