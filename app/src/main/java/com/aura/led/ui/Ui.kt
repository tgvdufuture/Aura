package com.aura.led.ui

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aura.led.AuraApp
import com.aura.led.LanguageManager
import com.aura.led.R
import com.aura.led.ThemeManager
import com.aura.led.analytics.Analytics
import com.aura.led.analytics.AnalyticsEvents
import com.aura.led.analytics.AnalyticsPrefs
import com.aura.led.data.AppRule
import com.aura.led.data.QuietHours
import com.aura.led.data.ReminderConfig
import com.aura.led.data.ReminderInterval
import com.aura.led.data.SenderKind
import com.aura.led.data.SenderRule
import com.aura.led.data.SettingsKeys
import com.aura.led.led.Animations
import com.aura.led.led.ColorMapper
import com.aura.led.led.DeviceSupport
import com.aura.led.led.ShizukuLEDController
import com.aura.led.led.SystemLedManager
import com.aura.led.notification.AuraNotificationListener
import com.aura.led.notification.NotificationListenerState
import com.aura.led.root.RootManager
import com.aura.led.root.RootState
import com.aura.led.service.AuraForegroundService
import com.aura.led.service.AutostartWatch
import com.aura.led.shizuku.ShizukuManager
import com.aura.led.shizuku.ShizukuState
import com.aura.led.update.ApkDownloader
import com.aura.led.update.ApkInstaller
import com.aura.led.update.CheckResult
import com.aura.led.update.ReleaseNotes
import com.aura.led.update.UpdateCache
import com.aura.led.update.UpdateChecker
import com.aura.led.update.UpdateNotifier
import com.aura.led.update.UpdatePolicy
import com.aura.led.update.UpdateScheduler
import com.aura.led.update.VersionComparison
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val PALETTE = listOf(
    "#ffffff", "#fa503e", "#ff9214", "#ffd61d",
    "#36e86e", "#4cdddd", "#458cff", "#8482ff",
)

private val ANIMATION_OPTIONS = listOf(
    null to R.string.animation_static,
    Animations.BREATHING to R.string.animation_breathing,
    Animations.CHARGING to R.string.animation_charging,
    Animations.RAINBOW to R.string.animation_rainbow,
    Animations.POLICE to R.string.animation_alert,
)

// How long to wait after the last color change before re-sending the settled color with a
// long duration (so the preview stays lit once the user stops dragging).
private const val SETTLE_DEBOUNCE_MS = 300L


private val MAIN_COLOR_SWATCHES = listOf(
    "#ef3030", "#f65d32", "#ff8818", "#ffc21d",
    "#acd21b", "#31b343", "#0e9951", "#47c7ad",
    "#2bb6db", "#318be7", "#555fe0", "#855de0",
    "#cf61d0", "#ef4e98", "#a9775b", "#8b6b5d",
    "#aaa39e", "#8997a7", "#4d5660", "#000000",
)

