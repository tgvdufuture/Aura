package com.aura.led.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import com.aura.led.led.ShizukuLEDController
import com.aura.led.led.SystemLedManager
import com.aura.led.service.AuraForegroundService
import com.aura.led.shizuku.ShizukuManager
import com.aura.led.shizuku.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    init {
        refreshSystemLed()
        refreshHealth()
        viewModelScope.launch(Dispatchers.IO) {
            val ms = repository.getSetting(SettingsKeys.LED_TIMEOUT_MS, "10000").toLongOrNull() ?: 10_000L
            val clamped = (ms / 1000).coerceIn(1, 30) * 1000L
            ShizukuLEDController.ledTimeoutMs = clamped
            _ledSettings.value = LedSettingsState(timeoutMs = clamped)
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
                    onSenderParsing = { viewModel.setSenderParsing(app.packageName, app.label, it) },
                    onAddSender = { kind, name, color, anim ->
                        viewModel.addSenderRule(app.packageName, kind, name, color, anim)
                    },
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
    val systemLed by viewModel.systemLed.collectAsState()
    val health by viewModel.health.collectAsState()
    val ledSettings by viewModel.ledSettings.collectAsState()

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
            item { ShizukuCard(shizuku, onRequest = viewModel::requestShizuku, onTest = viewModel::testLed) }
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
                    timeoutMs = ledSettings.timeoutMs,
                    onToggleService = viewModel::setServiceRunning,
                    onRequestBattery = viewModel::requestBatteryExemption,
                    onTimeoutChange = viewModel::setLedTimeout,
                    onRefresh = viewModel::refreshHealth,
                )
            }
        }
    }
}

@Composable
private fun ShizukuCard(state: ShizukuState, onRequest: () -> Unit, onTest: () -> Unit) {
    val (statusColor, statusTextRes) = when {
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
            if (!state.alive) {
                Text(
                    stringResource(R.string.shizuku_help),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.alive && !state.hasPermission) {
                Button(onClick = onRequest) { Text(stringResource(R.string.shizuku_request_permission)) }
            }
            if (state.hasPermission) {
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
    timeoutMs: Long,
    onToggleService: (Boolean) -> Unit,
    onRequestBattery: () -> Unit,
    onTimeoutChange: (Long) -> Unit,
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
            Text(stringResource(R.string.robustness), style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.service_background), modifier = Modifier.weight(1f))
                Switch(checked = state.serviceRunning, onCheckedChange = onToggleService)
            }
            Text(
                stringResource(if (state.serviceRunning) R.string.service_running else R.string.service_stopped),
                style = MaterialTheme.typography.bodyMedium,
            )

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
    onSenderParsing: (Boolean) -> Unit,
    onAddSender: (kind: String, name: String, color: String, animationId: String?) -> Unit,
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
                ColorRow(selected = rule.defaultColorHex, onSelect = onColor)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.identify_sender), modifier = Modifier.weight(1f))
                    Switch(checked = rule.senderParsingEnabled, onCheckedChange = onSenderParsing)
                }
                if (rule.senderParsingEnabled) {
                    SenderRulesEditor(
                        senderRules = senderRules,
                        onAdd = onAddSender,
                        onDelete = onDeleteSender,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorRow(selected: String, onSelect: (String) -> Unit) {
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
                    .clickable { onSelect(hex) },
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
            onConfirm = { onSelect(it); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun ColorPickerDialog(
    initialColor: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialHsv = remember(initialColor) { hexToHsv(initialColor) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    val currentColor = Color.hsv(hue, saturation, value)
    val currentHex = remember(hue, saturation, value) { hsvToHex(floatArrayOf(hue, saturation, value)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SaturationValueBox(hue, saturation, value) { s, v ->
                    saturation = s
                    value = v
                }
                HueBar(hue) { hue = it }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(currentColor)
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                    )
                    Column {
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
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentHex) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun SaturationValueBox(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (saturation: Float, value: Float) -> Unit,
) {
    val sizeState = remember { mutableStateOf(IntSize.Zero) }
    val currentOnChange = rememberUpdatedState(onChange)
    val density = LocalDensity.current
    val indicatorColor = Color.hsv(hue, saturation, value)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .onSizeChanged { sizeState.value = it }
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val sz = sizeState.value
                    if (sz.width > 0 && sz.height > 0) {
                        currentOnChange.value(
                            (offset.x / sz.width).coerceIn(0f, 1f),
                            1f - (offset.y / sz.height).coerceIn(0f, 1f),
                        )
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val sz = sizeState.value
                    if (sz.width > 0 && sz.height > 0) {
                        currentOnChange.value(
                            (change.position.x / sz.width).coerceIn(0f, 1f),
                            1f - (change.position.y / sz.height).coerceIn(0f, 1f),
                        )
                    }
                }
            },
    ) {
        val radiusPx = with(density) { 12.dp.toPx() }
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (saturation * sizeState.value.width - radiusPx).toInt(),
                        ((1f - value) * sizeState.value.height - radiusPx).toInt(),
                    )
                }
                .size(24.dp)
                .clip(CircleShape)
                .background(indicatorColor)
                .border(2.dp, Color.White, CircleShape),
        )
    }
}

@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    val widthState = remember { mutableStateOf(0) }
    val currentOnChange = rememberUpdatedState(onChange)
    val density = LocalDensity.current
    val rainbow = listOf(
        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { widthState.value = it.width }
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(rainbow))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val w = widthState.value
                    if (w > 0) currentOnChange.value((offset.x / w).coerceIn(0f, 1f) * 360f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val w = widthState.value
                    if (w > 0) currentOnChange.value((change.position.x / w).coerceIn(0f, 1f) * 360f)
                }
            },
    ) {
        val radiusPx = with(density) { 14.dp.toPx() }
        Box(
            modifier = Modifier
                .offset { IntOffset((hue / 360f * widthState.value - radiusPx).toInt(), 0) }
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color.Black, CircleShape),
        )
    }
}

private fun hexToHsv(hex: String): FloatArray {
    val color = android.graphics.Color.parseColor(hex)
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color, hsv)
    return hsv
}

private fun hsvToHex(hsv: FloatArray): String {
    val rgb = android.graphics.Color.HSVToColor(hsv) and 0xFFFFFF
    return "#" + rgb.toString(16).padStart(6, '0')
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SenderRulesEditor(
    senderRules: List<SenderRule>,
    onAdd: (kind: String, name: String, color: String, animationId: String?) -> Unit,
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

        ColorRow(selected = color, onSelect = { color = it })

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ANIMATION_OPTIONS.forEach { (id, labelRes) ->
                FilterChip(
                    selected = animation == id,
                    onClick = { animation = id },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }

        Button(
            onClick = {
                onAdd(kind, name, color, animation)
                name = ""
            },
        ) { Text(stringResource(R.string.add_rule)) }
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
