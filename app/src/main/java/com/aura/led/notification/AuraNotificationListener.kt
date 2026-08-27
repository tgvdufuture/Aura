package com.aura.led.notification

import android.app.KeyguardManager
import android.app.Notification
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.aura.led.data.AppDatabase
import com.aura.led.data.RuleRepository
import com.aura.led.data.SettingsKeys
import com.aura.led.engine.RuleEngine
import com.aura.led.led.LEDController
import com.aura.led.led.LedCommand
import com.aura.led.led.ShizukuLEDController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Detects notifications and emits LED commands whenever the user is not actively
 * using the phone — i.e. the screen is off OR the device is locked. This keeps the
 * sender color/animation working even when the lock screen hides notification content.
 * Processing is serialized on a single thread so "last wins" ordering is preserved.
 */
class AuraNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** Schedules the rebind after a disconnect; kept on the main looper so it survives service teardown. */
    private val rebindHandler = Handler(Looper.getMainLooper())

    private val rebindRunnable = Runnable {
        Log.d(TAG, "requesting rebind")
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(this@AuraNotificationListener, AuraNotificationListener::class.java),
            )
        }.onFailure { Log.w(TAG, "requestRebind failed", it) }
    }

    private lateinit var repository: RuleRepository
    private lateinit var engine: RuleEngine
    private lateinit var led: LEDController

    /** A notification that currently drives the LED. */
    private data class ActiveNotification(val key: String, val command: LedCommand)

    /**
     * Notifications currently driving the LED, most recent first. Kept so that when
     * the newest notification is dismissed we can fall back to the previous one
     * instead of switching the LED off.
     */
    private val active = mutableListOf<ActiveNotification>()

    /**
     * Last command sent to the LED and when. Apps like WhatsApp post/update the same
     * notification several times within a second, each of them triggering a full
     * clear→settle→color cycle that competes with itself; consecutive *identical*
     * emissions inside [DUPLICATE_EMISSION_WINDOW_MS] are skipped instead.
     */
    private var lastEmission: Pair<LedCommand, Long>? = null

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.get(this)
        repository = RuleRepository(db.ruleDao())
        engine = RuleEngine(repository)
        led = ShizukuLEDController()
        scope.launch {
            val ms = repository.getSetting(SettingsKeys.LED_TIMEOUT_MS, "10000").toLongOrNull() ?: 10_000L
            ShizukuLEDController.ledTimeoutMs = ms.coerceIn(1_000L, 30_000L)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationListenerState.connected.value = true
        Log.d(TAG, "listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationListenerState.connected.value = false
        Log.w(TAG, "listener disconnected -> scheduling rebind in ${REBIND_DELAY_MS}ms")
        // Rebinding from inside onDestroy's scope would be cancelled with it, so schedule
        // the request on the main looper instead. The process stays alive thanks to the
        // foreground service, so the request will be sent even after the service is gone.
        rebindHandler.removeCallbacks(rebindRunnable)
        rebindHandler.postDelayed(rebindRunnable, REBIND_DELAY_MS)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val screenOn = isScreenOn()
        val locked = isLocked()
        val extras = sbn.notification?.extras
        val rawTitle = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val rawText = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        Log.d(TAG, "onNotificationPosted pkg=$pkg screenOn=$screenOn locked=$locked rawTitle='$rawTitle' rawText='${rawText?.take(80)}'")
        // Only skip when the user is actively using the phone (screen on AND unlocked).
        if (screenOn && !locked) return
        scope.launch {
            val appRule = repository.getAppRule(pkg)
            if (appRule == null) {
                Log.d(TAG, "no rule for $pkg -> ignore")
                return@launch
            }
            if (!appRule.enabled) {
                Log.d(TAG, "rule disabled for $pkg -> ignore")
                return@launch
            }

            var info = if (appRule.senderParsingEnabled) extractSender(sbn) else SenderParser.Result(null, null)

            // When the lock screen hides notification content, Android hands us a
            // redacted notification (title/text stripped) and contact/group rules
            // can't resolve. Recover the real content through Shizuku so the
            // sender-specific animations still play while the screen is off.
            if (appRule.senderParsingEnabled && isRedacted(info, appRule.displayName)) {
                Log.d(TAG, "content looks redacted for $pkg -> reading full content via Shizuku")
                val full = FullNotificationReader.readLatest(pkg)
                if (full != null && (full.title != null || full.text != null)) {
                    Log.d(TAG, "recovered redacted content for $pkg")
                    info = SenderParser.parse(pkg, full.title.orEmpty().trim(), full.text.orEmpty().trim())
                } else {
                    Log.d(TAG, "could not recover content for $pkg -> falling back to app color")
                }
            }

            val command = engine.resolve(pkg, info.senderName, info.groupName, appRule.senderParsingEnabled)
            if (command == null) {
                Log.d(TAG, "no command resolved for $pkg -> ignore")
                return@launch
            }

            // Still track the key even when the emission itself is deduplicated, so
            // removal-fallback bookkeeping matches the notifications actually shown.
            recordActive(ActiveNotification(sbn.key, command))

            if (isDuplicateEmission(command)) {
                Log.d(TAG, "identical command already emitted <${DUPLICATE_EMISSION_WINDOW_MS}ms ago -> skip")
                return@launch
            }

            Log.d(TAG, "emitting ${command.animationId ?: "static"} ${command.colorHex} for $pkg")
            emit(command)
            noteEmission(command)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        handleNotificationRemoved(sbn.key)
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap,
    ) {
        handleNotificationRemoved(sbn.key)
    }

    private fun handleNotificationRemoved(key: String) {
        scope.launch {
            val wasCurrent = active.firstOrNull()?.key == key
            active.removeAll { it.key == key }
            if (!wasCurrent) {
                Log.d(TAG, "non-current notification removed -> ignore")
                return@launch
            }
            val next = active.firstOrNull()
            if (next == null) {
                Log.d(TAG, "current notification removed, nothing else active -> stopping LED")
                lastEmission = null
                led.stop()
            } else {
                Log.d(TAG, "current notification removed -> falling back to previous")
                emit(next.command)
                noteEmission(next.command)
            }
        }
    }

    private fun recordActive(entry: ActiveNotification) {
        active.removeAll { it.key == entry.key }
        active.add(0, entry)
        while (active.size > MAX_TRACKED) active.removeAt(active.lastIndex)
    }

    private fun isDuplicateEmission(command: LedCommand): Boolean {
        val (last, emittedAtMs) = lastEmission ?: return false
        val ageMs = System.currentTimeMillis() - emittedAtMs
        return ageMs < DUPLICATE_EMISSION_WINDOW_MS && last == command
    }

    private fun noteEmission(command: LedCommand) {
        lastEmission = command to System.currentTimeMillis()
    }

    private fun emit(command: LedCommand) {
        val result = if (command.animationId != null) {
            led.startAnimation(command.animationId, command.colorHex)
        } else {
            led.setColor(command.colorHex)
        }
        Log.d(TAG, "led result=$result")
    }

    private companion object {
        const val TAG = "AuraNLS"
        const val MAX_TRACKED = 50

        /** Delay before re-requesting a bind after a disconnect, to let the system settle. */
        const val REBIND_DELAY_MS = 2_000L

        /** Window inside which a consecutive, strictly identical command is not re-sent. */
        const val DUPLICATE_EMISSION_WINDOW_MS = 2_000L
    }

    private fun isScreenOn(): Boolean =
        getSystemService(PowerManager::class.java)?.isInteractive ?: false

    /** True while the lock screen is up (secured or swipe), including when the screen is off. */
    private fun isLocked(): Boolean = runCatching {
        val kgm = getSystemService(KeyguardManager::class.java)
        kgm?.isKeyguardLocked == true || kgm?.isDeviceLocked == true
    }.getOrDefault(false)

    private fun extractSender(sbn: StatusBarNotification): SenderParser.Result {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        Log.d(TAG, "parseSender app=${sbn.packageName} title='$title' text='${text.take(80)}'")
        return SenderParser.parse(sbn.packageName, title, text)
    }

    /** True when the content looks redacted (title stripped or replaced by the app label). */
    private fun isRedacted(info: SenderParser.Result, appLabel: String): Boolean =
        info.groupName == null &&
            (info.senderName == null || info.senderName.equals(appLabel, ignoreCase = true))
}