@Composable
fun AuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    val colors = if (darkTheme) darkColorScheme() else lightColorScheme()
    androidx.compose.material3.MaterialTheme(
        colorScheme = colors,
        content = {
            // A Surface is what provides LocalContentColor. Without it, any screen that is not
            // wrapped in a Scaffold draws its text in the default black on top of the window
            // background — 1.6:1 contrast in dark mode, i.e. invisible. It also keeps the
            // background consistent with the Scaffold-based screens.
            Surface(color = colors.background, contentColor = colors.onBackground) {
                content()
            }
        },
    )
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AuraApp).repository

    val appRules = repository.appRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val senderRules = repository.senderRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val shizuku = ShizukuManager.state
    val root = RootManager.state

    /** Whether the notification listener is currently bound and receiving notifications. */
    val listenerConnected: StateFlow<Boolean> = NotificationListenerState.connected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationListenerState.connected.value)

    fun reconnectListener() {
        val ctx = getApplication<Application>()
        // requestRebind is silently ignored on HyperOS/MIUI, so request it as a
        // best-effort and open the notification-access screen where the listener
        // can actually be re-enabled by the user.
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(ctx, AuraNotificationListener::class.java),
            )
        }.onFailure { Log.w("AuraVM", "requestRebind failed", it) }
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { Log.w("AuraVM", "open notification listener settings failed", it) }
        Analytics.track(AnalyticsEvents.SETUP_NOTIFICATION_ACCESS_OPENED)
    }

    /** Opens HyperOS's autostart manager so the listener/service can restart after a reboot. */
    fun openAutostartSettings() {
        val ctx = getApplication<Application>()
        val miui = Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
        )
        runCatching {
            ctx.startActivity(miui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Log.w("AuraVM", "open autostart settings failed, falling back to app details", it)
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        Analytics.track(AnalyticsEvents.SETUP_AUTOSTART_OPENED)
    }

    data class SystemLedState(val canControl: Boolean = false, val disabled: Boolean = false)

    private val _systemLed = MutableStateFlow(SystemLedState())
    val systemLed: StateFlow<SystemLedState> = _systemLed

    fun refreshSystemLed() {
        val ctx = getApplication<Application>()
        _systemLed.value = SystemLedState(
            canControl = SystemLedManager.canControl(),
            disabled = SystemLedManager.isDisabled(ctx),
        )
    }

    fun setSystemLedDisabled(disabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        SystemLedManager.setDisabled(disabled)
        repository.setBoolSetting(SettingsKeys.SYSTEM_LED_DISABLED, disabled)
        refreshSystemLed()
        Analytics.track(AnalyticsEvents.SYSTEM_LED_TOGGLED, mapOf(AnalyticsEvents.P_DISABLED to disabled))
    }

    data class HealthState(
        val batteryExempt: Boolean = false,
        val serviceRunning: Boolean = false,
        /** What HyperOS's autostart switch has been observed to do (see `AutostartWatch`). */
        val autostart: AutostartWatch.State = AutostartWatch.State.UNKNOWN,
    )

    private val _health = MutableStateFlow(HealthState())
    val health: StateFlow<HealthState> = _health

    /**
     * Re-reads everything the settings badge is computed from. All local (binder and settings reads,
     * no network), so it can be called every time the app comes back to the foreground — and the
     * privilege and the battery exemption are both granted outside the app.
     */
    fun refreshSetupState() {
        refreshSystemLed()
        refreshHealth()
        refreshAnalyticsState()
    }

    fun refreshHealth() {
        val ctx = getApplication<Application>()
        val pm = ctx.getSystemService(PowerManager::class.java)
        val batteryExempt = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        val autostart = AutostartWatch.observe(ctx)
        _health.value = HealthState(
            batteryExempt = batteryExempt,
            serviceRunning = AuraForegroundService.running,
            autostart = autostart,
        )
        if (batteryExempt) {
            Analytics.trackSetupOnce(ctx, AnalyticsEvents.SETUP_BATTERY_EXEMPTION_GRANTED)
        }
        // Deduced from a delivered boot broadcast, once per install, like the exemption above.
        if (autostart == AutostartWatch.State.ENABLED) {
            Analytics.trackSetupOnce(ctx, AnalyticsEvents.SETUP_AUTOSTART_GRANTED)
        }
    }

    fun requestBatteryExemption() {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        Analytics.track(AnalyticsEvents.SETUP_BATTERY_EXEMPTION_REQUESTED)
    }

    fun setServiceRunning(running: Boolean) {
        val ctx = getApplication<Application>()
        if (running) AuraForegroundService.start(ctx) else AuraForegroundService.stop(ctx)
        refreshHealth()
        Analytics.track(AnalyticsEvents.SERVICE_TOGGLED, mapOf(AnalyticsEvents.P_RUNNING to running))
    }

    data class LedSettingsState(val timeoutMs: Long = 10_000L)

    private val _ledSettings = MutableStateFlow(LedSettingsState(timeoutMs = ShizukuLEDController.ledTimeoutMs))
    val ledSettings: StateFlow<LedSettingsState> = _ledSettings

    fun setLedTimeout(ms: Long) {
        val clamped = (ms / 1000).coerceIn(1, 30) * 1000L
        ShizukuLEDController.ledTimeoutMs = clamped
        _ledSettings.value = LedSettingsState(timeoutMs = clamped)
        viewModelScope.launch { repository.setSetting(SettingsKeys.LED_TIMEOUT_MS, clamped.toString()) }
        Analytics.track(
            AnalyticsEvents.LED_DURATION_CHANGED,
            mapOf(AnalyticsEvents.P_SECONDS to (clamped / 1000).toInt()),
        )
    }

    // ---- Persistent reminder (PRD docs/PRD-persistent-reminder.md, Phase 1) ----

    data class ReminderState(
        val enabled: Boolean = false,
        val intervalMs: Long = ReminderConfig.DEFAULT_INTERVAL_MS,
        val quietStart: String = ReminderConfig.DEFAULT_QUIET_START,
        val quietEnd: String = ReminderConfig.DEFAULT_QUIET_END,
    )

    private val _reminder = MutableStateFlow(ReminderState())
    val reminder: StateFlow<ReminderState> = _reminder

    /** Pushes the current settings to the in-process listener so changes apply immediately. */
    private fun pushReminderToListener(state: ReminderState) {
        AuraNotificationListener.updateReminderConfig(
            ReminderConfig(
                enabled = state.enabled,
                intervalMs = state.intervalMs,
                quietStart = state.quietStart,
                quietEnd = state.quietEnd,
            )
        )
    }

    fun setReminderEnabled(enabled: Boolean) {
        _reminder.value = _reminder.value.copy(enabled = enabled)
        pushReminderToListener(_reminder.value)
        viewModelScope.launch(Dispatchers.IO) {
            repository.setBoolSetting(SettingsKeys.REMINDER_ENABLED, enabled)
        }
        Analytics.track(AnalyticsEvents.REMINDER_TOGGLED, mapOf(AnalyticsEvents.P_ENABLED to enabled))
    }

    fun setReminderInterval(ms: Long) {
        val clamped = ReminderInterval.clamp(ms)
        _reminder.value = _reminder.value.copy(intervalMs = clamped)
        pushReminderToListener(_reminder.value)
        viewModelScope.launch(Dispatchers.IO) {
            repository.setSetting(SettingsKeys.REMINDER_INTERVAL_MS, clamped.toString())
        }
        Analytics.track(
            AnalyticsEvents.REMINDER_INTERVAL_CHANGED,
            mapOf(AnalyticsEvents.P_INTERVAL_S to (clamped / 1000).toInt()),
        )
    }

    /** Accepts "HH:mm" values only; invalid input keeps the previous stored value. */
    fun setQuietHours(start: String, end: String) {
        val parsedStart = QuietHours.parseHHmm(start) ?: return
        val parsedEnd = QuietHours.parseHHmm(end) ?: return
        val normalizedStart = "%02d:%02d".format(parsedStart / 60, parsedStart % 60)
        val normalizedEnd = "%02d:%02d".format(parsedEnd / 60, parsedEnd % 60)
        _reminder.value = _reminder.value.copy(quietStart = normalizedStart, quietEnd = normalizedEnd)
        pushReminderToListener(_reminder.value)
        viewModelScope.launch(Dispatchers.IO) {
            repository.setSetting(SettingsKeys.REMINDER_QUIET_START, normalizedStart)
            repository.setSetting(SettingsKeys.REMINDER_QUIET_END, normalizedEnd)
        }
        Analytics.track(AnalyticsEvents.REMINDER_QUIET_HOURS_CHANGED)
    }

    data class AppInfo(val packageName: String, val label: String)

    val installedApps: List<AppInfo> = run {
        val pm = application.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { ri ->
                val ai = ri.activityInfo.applicationInfo
                val label = ai.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: ai.packageName
                AppInfo(ai.packageName, label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun setEnabled(pkg: String, label: String, enabled: Boolean) = viewModelScope.launch {
        val existing = repository.getAppRule(pkg)
        repository.upsertAppRule(existing?.copy(enabled = enabled) ?: AppRule(pkg, label, enabled = enabled))
        Analytics.track(
            AnalyticsEvents.APP_RULE_TOGGLED,
            mapOf(AnalyticsEvents.P_TARGET_PACKAGE to pkg, AnalyticsEvents.P_ENABLED to enabled),
        )
    }

    fun setColor(pkg: String, label: String, color: String) = viewModelScope.launch {
        val existing = repository.getAppRule(pkg)
        repository.upsertAppRule(existing?.copy(defaultColorHex = color) ?: AppRule(pkg, label, defaultColorHex = color))
        Analytics.track(
            AnalyticsEvents.APP_RULE_COLOR_CHANGED,
            mapOf(
                AnalyticsEvents.P_TARGET_PACKAGE to pkg,
                AnalyticsEvents.P_IS_CUSTOM to (color !in MAIN_COLOR_SWATCHES),
            ),
        )
    }

    fun setSenderParsing(pkg: String, label: String, enabled: Boolean) = viewModelScope.launch {
        val existing = repository.getAppRule(pkg)
        repository.upsertAppRule(
            existing?.copy(senderParsingEnabled = enabled) ?: AppRule(pkg, label, senderParsingEnabled = enabled)
        )
        Analytics.track(
            AnalyticsEvents.SENDER_PARSING_TOGGLED,
            mapOf(AnalyticsEvents.P_TARGET_PACKAGE to pkg, AnalyticsEvents.P_ENABLED to enabled),
        )
    }

    fun addSenderRule(appPkg: String, kind: String, matchKey: String, color: String, animationId: String?) =
        viewModelScope.launch {
            val key = matchKey.trim().lowercase()
            if (key.isBlank()) return@launch
            repository.upsertSenderRule(
                SenderRule(kind = kind, appPackage = appPkg, matchKey = key, colorHex = color, animationId = animationId)
            )
            // The rule name itself is never reported, only the app it belongs to.
            Analytics.track(
                AnalyticsEvents.SENDER_RULE_CREATED,
                mapOf(
                    AnalyticsEvents.P_KIND to kind,
                    AnalyticsEvents.P_TARGET_PACKAGE to appPkg,
                    AnalyticsEvents.P_ANIMATION_ID to animationId,
                ),
            )
            if (animationId != null) {
                Analytics.track(
                    AnalyticsEvents.ANIMATION_SELECTED,
                    mapOf(
                        AnalyticsEvents.P_ANIMATION_ID to animationId,
                        AnalyticsEvents.P_TARGET_PACKAGE to appPkg,
                    ),
                )
            }
        }

    fun deleteSenderRule(rule: SenderRule) = viewModelScope.launch {
        repository.deleteSenderRule(rule.id)
        Analytics.track(
            AnalyticsEvents.SENDER_RULE_DELETED,
            mapOf(AnalyticsEvents.P_KIND to rule.kind, AnalyticsEvents.P_TARGET_PACKAGE to rule.appPackage),
        )
    }

    fun requestShizuku() {
        Analytics.track(
            AnalyticsEvents.SETUP_PRIVILEGE_REQUESTED,
            mapOf(AnalyticsEvents.P_KIND to AnalyticsEvents.KIND_SHIZUKU),
        )
        ShizukuManager.requestPermission()
    }

    private val led = ShizukuLEDController()

    fun testLed() = viewModelScope.launch(Dispatchers.IO) {
        val success = led.setColor("#ff0000").getOrDefault(false)
        Analytics.track(
            AnalyticsEvents.SETUP_LED_TEST_RUN,
            mapOf(AnalyticsEvents.P_SUCCESS to success),
        )
    }

    // Latest requested preview (color, animation, persistence); null = stop. A StateFlow
    // keeps the newest request, and collectLatest below applies it as soon as the previous
    // command completes, so the light service never receives concurrent or out-of-order commands.
    private val previewRequests = MutableStateFlow<LedPreviewRequest?>(null)

    private data class LedPreviewRequest(
        val color: String,
        val animationId: String? = null,
        val persistent: Boolean = false,
    )

    /** Persistent preview (custom picker / sender rules): tracks, then stays lit. */
    fun previewLed(colorHex: String, animationId: String? = null) {
        previewRequests.value = LedPreviewRequest(colorHex, animationId, persistent = true)
        if (animationId == null) trackPreview(colorHex)
    }

    /** Transient preview (default swatches): brief flash, then auto-off. */
    fun flashLed(colorHex: String) {
        previewRequests.value = LedPreviewRequest(colorHex, persistent = false)
        trackPreview(colorHex)
    }

    private fun trackPreview(colorHex: String) {
        Analytics.track(
            AnalyticsEvents.APP_RULE_PREVIEWED,
            mapOf(AnalyticsEvents.P_IS_CUSTOM to (colorHex !in MAIN_COLOR_SWATCHES)),
        )
    }

    /** Turns the preview LED off (e.g. when the color picker is dismissed). */
    fun stopLedPreview() {
        previewRequests.value = null
    }

    private fun logPreviewResult(request: LedPreviewRequest?, result: Result<Boolean>) {
        if (request == null) {
            result.onSuccess { Log.i("AuraVM", "LED preview stopped") }
            result.onFailure { Log.w("AuraVM", "LED preview stop failed", it) }
        } else {
            result.onSuccess { Log.i("AuraVM", "LED preview OK: ${request.color} / ${request.animationId}") }
            result.onFailure {
                Log.w("AuraVM", "LED preview failed for ${request.color} / ${request.animationId}", it)
                val now = System.currentTimeMillis()
                if (now - lastPreviewFailureToast > 2_000L) {
                    lastPreviewFailureToast = now
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(
                            getApplication(),
                            getApplication<Application>().getString(R.string.led_preview_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    @Volatile
    private var lastPreviewFailureToast = 0L

    // ---- Usage analytics (opt-in) and in-app update ----
    // PRD: docs/PRD-usage-analytics-and-updates.md

    data class AnalyticsState(
        val granted: Boolean = false,
        val updateCheckEnabled: Boolean = true,
        /** The support answer currently selected, `null` while the user has not answered. */
        val supportAnswer: String? = null,
    )

    private val _analytics = MutableStateFlow(AnalyticsState())
    val analytics: StateFlow<AnalyticsState> = _analytics

    enum class UpdatePhase { HIDDEN, AVAILABLE, PERMISSION_REQUIRED, DOWNLOADING, ERROR }

    data class UpdateUiState(
        val phase: UpdatePhase = UpdatePhase.HIDDEN,
        val versionName: String = "",
        val sizeBytes: Long = 0L,
        val notes: String = "",
        val progress: Int = 0,
        val error: String? = null,
    )

    private val _update = MutableStateFlow(UpdateUiState())
    val update: StateFlow<UpdateUiState> = _update

    /** Kept out of the UI state: only the phase and the size are shown. */
    private var pendingApkUrl = ""

    /**
     * The tag the dialog is showing, kept beside the URL and out of the UI state for the same
     * reason. Every update event has to name the same version: `versionName` drops the `v`, so a
     * funnel broken down by `latest_tag` would show two series for a single release.
     */
    private var pendingTag = ""

    private fun ctx(): Application = getApplication()

    fun refreshAnalyticsState() {
        val context = ctx()
        _analytics.value = AnalyticsState(
            granted = AnalyticsPrefs.isGranted(context),
            updateCheckEnabled = AnalyticsPrefs.isUpdateCheckEnabled(context),
            supportAnswer = AnalyticsPrefs.supportAnswer(context),
        )
    }

    /** Records the answer to the first-run consent screen, or the Settings toggle. */
    fun setAnalyticsEnabled(enabled: Boolean) {
        val context = ctx()
        if (enabled) {
            Analytics.grantConsent(context)
            Analytics.track(AnalyticsEvents.CONSENT_GRANTED)
            trackLanguageStepOnce()
        } else {
            Analytics.track(AnalyticsEvents.CONSENT_DECLINED)
            Analytics.revokeConsent(context)
        }
        refreshAnalyticsState()
    }

    fun setUpdateCheckEnabled(enabled: Boolean) {
        val context = ctx()
        AnalyticsPrefs.setUpdateCheckEnabled(context, enabled)
        // Turning the check off must also stop the background job: the setting promises no
        // request at all, not merely no request while a screen is open.
        UpdateScheduler.sync(context)
        refreshAnalyticsState()
        if (enabled) checkForUpdate()
    }

    /** The language step happens before the consent screen, so it is reported once consent exists. */
    private fun trackLanguageStepOnce() {
        val context = ctx()
        if (!AnalyticsPrefs.isGranted(context)) return
        val language = LanguageManager.getSavedLanguage(context) ?: return
        Analytics.trackSetupOnce(
            context,
            AnalyticsEvents.SETUP_LANGUAGE_SELECTED,
            mapOf(AnalyticsEvents.P_LANGUAGE to language),
        )
    }

    /**
     * Asks GitHub for the latest release, at most once a day (and never when the user
     * disabled the check). Failures are silent: the owner sees them in the log, the user
     * sees nothing.
     */
    fun checkForUpdate() {
        val context = ctx()
        if (!AnalyticsPrefs.isUpdateCheckEnabled(context)) return
        val now = System.currentTimeMillis()
        // One request a day at most: screens can recompose freely.
        if (!UpdatePolicy.shouldCheck(now, AnalyticsPrefs.lastUpdateCheckMs(context))) return
        viewModelScope.launch(Dispatchers.IO) {
            val installed = installedVersionName()
            when (val result = UpdateChecker.check(installed, installed)) {
                is CheckResult.UpToDate -> {
                    AnalyticsPrefs.setLastUpdateCheckMs(context, now)
                    // Nothing left to announce: drop the version the background check had been
                    // holding and the notification the user might still be keeping in the shade.
                    UpdateCache.clear(context)
                    Analytics.track(
                        AnalyticsEvents.UPDATE_CHECK_RESULT,
                        mapOf(AnalyticsEvents.P_RESULT to "up_to_date"),
                    )
                }

                is CheckResult.Failed -> {
                    // Throttled too, so a broken network cannot turn every launch into a request.
                    AnalyticsPrefs.setLastUpdateCheckMs(context, now)
                    UpdateChecker.logFailure(result.reason)
                }

                is CheckResult.UpdateAvailable -> {
                    val decision = UpdatePolicy.decide(
                        nowMs = now,
                        lastCheckMs = AnalyticsPrefs.lastUpdateCheckMs(context),
                        lastPromptMs = AnalyticsPrefs.lastPromptMs(context),
                        dismissedTag = AnalyticsPrefs.dismissedTag(context),
                        latestTag = result.tag,
                    )
                    AnalyticsPrefs.setLastUpdateCheckMs(context, now)
                    Analytics.track(
                        AnalyticsEvents.UPDATE_CHECK_RESULT,
                        mapOf(
                            AnalyticsEvents.P_RESULT to "update_available",
                            AnalyticsEvents.P_LATEST_TAG to result.tag,
                        ),
                    )
                    if (decision.shouldPrompt) {
                        pendingApkUrl = result.apkUrl
                        pendingTag = result.tag
                        AnalyticsPrefs.setLastPromptMs(context, now)
                        _update.value = UpdateUiState(
                            phase = UpdatePhase.AVAILABLE,
                            versionName = result.versionName,
                            sizeBytes = result.sizeBytes,
                            notes = result.notes,
                        )
                        Analytics.track(
                            AnalyticsEvents.UPDATE_PROMPT_SHOWN,
                            mapOf(AnalyticsEvents.P_LATEST_TAG to result.tag),
                        )
                        // The user is reading the dialog: the notification for this tag has done its
                        // job, and the tag is spent — being told twice about one version is nagging.
                        AnalyticsPrefs.setNotifiedTag(context, result.tag)
                        UpdateNotifier.cancel(context)
                    }
                }
            }
        }
    }

    /**
     * Shows the update screen for the version the background check announced, when its notification
     * is tapped. The record comes from preferences, not from a fresh GitHub request: the tap has to
     * work on a plane, and a failed round trip would leave the user staring at the app list.
     */
    fun showPendingUpdate() {
        val context = ctx()
        val pending = UpdateCache.load(context)
        val installed = installedVersionName()
        if (pending == null || !VersionComparison.isNewer(installed, pending.tag)) {
            // Stale record (the version got installed in the meantime): the notification pointing at
            // it is misleading too, so both go.
            UpdateCache.clear(context)
            return
        }
        pendingApkUrl = pending.apkUrl
        pendingTag = pending.tag
        _update.value = UpdateUiState(
            phase = UpdatePhase.AVAILABLE,
            versionName = pending.versionName,
            sizeBytes = pending.sizeBytes,
            notes = pending.notes,
        )
        Analytics.track(
            AnalyticsEvents.UPDATE_NOTIFICATION_TAPPED,
            mapOf(AnalyticsEvents.P_LATEST_TAG to pending.tag),
        )
    }

    /** Downloads the release APK and launches the system installer. */
    fun startUpdate() {
        val context = ctx()
        if (pendingApkUrl.isBlank()) return
        if (!ApkInstaller.canInstall(context)) {
            _update.value = _update.value.copy(phase = UpdatePhase.PERMISSION_REQUIRED)
            return
        }
        Analytics.track(
            AnalyticsEvents.UPDATE_PROMPT_ACCEPTED,
            mapOf(AnalyticsEvents.P_LATEST_TAG to pendingTag),
        )
        viewModelScope.launch(Dispatchers.IO) {
            val expectedSize = _update.value.sizeBytes
            _update.value = _update.value.copy(phase = UpdatePhase.DOWNLOADING, progress = 0, error = null)
            ApkDownloader.download(context, pendingApkUrl, expectedSize) { percent ->
                _update.value = _update.value.copy(progress = percent)
            }.fold(
                onSuccess = { apk ->
                    Analytics.track(
                        AnalyticsEvents.UPDATE_DOWNLOAD_RESULT,
                        mapOf(
                            AnalyticsEvents.P_RESULT to "success",
                            AnalyticsEvents.P_LATEST_TAG to pendingTag,
                        ),
                    )
                    if (ApkInstaller.install(context, apk)) {
                        Analytics.track(
                            AnalyticsEvents.UPDATE_INSTALL_LAUNCHED,
                            mapOf(AnalyticsEvents.P_LATEST_TAG to pendingTag),
                        )
                        // The install is under way; the notification has nothing left to say.
                        UpdateNotifier.cancel(context)
                        _update.value = UpdateUiState()
                    } else {
                        // The installer refused the intent: hand the user the release page instead.
                        ApkInstaller.openReleasePage(context, RELEASES_PAGE_URL)
                        _update.value = UpdateUiState()
                    }
                },
                onFailure = { error ->
                    Analytics.track(
                        AnalyticsEvents.UPDATE_DOWNLOAD_RESULT,
                        mapOf(
                            AnalyticsEvents.P_RESULT to "error",
                            AnalyticsEvents.P_LATEST_TAG to pendingTag,
                        ),
                    )
                    _update.value = _update.value.copy(
                        phase = UpdatePhase.ERROR,
                        error = error.message ?: error.javaClass.simpleName,
                    )
                },
            )
        }
    }

    fun openInstallPermissionSettings() = ApkInstaller.openInstallPermissionSettings(ctx())

    /**
     * Back from the system's "install unknown apps" screen.
     *
     * The grant happens outside the app, so this state is stale by design until we look again, and
     * nothing else looks at it: the dialog kept offering "Open settings" to a user who had just
     * allowed the install, with no button left that would download the update — while the copy on
     * that same screen says "then start the update again". Closing the dialog there also marked the
     * release as dismissed and cancelled its notification, so the update was gone for good.
     */
    fun refreshInstallPermission() {
        if (_update.value.phase != UpdatePhase.PERMISSION_REQUIRED) return
        if (ApkInstaller.canInstall(ctx())) {
            _update.value = _update.value.copy(phase = UpdatePhase.AVAILABLE)
        }
    }

    fun dismissUpdateDialog() {
        val context = ctx()
        Analytics.track(
            AnalyticsEvents.UPDATE_PROMPT_POSTPONED,
            mapOf(AnalyticsEvents.P_LATEST_TAG to pendingTag),
        )
        // Closing the dialog means "not this version"; "Later" only postpones.
        AnalyticsPrefs.setDismissedTag(context, "v${_update.value.versionName}")
        // "Not this version" also covers the notification: it must not reappear on its own.
        UpdateNotifier.cancel(context)
        _update.value = UpdateUiState()
    }

    fun postponeUpdate() {
        Analytics.track(
            AnalyticsEvents.UPDATE_PROMPT_POSTPONED,
            mapOf(AnalyticsEvents.P_LATEST_TAG to pendingTag),
        )
        AnalyticsPrefs.setLastPromptMs(ctx(), System.currentTimeMillis())
        _update.value = UpdateUiState()
    }

    fun reportSupportIntent(answer: String) {
        // The choice stays editable, so it is reported only when it actually changes: re-tapping
        // the selected answer must not add a second event for the same opinion.
        if (SupportQuestion.shouldRecord(AnalyticsPrefs.supportAnswer(ctx()), answer)) {
            Analytics.track(AnalyticsEvents.SUPPORT_INTENT, mapOf(AnalyticsEvents.P_ANSWER to answer))
            AnalyticsPrefs.setSupportAnswer(ctx(), answer)
            refreshAnalyticsState()
        }
        // Answering "a donation now" is a request, not a vote: open the page instead of
        // recording an intention the user cannot act on.
        if (SupportQuestion.opensDonationPage(answer)) openDonationPage()
    }

    /** Opens the Ko-fi page. Not analytics: this works whether or not statistics are enabled. */
    fun openDonationPage() {
        Analytics.track(AnalyticsEvents.SUPPORT_CLICKED, mapOf(AnalyticsEvents.P_CHANNEL to AnalyticsEvents.CHANNEL_KOFI))
        val context = ctx()
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(DONATION_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { Log.w("AuraVM", "could not open the donation page", it) }
    }

    /**
     * Opens the GitHub repository, which is where a star can be given.
     *
     * A download is counted on a page nobody looks at; a star is the first thing the next visitor
     * checks. The update dialog is the one screen every updating user has in front of them, so the
     * ask goes there — once, as a quiet text button, and never as a second dialog.
     */
    fun openGitHubRepo() {
        Analytics.track(
            AnalyticsEvents.STAR_CLICKED,
            mapOf(AnalyticsEvents.P_CHANNEL to AnalyticsEvents.CHANNEL_GITHUB),
        )
        val context = ctx()
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { Log.w("AuraVM", "could not open the GitHub repository", it) }
    }

    private fun installedVersionName(): String = runCatching {
        ctx().packageManager.getPackageInfo(ctx().packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    init {
        refreshAnalyticsState()
        trackLanguageStepOnce()
        // Setup-funnel steps observed after the fact: notification access, autostart and the
        // battery exemption are granted in system screens, so Aura reports the result the first
        // time it sees it instead of polling.
        viewModelScope.launch {
            NotificationListenerState.connected.collect { connected ->
                if (connected) {
                    Analytics.trackSetupOnce(getApplication(), AnalyticsEvents.SETUP_NOTIFICATION_ACCESS_GRANTED)
                }
            }
        }
        viewModelScope.launch {
            ShizukuManager.state.collect { state ->
                // Shizuku can come up (or be granted) long after this ViewModel was built, and the
                // badge on the settings gear reads the privilege from that state: recompute it here
                // or the gear keeps claiming something is missing that was fixed minutes ago.
                refreshSetupState()
                if (state.hasPermission) {
                    Analytics.trackSetupOnce(
                        getApplication(),
                        AnalyticsEvents.SETUP_PRIVILEGE_GRANTED,
                        mapOf(AnalyticsEvents.P_KIND to AnalyticsEvents.KIND_SHIZUKU),
                        flag = AnalyticsEvents.SETUP_PRIVILEGE_GRANTED + "_" + AnalyticsEvents.KIND_SHIZUKU,
                    )
                }
            }
        }
        viewModelScope.launch {
            RootManager.state.collect { state ->
                // Same reason as Shizuku above: the badge waits for this answer.
                refreshSetupState()
                if (state.available) {
                    Analytics.trackSetupOnce(
                        getApplication(),
                        AnalyticsEvents.SETUP_PRIVILEGE_GRANTED,
                        mapOf(AnalyticsEvents.P_KIND to AnalyticsEvents.KIND_ROOT),
                        flag = AnalyticsEvents.SETUP_PRIVILEGE_GRANTED + "_" + AnalyticsEvents.KIND_ROOT,
                    )
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            RootManager.refresh()
            refreshSystemLed()
        }
        refreshSystemLed()
        refreshHealth()
        viewModelScope.launch(Dispatchers.IO) {
            val ms = repository.getSetting(SettingsKeys.LED_TIMEOUT_MS, "10000").toLongOrNull() ?: 10_000L
            val clamped = (ms / 1000).coerceIn(1, 30) * 1000L
            ShizukuLEDController.ledTimeoutMs = clamped
            _ledSettings.value = LedSettingsState(timeoutMs = clamped)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val enabled = repository.getBoolSetting(SettingsKeys.REMINDER_ENABLED, false)
            val intervalMs = repository.getSetting(
                SettingsKeys.REMINDER_INTERVAL_MS,
                ReminderConfig.DEFAULT_INTERVAL_MS.toString(),
            ).toLongOrNull() ?: ReminderConfig.DEFAULT_INTERVAL_MS
            val quietStart = repository.getSetting(SettingsKeys.REMINDER_QUIET_START, ReminderConfig.DEFAULT_QUIET_START)
            val quietEnd = repository.getSetting(SettingsKeys.REMINDER_QUIET_END, ReminderConfig.DEFAULT_QUIET_END)
            _reminder.value = ReminderState(
                enabled = enabled,
                intervalMs = ReminderInterval.clamp(intervalMs),
                quietStart = quietStart,
                quietEnd = quietEnd,
            )
        }
        // Single serialized worker for previews. collectLatest cancels the pending settle
        // whenever a new color arrives, and re-applies the settled color with a long
        // duration once the user stops changing it for a beat.
        viewModelScope.launch(Dispatchers.IO) {
            previewRequests.collectLatest { request ->
                if (request == null) {
                    logPreviewResult(null, led.stopPreview())
                } else if (request.animationId != null) {
                    logPreviewResult(request, led.startAnimation(request.animationId, request.color))
                } else if (request.persistent) {
                    logPreviewResult(request, led.previewColor(request.color))
                    delay(SETTLE_DEBOUNCE_MS)
                    if (previewRequests.value == request) {
                        logPreviewResult(request, led.settleColor(request.color))
                    }
                } else {
                    logPreviewResult(request, led.flashColor(request.color))
                }
            }
        }
    }

    private companion object {
        const val RELEASES_PAGE_URL = "https://github.com/tgvdufuture/Aura/releases/latest"
        const val REPO_URL = "https://github.com/tgvdufuture/Aura"
        const val DONATION_URL = "https://ko-fi.com/tgvdufuture"
    }
}

/**
 * Where the dot on the Settings gear sits.
 *
 * Its centre, in dp, measured from the top-end corner of the 48 dp touch target: a negative x is to
 * the left of that corner, a positive y below its top edge. [BADGE_SIZE_DP] is its diameter. These
 * are the numbers chosen with `docs/design/settings-badge.html`, a life-size mock of this bar —
 * open it there to pick another spot instead of nudging these by eye.
 */
private const val BADGE_CENTRE_X_DP = -16.8f
private const val BADGE_CENTRE_Y_DP = 11.9f
private const val BADGE_SIZE_DP = 7.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val appRules by viewModel.appRules.collectAsState()
    val senderRules by viewModel.senderRules.collectAsState()
    val update by viewModel.update.collectAsState()
    val listenerConnected by viewModel.listenerConnected.collectAsState()
    val systemLed by viewModel.systemLed.collectAsState()
    val health by viewModel.health.collectAsState()
    val analytics by viewModel.analytics.collectAsState()

    val rulesByApp = remember(senderRules) { senderRules.groupBy { it.appPackage } }

    // The privilege and the battery exemption are granted in system screens, so the state behind the
    // badge is stale by design until we look again: same refresh-on-resume the Settings cards use.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSetupState()
                // Same reason, for the dialog: the install permission is granted in a system screen,
                // and coming back from it has to put the download button back.
                viewModel.refreshInstallPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Whether Settings is owed something, and how badly — see SettingsAttention.kt for which state
    // sits on which side of "important".
    val attention = settingsAttention(
        AttentionInputs(
            notificationAccess = listenerConnected,
            privilege = systemLed.canControl,
            batteryExempt = health.batteryExempt,
            analyticsGranted = analytics.granted,
            supportAnswer = analytics.supportAnswer,
            autostartBlocked = health.autostart == AutostartWatch.State.BLOCKED,
        ),
    )

    LaunchedEffect(Unit) {
        Analytics.screen("main")
        // Throttled to one GitHub request per day inside the ViewModel.
        viewModel.checkForUpdate()
    }

    var searchQuery by remember { mutableStateOf("") }
    // A phone Aura cannot drive at all. The list stays browsable, but nothing here will ever light
    // up, and the empty state below would promise the opposite.
    val unsupportedDevice = remember { DeviceSupport.shouldWarn(Build.MANUFACTURER, Build.BRAND) }
    val filteredApps = remember(viewModel.installedApps, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) viewModel.installedApps
        else viewModel.installedApps.filter {
            it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    // The dot is the only part of this screen a screen reader cannot see, so the
                    // gear says what it found — including the severity, not just the count.
                    // The count announced is the count of the level being announced: saying "3 to
                    // fix" when one of the three is merely worth a look would be the label lying.
                    val description = when (attention.level) {
                        AttentionLevel.IMPORTANT ->
                            stringResource(R.string.settings_attention_important, attention.important)
                        AttentionLevel.INFO ->
                            stringResource(R.string.settings_attention_info, attention.info)
                        null -> stringResource(R.string.settings)
                    }
                    // Hand-placed rather than a BadgedBox: the framework pins its badge to the very
                    // corner of the touch target, against the edge of the screen and a dozen dp off
                    // the gear's own ink, where it reads as belonging to the bar and not the icon.
                    Box {
                        IconButton(onClick = onOpenSettings) {
                            Icon(painterResource(R.drawable.ic_settings), contentDescription = description)
                        }
                        if (attention.level != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    // This offset moves the dot's own top-end corner, so the centre
                                    // above is what the constants mean and what the tool reports.
                                    .offset(
                                        x = (BADGE_CENTRE_X_DP + BADGE_SIZE_DP / 2f).dp,
                                        y = (BADGE_CENTRE_Y_DP - BADGE_SIZE_DP / 2f).dp,
                                    )
                                    .size(BADGE_SIZE_DP.dp)
                                    .background(
                                        color = when (attention.level) {
                                            AttentionLevel.IMPORTANT -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        shape = CircleShape,
                                    ),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (unsupportedDevice) {
                item { CompatibilityNotice() }
            }
            item { Text(stringResource(R.string.section_applications), style = MaterialTheme.typography.titleMedium) }
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Two different empties: "nothing matches that name" and "nothing is lit yet". The
            // second one is the app's real activation moment, so it says what to do about it — but
            // only where the promise holds: on a phone Aura cannot drive, the notice above is the
            // truthful message and "the LED lights the moment it notifies" would be a lie.
            if (searchQuery.isNotBlank() && filteredApps.isEmpty()) {
                item { EmptyState(title = stringResource(R.string.no_apps_found)) }
            } else if (searchQuery.isBlank() && appRules.none { it.enabled } && !unsupportedDevice) {
                item {
                    EmptyState(
                        title = stringResource(R.string.empty_no_enabled_title),
                        body = stringResource(R.string.empty_no_enabled_hint),
                    )
                }
            }
            items(filteredApps) { app ->
                val rule = appRules.firstOrNull { it.packageName == app.packageName }
                AppRuleCard(
                    app = app,
                    rule = rule,
                    senderRules = rulesByApp[app.packageName].orEmpty(),
                    onToggle = { viewModel.setEnabled(app.packageName, app.label, it) },
                    onColor = { viewModel.setColor(app.packageName, app.label, it) },
                    onFlashColor = { viewModel.flashLed(it) },
                    onPreviewColor = { viewModel.previewLed(it) },
                    onStopPreview = viewModel::stopLedPreview,
                    onSenderParsing = { viewModel.setSenderParsing(app.packageName, app.label, it) },
                    onAddSender = { kind, name, color, anim ->
                        viewModel.addSenderRule(app.packageName, kind, name, color, anim)
                    },
                    onPreviewSender = { color, anim -> viewModel.previewLed(color, anim) },
                    onDeleteSender = viewModel::deleteSenderRule,
                )
            }
        }
    }

    UpdateDialog(
        state = update,
        onUpdate = viewModel::startUpdate,
        onStar = viewModel::openGitHubRepo,
        onLater = viewModel::postponeUpdate,
        onDismiss = viewModel::dismissUpdateDialog,
        onOpenPermissionSettings = viewModel::openInstallPermissionSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    themeMode: String,
    onThemeChange: (String) -> Unit,
    onBack: () -> Unit,
    onReviewPrivacy: () -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val shizuku by viewModel.shizuku.collectAsState()
    val root by viewModel.root.collectAsState()
    val systemLed by viewModel.systemLed.collectAsState()
    val health by viewModel.health.collectAsState()
    val ledSettings by viewModel.ledSettings.collectAsState()
    val listenerConnected by viewModel.listenerConnected.collectAsState()
    val reminder by viewModel.reminder.collectAsState()
    val analytics by viewModel.analytics.collectAsState()

    LaunchedEffect(Unit) { Analytics.screen("settings") }

    // Settings is a screen of the app, not a second app: without this the system Back button
    // finishes the activity, which reads as Aura quitting rather than as going back.
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { LanguageToggle(currentLanguage, onLanguageChange) }
            item { ThemeToggle(themeMode, onThemeChange) }
            item { ShizukuCard(shizuku, root, onRequest = viewModel::requestShizuku, onTest = viewModel::testLed) }
            item {
                SystemLedCard(
                    state = systemLed,
                    onToggle = viewModel::setSystemLedDisabled,
                    onRefresh = viewModel::refreshSystemLed,
                )
            }
            item { HealthCard(
                state = health,
                listenerConnected = listenerConnected,
                timeoutMs = ledSettings.timeoutMs,
                onToggleService = viewModel::setServiceRunning,
                onRequestBattery = viewModel::requestBatteryExemption,
                onTimeoutChange = viewModel::setLedTimeout,
                onRefresh = viewModel::refreshHealth,
                onReconnectListener = viewModel::reconnectListener,
                onOpenAutostart = viewModel::openAutostartSettings,
            ) }
            item { ReminderCard(
                state = reminder,
                onToggle = viewModel::setReminderEnabled,
                onIntervalChange = viewModel::setReminderInterval,
                onQuietHoursChange = viewModel::setQuietHours,
            ) }
            item {
                AnalyticsCard(
                    state = analytics,
                    onAnalyticsToggle = viewModel::setAnalyticsEnabled,
                    onUpdateCheckToggle = viewModel::setUpdateCheckEnabled,
                    onReviewPrivacy = onReviewPrivacy,
                )
            }
            item { SupportCard(onDonate = viewModel::openDonationPage) }
            // Asked with consent only (an answer that cannot be sent is not worth asking for),
            // but always editable afterwards: the choice is shown back and can be changed.
            if (analytics.granted) {
                item {
                    SupportQuestionCard(
                        currentAnswer = analytics.supportAnswer,
                        onAnswer = viewModel::reportSupportIntent,
                    )
                }
            }
        }
    }
}

/**
 * First-run explanation of what Aura may report. Shown once, after the language picker and
 * before the app itself: both answers are one tap away and the statistics switch starts off.
 */
@Composable
fun ConsentScreen(
    updateCheckEnabledInitially: Boolean,
    onDone: (analyticsEnabled: Boolean, updateCheckEnabled: Boolean) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    var updateCheckEnabled by remember { mutableStateOf(updateCheckEnabledInitially) }
    var showDetails by remember { mutableStateOf(false) }

    // Re-opened from Settings, this screen is a sub-screen: Back returns to the settings list, the
    // same thing the "Fermer" button does. Without this the press fell through to the activity and
    // Aura looked like it was quitting. On first run there is nothing to go back to, so Back keeps
    // its default meaning — leaving the app — and the choice is asked again next launch.
    BackHandler(enabled = onCancel != null) { onCancel?.invoke() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Android 15+ draws edge to edge, so content would otherwise sit under the system bars.
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
    ) {
        // A friendly face on a serious screen, and it makes no claim.
        AuraMascot(mood = MascotMood.JOY, size = 72.dp)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            stringResource(R.string.consent_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.consent_lead),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Why this screen exists at all: the app is funded by donations, not by data.
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            stringResource(R.string.consent_funding),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        // Two labelled points instead of one dense paragraph: the promise and its limit are
        // what the user has to be able to read at a glance.
        Spacer(modifier = Modifier.height(22.dp))
        ConsentPoint(
            label = stringResource(R.string.consent_point_sent_label),
            body = stringResource(R.string.consent_point_sent_body),
            accent = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(14.dp))
        ConsentPoint(
            label = stringResource(R.string.consent_point_never_label),
            body = stringResource(R.string.consent_point_never_body),
            accent = MaterialTheme.colorScheme.outline,
        )

        Spacer(modifier = Modifier.height(6.dp))
        TextButton(
            onClick = { showDetails = !showDetails },
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                stringResource(
                    if (showDetails) R.string.consent_details_hide else R.string.consent_details_show
                )
            )
        }
        if (showDetails) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.consent_details),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(22.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(18.dp))

        // A separate network feature, deliberately outside the privacy choice above.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.consent_update_label),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Switch(checked = updateCheckEnabled, onCheckedChange = { updateCheckEnabled = it })
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            stringResource(R.string.consent_update_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = { onDone(true, updateCheckEnabled) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.consent_accept))
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = { onDone(false, updateCheckEnabled) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.consent_decline))
        }
        if (onCancel != null) {
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    stringResource(R.string.consent_close),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One labelled point of the promise, marked by a dot so the two points read as a pair at a
 * glance instead of as two more paragraphs.
 */
@Composable
private fun ConsentPoint(label: String, body: String, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AnalyticsCard(
    state: MainViewModel.AnalyticsState,
    onAnalyticsToggle: (Boolean) -> Unit,
    onUpdateCheckToggle: (Boolean) -> Unit,
    onReviewPrivacy: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.analytics_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.analytics_description), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.analytics_enable), modifier = Modifier.weight(1f))
                Switch(checked = state.granted, onCheckedChange = onAnalyticsToggle)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.analytics_update_check), modifier = Modifier.weight(1f))
                Switch(checked = state.updateCheckEnabled, onCheckedChange = onUpdateCheckToggle)
            }
            Text(stringResource(R.string.analytics_what_is_sent), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onReviewPrivacy) { Text(stringResource(R.string.analytics_review_screen)) }
        }
    }
}

/**
 * The full-screen notice for a phone Aura cannot drive, shown once, after the privacy screen.
 *
 * Only a device *positively identified* outside the Xiaomi family reaches it ([DeviceSupport]); an
 * unknown identity never does, and a Xiaomi-family phone never does. It says what the phone cannot
 * do rather than claiming something failed: nothing is broken, the app opens, there is simply no
 * light to drive. The single action is "continue anyway", so a phone that would in fact work — a
 * rebranded device, a HyperOS port — is never locked out and its LED test still reaches PostHog.
 */
@Composable
fun CompatibilityScreen(onContinue: () -> Unit) {
    // Nothing to go back to on first run, so Back does exactly what the single visible action does.
    // A screen whose only exit is a button is a trap; the two Back defects of 2026-10-14 are why.
    BackHandler { onContinue() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
    ) {
        // The disappointed face, not the puzzled one: this is bad news about the phone, and the
        // mascot never wears the face of good news for it (see MascotMood).
        AuraMascot(mood = MascotMood.OH, size = 72.dp)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            stringResource(R.string.compat_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.compat_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The one actionable thing on this screen: which phones actually work.
        Spacer(modifier = Modifier.height(22.dp))
        ConsentPoint(
            label = stringResource(R.string.compat_works_label),
            body = stringResource(R.string.compat_works_body),
            accent = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(22.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(18.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.compat_continue))
        }
    }
}

/**
 * The one honest thing to say on a phone Aura cannot drive.
 *
 * The only LED backend is the HyperOS light service, so on any other device family every command
 * fails — the v0.6.0 data is unambiguous: 12 LED tests run outside the family, 12 failures, and not
 * a single `led_emitted`. Saying it here costs one band and spares the user the Shizuku setup they
 * would otherwise do for nothing. Non-blocking and not dismissible: it is a fact about the phone.
 */
@Composable
private fun CompatibilityNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuraMascot(mood = MascotMood.OH, size = 64.dp)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.compat_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.compat_body), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Empty states. A tinted band, the mascot asking its question, and words that say what to do — the
 * list below is the answer to the question this asks.
 */
@Composable
private fun EmptyState(title: String, body: String? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AuraMascot(mood = MascotMood.WHAT, size = 92.dp)
            Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * Support card, always available: Aura is funded by donations today, so the donation path is the
 * primary action here rather than something mentioned in passing.
 */
@Composable
private fun SupportCard(onDonate: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.support_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.support_intro), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onDonate) { Text(stringResource(R.string.support_donate)) }
        }
    }
}

/**
 * Question about how users would support the project. The offer is listed first: asking "would you
 * pay?" with nothing on screen cannot separate "no interest" from "nothing to buy".
 *
 * The answer is a selection, not a one-shot vote: the current choice stays highlighted and any
 * chip can be tapped again to change it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupportQuestionCard(currentAnswer: String?, onAnswer: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.support_offer_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.support_offer_items), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.support_question), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SupportQuestion.ANSWERS.forEach { (value, labelRes) ->
                    FilterChip(
                        selected = value == currentAnswer,
                        onClick = { onAnswer(value) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            // Say so explicitly: nothing else on screen tells the user the choice is reversible.
            if (currentAnswer != null) {
                Text(
                    stringResource(R.string.support_answer_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UpdateDialog(
    state: MainViewModel.UpdateUiState,
    onUpdate: () -> Unit,
    onStar: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
) {
    if (state.phase == MainViewModel.UpdatePhase.HIDDEN) return

    val downloading = state.phase == MainViewModel.UpdatePhase.DOWNLOADING
    val shape = MaterialTheme.shapes.extraLarge
    val title = stringResource(R.string.update_title, state.versionName)
    // The release body is markdown written for a web page; parse it once per version, not per frame.
    val notes = remember(state.notes) { ReleaseNotes.parse(state.notes) }

    Dialog(
        // Tapping outside means "not this version"; mid-download it means "hide this", because a
        // dialog a running download cannot be closed from is a trap, not a safeguard.
        onDismissRequest = { if (downloading) onLater() else onDismiss() },
    ) {
        Surface(
            modifier = Modifier.semantics { paneTitle = title },
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.clip(shape)) {
                // The mascot says what this dialog is about before a word is read, and it keeps
                // saying it: laughing when a version is ready, puzzled when Android wants a
                // permission, disappointed when the install failed.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AuraMascot(mood = mascotFor(state.phase), size = 104.dp)
                }

                Column(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)

                    when (state.phase) {
                        MainViewModel.UpdatePhase.AVAILABLE -> {
                            if (notes.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.update_notes_title),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Box(modifier = Modifier.heightIn(max = 168.dp)) {
                                    Column(
                                        modifier = Modifier
                                            .verticalScroll(rememberScrollState())
                                            // Same height as the mask below: without it the mask would
                                            // dim the last line of every release, for good.
                                            .padding(bottom = 20.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        notes.forEach { note -> ReleaseNoteLine(note) }
                                    }
                                    // The copy slides under the dialog's own surface: a mask, not a
                                    // shadow, and the only thing saying "there is more below" —
                                    // without it a long release ends on a half-cut line.
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(20.dp)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                                                )
                                            ),
                                    )
                                }
                            }
                            Text(
                                stringResource(R.string.update_size, formatSize(state.sizeBytes)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // The one ask this dialog makes beyond updating. Below the release notes
                            // and above the buttons, so that "Update" stays the obvious action and
                            // the ask still reads as an aside rather than a second decision.
                            TextButton(
                                onClick = onStar,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(stringResource(R.string.update_star))
                            }
                        }

                        MainViewModel.UpdatePhase.DOWNLOADING -> {
                            Text(
                                stringResource(R.string.update_downloading, state.progress),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            LinearProgressIndicator(
                                progress = { state.progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        MainViewModel.UpdatePhase.PERMISSION_REQUIRED -> Text(
                            stringResource(R.string.update_permission_needed),
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        MainViewModel.UpdatePhase.ERROR -> Text(
                            stringResource(R.string.update_failed_reason, state.error.orEmpty()),
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        MainViewModel.UpdatePhase.HIDDEN -> Unit
                    }

                    // One obvious action per state; "later" is always the quieter of the two.
                    when (state.phase) {
                        MainViewModel.UpdatePhase.AVAILABLE -> Button(
                            onClick = onUpdate,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.update_install))
                        }

                        MainViewModel.UpdatePhase.ERROR -> Button(
                            onClick = onUpdate,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.update_retry))
                        }

                        MainViewModel.UpdatePhase.PERMISSION_REQUIRED -> Button(
                            onClick = onOpenPermissionSettings,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.update_open_settings))
                        }

                        else -> Unit
                    }

                    // There is always a way out, including mid-download: "Later" postpones the
                    // decision while the download keeps running, "Hide" says exactly that.
                    TextButton(
                        onClick = onLater,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            stringResource(
                                if (downloading) R.string.update_hide else R.string.update_later
                            )
                        )
                    }
                }
            }
        }
    }
}

/** One line of a release body: a heading, a bullet with its drawn dot, or a paragraph. */
@Composable
private fun ReleaseNoteLine(note: ReleaseNotes.Note) {
    when (note.kind) {
        ReleaseNotes.Kind.HEADING -> Text(note.text, style = MaterialTheme.typography.titleSmall)

        ReleaseNotes.Kind.BULLET -> Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .padding(top = 7.dp, end = 8.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Text(note.text, style = MaterialTheme.typography.bodyMedium)
        }

        ReleaseNotes.Kind.PARAGRAPH -> Text(
            note.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatSize(bytes: Long): String = if (bytes <= 0L) {
    "—"
} else {
    "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderCard(
    state: MainViewModel.ReminderState,
    onToggle: (Boolean) -> Unit,
    onIntervalChange: (Long) -> Unit,
    onQuietHoursChange: (String, String) -> Unit,
) {
    var editingField by remember { mutableStateOf<QuietField?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reminder_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.reminder_description),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reminder_enable), modifier = Modifier.weight(1f))
                Switch(checked = state.enabled, onCheckedChange = onToggle)
            }
            if (state.enabled) {
                Text(
                    stringResource(R.string.reminder_interval, (state.intervalMs / 1000).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = state.intervalMs.toFloat(),
                    onValueChange = { onIntervalChange(it.toLong()) },
                    valueRange = 5_000f..120_000f,
                    steps = 22,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.reminder_quiet_hours), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { editingField = QuietField.START }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.reminder_quiet_start) + " · " + state.quietStart)
                    }
                    Text("–")
                    OutlinedButton(onClick = { editingField = QuietField.END }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.reminder_quiet_end) + " · " + state.quietEnd)
                    }
                }
                Text(
                    if (state.quietStart == state.quietEnd) {
                        stringResource(R.string.reminder_quiet_disabled_hint)
                    } else {
                        stringResource(R.string.reminder_quiet_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    editingField?.let { field ->
        val currentValue = if (field == QuietField.START) state.quietStart else state.quietEnd
        val initialMinute = QuietHours.parseHHmm(currentValue)
            ?: if (field == QuietField.START) 23 * 60 else 7 * 60
        val pickerState = rememberTimePickerState(
            initialHour = initialMinute / 60,
            initialMinute = initialMinute % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { editingField = null },
            confirmButton = {
                TextButton(onClick = {
                    val picked = "%02d:%02d".format(pickerState.hour, pickerState.minute)
                    if (field == QuietField.START) {
                        onQuietHoursChange(picked, state.quietEnd)
                    } else {
                        onQuietHoursChange(state.quietStart, picked)
                    }
                    editingField = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { editingField = null }) { Text(stringResource(R.string.cancel)) }
            },
            title = {
                Text(
                    stringResource(
                        if (field == QuietField.START) R.string.reminder_quiet_start else R.string.reminder_quiet_end
                    )
                )
            },
            text = { TimeInput(state = pickerState) },
        )
    }
}

private enum class QuietField { START, END }

@Composable
private fun ShizukuCard(
    state: ShizukuState,
    root: RootState,
    onRequest: () -> Unit,
    onTest: () -> Unit,
) {
    val (statusColor, statusTextRes) = when {
        root.available && !state.hasPermission -> Color(0xFF2E7D32) to R.string.root_connected
        !state.alive -> Color(0xFFD32F2F) to R.string.shizuku_reactivate
        !state.hasPermission -> Color(0xFFF57C00) to R.string.shizuku_permission_required
        else -> Color(0xFF2E7D32) to R.string.shizuku_connected
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.shizuku), style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(statusColor))
                Text(stringResource(statusTextRes), style = MaterialTheme.typography.bodyMedium)
            }
            if (!state.alive && !root.available) {
                Text(
                    stringResource(R.string.shizuku_help),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.alive && !state.hasPermission) {
                Button(onClick = onRequest) { Text(stringResource(R.string.shizuku_request_permission)) }
            }
            if (state.hasPermission || root.available) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onTest) { Text(stringResource(R.string.shizuku_test_led)) }
                }
            }
        }
    }
}

