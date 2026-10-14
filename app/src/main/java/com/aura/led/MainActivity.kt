package com.aura.led

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aura.led.analytics.Analytics
import com.aura.led.analytics.AnalyticsEvents
import com.aura.led.analytics.AnalyticsPrefs
import com.aura.led.service.AuraForegroundService
import com.aura.led.update.UpdateNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import com.aura.led.ui.AuraTheme
import com.aura.led.ui.ConsentScreen
import com.aura.led.ui.LanguagePickerScreen
import com.aura.led.ui.MainScreen
import com.aura.led.ui.MainViewModel
import com.aura.led.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val requestPostNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * Set when Aura was opened from the update notification, which carries a pending version to
     * show. A state flow rather than a flag read once: the notification is delivered through
     * `onNewIntent` when the app is already running, and that has to reach the composition whether
     * it started the activity or woke it up.
     */
    private val showUpdateRequest = MutableStateFlow(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(UpdateNotifier.EXTRA_SHOW_UPDATE, false)) {
            showUpdateRequest.value = true
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LanguageManager.getSavedLanguage(newBase)
        super.attachBaseContext(language?.let { LanguageManager.applyLanguage(newBase, it) } ?: newBase)
    }

    override fun onResume() {
        super.onResume()
        // Analytics is a no-op until the user agreed to it.
        Analytics.init(this)
        Analytics.onForeground(this)
    }

    override fun onStop() {
        super.onStop()
        // A configuration change (language switch) recreates the activity; that is the same session.
        if (!isChangingConfigurations) Analytics.onBackground()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launched from the update notification: pick the request up in the composition, once the
        // ViewModel that serves the update screen exists.
        showUpdateRequest.value =
            intent?.getBooleanExtra(UpdateNotifier.EXTRA_SHOW_UPDATE, false) == true

        // Keep the process alive in the background so notifications keep driving the LEDs.
        AuraForegroundService.start(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            var themeMode by remember { mutableStateOf(ThemeManager.getSavedMode(this@MainActivity)) }
            val darkTheme = when (themeMode) {
                ThemeManager.MODE_DARK -> true
                ThemeManager.MODE_LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            AuraTheme(darkTheme = darkTheme) {
                val savedLanguage = LanguageManager.getSavedLanguage(this@MainActivity)
                val viewModel: MainViewModel = viewModel()
                var consentAnswered by rememberSaveable {
                    mutableStateOf(AnalyticsPrefs.consent(this@MainActivity) != null)
                }
                // Settings can re-open the privacy screen so the choice can be reviewed at any time.
                var reviewingPrivacy by rememberSaveable { mutableStateOf(false) }
                // Declared next to the flag above, not inside the screen that uses it: the settings
                // list leaves the composition while the privacy screen is shown, which would forget
                // the state and send Back to the main list instead of back to the settings list.
                var showSettings by rememberSaveable { mutableStateOf(false) }

                // A tap on the update notification lands here, on whatever screen the app was left
                // on: the update screen takes over rather than waiting behind a settings list the
                // user is not looking at.
                val updateRequested by showUpdateRequest.collectAsState()
                LaunchedEffect(updateRequested) {
                    if (updateRequested) {
                        showSettings = false
                        viewModel.showPendingUpdate()
                        showUpdateRequest.value = false
                    }
                }

                if (savedLanguage == null) {
                    LanguagePickerScreen(
                        onSelect = { language ->
                            LanguageManager.setLanguage(this@MainActivity, language)
                            recreate()
                        },
                    )
                } else if (!consentAnswered || reviewingPrivacy) {
                    ConsentScreen(
                        updateCheckEnabledInitially = AnalyticsPrefs.isUpdateCheckEnabled(this@MainActivity),
                        onDone = { analyticsEnabled, updateCheckEnabled ->
                            viewModel.setAnalyticsEnabled(analyticsEnabled)
                            viewModel.setUpdateCheckEnabled(updateCheckEnabled)
                            consentAnswered = true
                            reviewingPrivacy = false
                        },
                        onCancel = if (reviewingPrivacy) {
                            { reviewingPrivacy = false }
                        } else {
                            null
                        },
                    )
                } else {
                    if (showSettings) {
                SettingsScreen(
                    currentLanguage = savedLanguage,
                    onLanguageChange = { language ->
                        LanguageManager.setLanguage(this@MainActivity, language)
                        recreate()
                    },
                    themeMode = themeMode,
                    onThemeChange = { mode ->
                        themeMode = mode
                        ThemeManager.setMode(this@MainActivity, mode)
                        Analytics.track(AnalyticsEvents.THEME_CHANGED, mapOf(AnalyticsEvents.P_MODE to mode))
                    },
                    onBack = { showSettings = false },
                    onReviewPrivacy = { reviewingPrivacy = true },
                )
                    } else {
                        MainScreen(
                            onOpenSettings = { showSettings = true },
                        )
                    }
                }
            }
        }
    }
}
