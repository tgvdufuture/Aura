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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.aura.led.data.AppRule
import com.aura.led.data.SenderKind
import com.aura.led.data.SenderRule
import com.aura.led.data.SettingsKeys
import com.aura.led.led.Animations
import com.aura.led.led.ColorMapper
import com.aura.led.led.ShizukuLEDController
import com.aura.led.led.SystemLedManager
import com.aura.led.notification.AuraNotificationListener
import com.aura.led.notification.NotificationListenerState
import com.aura.led.root.RootManager
import com.aura.led.root.RootState
import com.aura.led.service.AuraForegroundService
import com.aura.led.shizuku.ShizukuManager
import com.aura.led.shizuku.ShizukuState
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
        content = content,
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
    }

    data class HealthState(val batteryExempt: Boolean = false, val serviceRunning: Boolean = false)

    private val _health = MutableStateFlow(HealthState())
    val health: StateFlow<HealthState> = _health

    fun refreshHealth() {
        val ctx = getApplication<Application>()
        val pm = ctx.getSystemService(PowerManager::class.java)
        _health.value = HealthState(
            batteryExempt = pm.isIgnoringBatteryOptimizations(ctx.packageName),
            serviceRunning = AuraForegroundService.running,
        )
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
    }

    fun setServiceRunning(running: Boolean) {
        val ctx = getApplication<Application>()
        if (running) AuraForegroundService.start(ctx) else AuraForegroundService.stop(ctx)
        refreshHealth()
    }

    data class LedSettingsState(val timeoutMs: Long = 10_000L)

    private val _ledSettings = MutableStateFlow(LedSettingsState(timeoutMs = ShizukuLEDController.ledTimeoutMs))
    val ledSettings: StateFlow<LedSettingsState> = _ledSettings

    fun setLedTimeout(ms: Long) {
        val clamped = (ms / 1000).coerceIn(1, 30) * 1000L
        ShizukuLEDController.ledTimeoutMs = clamped
        _ledSettings.value = LedSettingsState(timeoutMs = clamped)
        viewModelScope.launch { repository.setSetting(SettingsKeys.LED_TIMEOUT_MS, clamped.toString()) }
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
    }

    fun setColor(pkg: String, label: String, color: String) = viewModelScope.launch {
        val existing = repository.getAppRule(pkg)
        repository.upsertAppRule(existing?.copy(defaultColorHex = color) ?: AppRule(pkg, label, defaultColorHex = color))
    }

    fun setSenderParsing(pkg: String, label: String, enabled: Boolean) = viewModelScope.launch {
        val existing = repository.getAppRule(pkg)
        repository.upsertAppRule(
            existing?.copy(senderParsingEnabled = enabled) ?: AppRule(pkg, label, senderParsingEnabled = enabled)
        )
    }

    fun addSenderRule(appPkg: String, kind: String, matchKey: String, color: String, animationId: String?) =
        viewModelScope.launch {
            val key = matchKey.trim().lowercase()
            if (key.isBlank()) return@launch
            repository.upsertSenderRule(
                SenderRule(kind = kind, appPackage = appPkg, matchKey = key, colorHex = color, animationId = animationId)
            )
        }

    fun deleteSenderRule(rule: SenderRule) = viewModelScope.launch {
        repository.deleteSenderRule(rule.id)
    }

    fun requestShizuku() = ShizukuManager.requestPermission()

    private val led = ShizukuLEDController()

    fun testLed() = viewModelScope.launch(Dispatchers.IO) {
        led.setColor("#ff0000")
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
    }

    /** Transient preview (default swatches): brief flash, then auto-off. */
    fun flashLed(colorHex: String) {
        previewRequests.value = LedPreviewRequest(colorHex, persistent = false)
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
                    Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.led_preview_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    @Volatile
    private var lastPreviewFailureToast = 0L

    init {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val appRules by viewModel.appRules.collectAsState()
    val senderRules by viewModel.senderRules.collectAsState()

    val rulesByApp = remember(senderRules) { senderRules.groupBy { it.appPackage } }

    var searchQuery by remember { mutableStateOf("") }
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
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings),
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
            if (filteredApps.isEmpty()) {
                item { Text(stringResource(R.string.no_apps_found), style = MaterialTheme.typography.bodyMedium) }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    themeMode: String,
    onThemeChange: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val shizuku by viewModel.shizuku.collectAsState()
    val root by viewModel.root.collectAsState()
    val systemLed by viewModel.systemLed.collectAsState()
    val health by viewModel.health.collectAsState()
    val ledSettings by viewModel.ledSettings.collectAsState()
    val listenerConnected by viewModel.listenerConnected.collectAsState()

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
            item {
                HealthCard(
                    state = health,
                    listenerConnected = listenerConnected,
                    timeoutMs = ledSettings.timeoutMs,
                    onToggleService = viewModel::setServiceRunning,
                    onRequestBattery = viewModel::requestBatteryExemption,
                    onTimeoutChange = viewModel::setLedTimeout,
                    onRefresh = viewModel::refreshHealth,
                    onReconnectListener = viewModel::reconnectListener,
                    onOpenAutostart = viewModel::openAutostartSettings,
                )
            }
        }
    }
}

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
                Text(
                    stringResource(R.string.autostart_warning),
                    style = MaterialTheme.typography.bodyMedium,
                )
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
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { onSelect(LanguageManager.LANG_EN) }, modifier = Modifier.fillMaxWidth()) {
            Text("English")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { onSelect(LanguageManager.LANG_FR) }, modifier = Modifier.fillMaxWidth()) {
            Text("Français")
        }
    }
}

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