@Composable
private fun SystemLedCard(
    state: MainViewModel.SystemLedState,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.system_led), style = MaterialTheme.typography.titleMedium)
            if (!state.canControl) {
                Text(
                    stringResource(R.string.system_led_shizuku_required),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.system_led_disable), modifier = Modifier.weight(1f))
                    Switch(checked = state.disabled, onCheckedChange = onToggle)
                }
                Text(
                    stringResource(if (state.disabled) R.string.system_led_disabled else R.string.system_led_active),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun HealthCard(
    state: MainViewModel.HealthState,
    listenerConnected: Boolean,
    timeoutMs: Long,
    onToggleService: (Boolean) -> Unit,
    onRequestBattery: () -> Unit,
    onTimeoutChange: (Long) -> Unit,
    onRefresh: () -> Unit,
    onReconnectListener: () -> Unit,
    onOpenAutostart: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.robustness), style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.service_background), modifier = Modifier.weight(1f))
                Switch(checked = state.serviceRunning, onCheckedChange = onToggleService)
            }
            Text(
                stringResource(if (state.serviceRunning) R.string.service_running else R.string.service_stopped),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.listener_status), style = MaterialTheme.typography.titleSmall)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (listenerConnected) Color(0xFF2E7D32) else Color(0xFFD32F2F)),
                )
                Text(
                    stringResource(if (listenerConnected) R.string.listener_connected else R.string.listener_disconnected),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!listenerConnected) {
                Button(onClick = onReconnectListener) { Text(stringResource(R.string.listener_reconnect)) }
            }

            if (Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.autostart_title), style = MaterialTheme.typography.titleSmall)
                // What Aura has actually seen the switch do, rather than the value of the switch:
                // the value is unreadable, the effect is not (see AutostartWatch).
                Text(
                    stringResource(
                        when (state.autostart) {
                            AutostartWatch.State.ENABLED -> R.string.autostart_state_enabled
                            AutostartWatch.State.BLOCKED -> R.string.autostart_state_blocked
                            AutostartWatch.State.UNKNOWN -> R.string.autostart_state_unknown
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // This warning promises the LED stops working after a reboot; only say it while
                // that has not been disproved.
                if (state.autostart != AutostartWatch.State.ENABLED) {
                    Text(
                        stringResource(R.string.autostart_warning),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(onClick = onOpenAutostart) { Text(stringResource(R.string.autostart_open)) }
            }

            if (!state.batteryExempt) {
                Text(
                    stringResource(R.string.battery_optimization_warning),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onRequestBattery) { Text(stringResource(R.string.battery_exclude)) }
            } else {
                Text(stringResource(R.string.battery_excluded), style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.led_duration, (timeoutMs / 1000).toInt()), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = timeoutMs.toFloat(),
                onValueChange = { onTimeoutChange(it.toLong()) },
                valueRange = 1000f..30000f,
                steps = 28,
            )
        }
    }
}

@Composable
private fun AppRuleCard(
    app: MainViewModel.AppInfo,
    rule: AppRule?,
    senderRules: List<SenderRule>,
    onToggle: (Boolean) -> Unit,
    onColor: (String) -> Unit,
    onFlashColor: (String) -> Unit,
    onPreviewColor: (String) -> Unit,
    onStopPreview: () -> Unit,
    onSenderParsing: (Boolean) -> Unit,
    onAddSender: (kind: String, name: String, color: String, animationId: String?) -> Unit,
    onPreviewSender: (color: String, animationId: String?) -> Unit,
    onDeleteSender: (SenderRule) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleSmall)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = rule?.enabled == true, onCheckedChange = onToggle)
            }

            if (rule?.enabled == true) {
                Text(stringResource(R.string.default_color), style = MaterialTheme.typography.bodySmall)
                ColorRow(selected = rule.defaultColorHex, onSelect = onColor, onFlash = onFlashColor, onPreview = onPreviewColor, onStopPreview = onStopPreview)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.identify_sender), modifier = Modifier.weight(1f))
                    Switch(checked = rule.senderParsingEnabled, onCheckedChange = onSenderParsing)
                }
                if (rule.senderParsingEnabled) {
                    SenderRulesEditor(
                        senderRules = senderRules,
                        onAdd = onAddSender,
                        onPreview = onPreviewSender,
                        onStopPreview = onStopPreview,
                        onDelete = onDeleteSender,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorRow(
    selected: String,
    onSelect: (String) -> Unit,
    onPreview: ((String) -> Unit)? = null,
    onFlash: ((String) -> Unit)? = null,
    onStopPreview: (() -> Unit)? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    val isCustom = PALETTE.none { it.equals(selected, ignoreCase = true) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PALETTE.forEach { hex ->
            val color = remember(hex) { Color(android.graphics.Color.parseColor(hex)) }
            val isSelected = hex.equals(selected, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable {
                        onSelect(hex)
                        (onFlash ?: onPreview)?.invoke(hex)
                    },
            )
        }

        // Custom color swatch (opens the full HSV picker).
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .border(
                    width = if (isCustom) 3.dp else 1.dp,
                    color = if (isCustom) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                )
                .clickable { showPicker = true },
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            initialColor = selected,
            onConfirm = {
                onSelect(it)
                onStopPreview?.invoke()
                showPicker = false
            },
            onDismiss = {
                onStopPreview?.invoke()
                showPicker = false
            },
            onPreview = { onPreview?.invoke(it) },
        )
    }
}

@Composable
private fun ColorPickerDialog(
    initialColor: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onPreview: (String) -> Unit,
) {
    val initialHsv = remember(initialColor) { hexToHsvOrWhite(initialColor) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var hexText by remember(initialColor) { mutableStateOf(hsvToHex(initialHsv)) }
    var expandedBase by remember { mutableStateOf<String?>(null) }

    val currentColor = safeHsvColor(hue, saturation, value)
    val currentHex = remember(hue, saturation, value) { hsvToHex(floatArrayOf(hue, saturation, value)) }
    val isHexValid = ColorMapper.hexToInt(hexText.trim()) != null

    fun selectHex(hex: String) {
        val selectedHsv = hexToHsvOrWhite(hex)
        hue = selectedHsv[0]
        saturation = selectedHsv[1]
        value = selectedHsv[2]
        hexText = hex
    }

    // Live LED preview: fire on every color change, in real time, with no debounce.
    LaunchedEffect(hue, saturation, value) {
        onPreview(currentHex)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(currentColor)
                            .border(1.dp, Color.Gray, RoundedCornerShape(10.dp)),
                    )
                    Column {
                        Text(stringResource(R.string.selected_color), style = MaterialTheme.typography.bodySmall)
                        Text(currentHex, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                R.string.hsv_label,
                                hue.toInt(),
                                (saturation * 100).toInt(),
                                (value * 100).toInt(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(stringResource(R.string.main_colors), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.color_variants_hint), style = MaterialTheme.typography.bodySmall)
                ColorPalette(
                    selected = currentHex,
                    expandedBase = expandedBase,
                    onMainColorClick = { base ->
                        expandedBase = base
                        selectHex(base)
                    },
                    onVariantClick = ::selectHex,
                )
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { typed ->
                        hexText = typed
                        val normalized = typed.trim().lowercase()
                        if (ColorMapper.hexToInt(normalized) != null) selectHex(normalized)
                    },
                    label = { Text(stringResource(R.string.hex_color)) },
                    supportingText = {
                        if (hexText.isNotEmpty() && !isHexValid) {
                            Text(stringResource(R.string.hex_color_error))
                        }
                    },
                    isError = hexText.isNotEmpty() && !isHexValid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(currentHex) },
                enabled = isHexValid,
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPalette(
    selected: String,
    expandedBase: String?,
    onMainColorClick: (String) -> Unit,
    onVariantClick: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 6,
    ) {
        MAIN_COLOR_SWATCHES.forEach { hex ->
            ColorSwatch(
                hex = hex,
                selected = hex.equals(selected, ignoreCase = true),
                highlighted = hex.equals(expandedBase, ignoreCase = true),
                onClick = { onMainColorClick(hex) },
            )
        }
    }
    if (expandedBase != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.color_variants), style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 6,
        ) {
            colorVariantsFor(expandedBase).forEach { hex ->
                ColorSwatch(
                    hex = hex,
                    selected = hex.equals(selected, ignoreCase = true),
                    onClick = { onVariantClick(hex) },
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    hex: String,
    selected: Boolean,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color(android.graphics.Color.parseColor(hex)))
            .border(
                width = when {
                    selected -> 3.dp
                    highlighted -> 2.dp
                    else -> 1.dp
                },
                color = when {
                    selected -> MaterialTheme.colorScheme.onSurface
                    highlighted -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(7.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text(
                "✓",
                color = swatchCheckColor(hex),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private fun swatchCheckColor(hex: String): Color {
    val color = android.graphics.Color.parseColor(hex)
    val luminance = (
        0.299 * android.graphics.Color.red(color) +
            0.587 * android.graphics.Color.green(color) +
            0.114 * android.graphics.Color.blue(color)
        ) / 255.0
    return if (luminance > 0.62) Color.Black else Color.White
}

private fun colorVariantsFor(baseHex: String): List<String> {
    val hsv = hexToHsvOrWhite(baseHex)
    if (hsv[1] < 0.05f) {
        return listOf(1f, 0.9f, 0.75f, 0.6f, 0.45f, 0.3f, 0.15f, 0f)
            .map { value -> hsvToHex(floatArrayOf(0f, 0f, value)) }
    }
    return listOf(
        0.25f to 1f,
        0.5f to 1f,
        0.75f to 1f,
        1f to 1f,
        0.85f to 0.8f,
        1f to 0.55f,
        1f to 0.35f,
    ).map { (saturation, value) ->
        hsvToHex(floatArrayOf(hsv[0], saturation, value))
    }
}

private fun hexToHsvOrWhite(hex: String): FloatArray {
    val color = runCatching { android.graphics.Color.parseColor(hex) }
        .getOrDefault(android.graphics.Color.WHITE)
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color, hsv)
    return safeHsv(hsv)
}

private fun hsvToHex(hsv: FloatArray): String {
    val rgb = android.graphics.Color.HSVToColor(safeHsv(hsv)) and 0xFFFFFF
    return "#" + rgb.toString(16).padStart(6, '0')
}

private fun safeHsv(hsv: FloatArray): FloatArray = floatArrayOf(
    hsv.getOrNull(0)?.takeIf { it.isFinite() }?.coerceIn(0f, 359.999f) ?: 0f,
    hsv.getOrNull(1)?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f,
    hsv.getOrNull(2)?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f,
)

private fun safeHsvColor(hue: Float, saturation: Float, value: Float): Color {
    val hsv = safeHsv(floatArrayOf(hue, saturation, value))
    return Color.hsv(hsv[0], hsv[1], hsv[2])
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SenderRulesEditor(
    senderRules: List<SenderRule>,
    onAdd: (kind: String, name: String, color: String, animationId: String?) -> Unit,
    onPreview: (color: String, animationId: String?) -> Unit,
    onStopPreview: () -> Unit,
    onDelete: (SenderRule) -> Unit,
) {
    var kind by remember { mutableStateOf(SenderKind.CONTACT) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("#fa503e") }
    var animation by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.sender_rules), style = MaterialTheme.typography.titleSmall)

        if (senderRules.isEmpty()) {
            Text(stringResource(R.string.no_rules), style = MaterialTheme.typography.bodySmall)
        } else {
            senderRules.forEach { rule ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${stringResource(if (rule.kind == SenderKind.GROUP) R.string.group else R.string.contact)} · ${rule.matchKey}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val animLabel = ANIMATION_OPTIONS.firstOrNull { it.first == rule.animationId }?.second ?: R.string.animation_static
                        Text("${rule.colorHex} · ${stringResource(animLabel)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("✕", modifier = Modifier
                        .clickable { onDelete(rule) }
                        .padding(8.dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = kind == SenderKind.CONTACT, onClick = { kind = SenderKind.CONTACT }, label = { Text(stringResource(R.string.contact)) })
            FilterChip(selected = kind == SenderKind.GROUP, onClick = { kind = SenderKind.GROUP }, label = { Text(stringResource(R.string.group)) })
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.sender_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        ColorRow(selected = color, onSelect = { color = it }, onPreview = { onPreview(it, animation) }, onStopPreview = onStopPreview)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ANIMATION_OPTIONS.forEach { (id, labelRes) ->
                FilterChip(
                    selected = animation == id,
                    onClick = { animation = id },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onAdd(kind, name, color, animation)
                    name = ""
                },
            ) { Text(stringResource(R.string.add_rule)) }
            OutlinedButton(onClick = { onPreview(color, animation) }) {
                Text(stringResource(R.string.led_preview))
            }
        }
    }
}

@Composable
fun LanguagePickerScreen(onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.language_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.language_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(28.dp))
        // Language names stay in their own language: that is how someone finds theirs.
        LANGUAGE_OPTIONS.forEach { (code, label) ->
            OutlinedButton(
                onClick = { onSelect(code) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

private val LANGUAGE_OPTIONS = listOf(
    LanguageManager.LANG_EN to "English",
    LanguageManager.LANG_FR to "Français",
    LanguageManager.LANG_RU to "Русский",
)

@Composable
private fun LanguageToggle(currentLanguage: String, onLanguageChange: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.language_label), modifier = Modifier.weight(1f))
            FilterChip(
                selected = currentLanguage == LanguageManager.LANG_EN,
                onClick = { onLanguageChange(LanguageManager.LANG_EN) },
                label = { Text("EN") },
            )
            FilterChip(
                selected = currentLanguage == LanguageManager.LANG_FR,
                onClick = { onLanguageChange(LanguageManager.LANG_FR) },
                label = { Text("FR") },
            )
            FilterChip(
                selected = currentLanguage == LanguageManager.LANG_RU,
                onClick = { onLanguageChange(LanguageManager.LANG_RU) },
                label = { Text("RU") },
            )
        }
    }
}

@Composable
private fun ThemeToggle(themeMode: String, onThemeChange: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = themeMode == ThemeManager.MODE_AUTO,
                    onClick = { onThemeChange(ThemeManager.MODE_AUTO) },
                    label = { Text(stringResource(R.string.theme_auto)) },
                )
                FilterChip(
                    selected = themeMode == ThemeManager.MODE_LIGHT,
                    onClick = { onThemeChange(ThemeManager.MODE_LIGHT) },
                    label = { Text(stringResource(R.string.theme_light)) },
                )
                FilterChip(
                    selected = themeMode == ThemeManager.MODE_DARK,
                    onClick = { onThemeChange(ThemeManager.MODE_DARK) },
                    label = { Text(stringResource(R.string.theme_dark)) },
                )
            }
        }
    }
}
