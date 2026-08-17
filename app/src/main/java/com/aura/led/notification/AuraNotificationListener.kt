package com.aura.led.notification

import android.app.KeyguardManager
import android.app.Notification
import android.os.PowerManager
import android.util.Log
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.aura.led.data.AppDatabase
import com.aura.led.data.RuleRepository
import com.aura.led.data.SettingsKeys
import com.aura.led.engine.RuleEngine
import com.aura.led.led.LEDController
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

    private lateinit var repository: RuleRepository
    private lateinit var engine: RuleEngine
    private lateinit var led: LEDController

    /** Key of the notification currently driving the LED ("last wins"). */
    @Volatile
    private var currentKey: String? = null

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

            var info = if (appRule.senderParsingEnabled) extractSender(sbn) else SenderInfo(null, null)

            // When the lock screen hides notification content, Android hands us a
            // redacted notification (title/text stripped) and contact/group rules
            // can't resolve. Recover the real content through Shizuku so the
            // sender-specific animations still play while the screen is off.
            if (appRule.senderParsingEnabled && isRedacted(info, appRule.displayName)) {
                Log.d(TAG, "content looks redacted for $pkg -> reading full content via Shizuku")
                val full = FullNotificationReader.readLatest(pkg)
                if (full != null && (full.title != null || full.text != null)) {
                    Log.d(TAG, "recovered redacted content for $pkg")
                    info = parseSender(pkg, full.title.orEmpty().trim(), full.text.orEmpty().trim())
                } else {
                    Log.d(TAG, "could not recover content for $pkg -> falling back to app color")
                }
            }

            val command = engine.resolve(pkg, info.senderName, info.groupName, appRule.senderParsingEnabled)
            if (command == null) {
                Log.d(TAG, "no command resolved for $pkg -> ignore")
                return@launch
            }

            Log.d(TAG, "emitting ${command.animationId ?: "static"} ${command.colorHex} for $pkg")
            val result = if (command.animationId != null) {
                led.startAnimation(command.animationId, command.colorHex)
            } else {
                led.setColor(command.colorHex)
            }
            Log.d(TAG, "led result=$result")
            currentKey = sbn.key
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
        if (currentKey == key) {
            Log.d(TAG, "notification removed -> stopping LED")
            currentKey = null
            scope.launch { led.stop() }
        }
    }

    private companion object {
        const val TAG = "AuraNLS"

        const val SNAPCHAT = "com.snapchat.android"
        const val INSTAGRAM = "com.instagram.android"

        /** Snapchat group snaps: "Friend sent you a snap/chat/video/photo". */
        private val SNAP_ACTION = Regex(
            "^(.+?) sent (?:you )?(?:a snap|a chat|a video|a photo)$",
            RegexOption.IGNORE_CASE,
        )

        /** Instagram group DMs: "Friend sent you a message/photo/video/reel" or mentions. */
        private val INSTA_ACTION = Regex(
            "^(.+?) (?:sent you (?:a message|a photo|a video|a reel)|mentioned you in .+)$",
            RegexOption.IGNORE_CASE,
        )
    }

    private fun isScreenOn(): Boolean =
        getSystemService(PowerManager::class.java)?.isInteractive ?: false

    /** True while the lock screen is up (secured or swipe), including when the screen is off. */
    private fun isLocked(): Boolean = runCatching {
        val kgm = getSystemService(KeyguardManager::class.java)
        kgm?.isKeyguardLocked == true || kgm?.isDeviceLocked == true
    }.getOrDefault(false)

    private data class SenderInfo(val senderName: String?, val groupName: String?)

    private fun extractSender(sbn: StatusBarNotification): SenderInfo {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        return parseSender(sbn.packageName, title, text)
    }

    /**
     * Fallback based on title/text, most to least specific:
     *  1. Generic group-style chats ("Sender: message" with a non-empty title) — covers
     *     WhatsApp, Telegram and any app using that format, so sender detection also
     *     works for apps Aura doesn't know about.
     *  2. App-specific formats (Snapchat / Instagram) where the sender is not the title.
     *  3. Default: the title is the sender/contact name (SMS and most apps).
     */
    private fun parseSender(appPkg: String, title: String, text: String): SenderInfo {
        Log.d(TAG, "parseSender app=$appPkg title='$title' text='${text.take(80)}'")

        // 1) Group-style chat.
        if (title.isNotEmpty()) {
            val idx = text.indexOf(": ")
            if (idx in 1..40) {
                return SenderInfo(senderName = text.substring(0, idx).trim(), groupName = title)
            }
        }

        // 2) App-specific.
        when (appPkg) {
            SNAPCHAT -> parseSnapchat(title, text)?.let { return it }
            INSTAGRAM -> parseInstagram(title, text)?.let { return it }
        }

        // 3) Default.
        return SenderInfo(senderName = title.ifEmpty { null }, groupName = null)
    }

    /** Snapchat group snaps: "Friend sent you a snap/chat/video/photo". */
    private fun parseSnapchat(title: String, text: String): SenderInfo? {
        val match = SNAP_ACTION.find(text) ?: return null
        val sender = match.groupValues[1].trim()
        if (sender.isEmpty()) return null
        val group = title.takeIf { it.isNotEmpty() && !it.equals(sender, ignoreCase = true) }
        return SenderInfo(senderName = sender, groupName = group)
    }

    /** Instagram group DMs: "Friend sent you a message/photo/video/reel". */
    private fun parseInstagram(title: String, text: String): SenderInfo? {
        val match = INSTA_ACTION.find(text) ?: return null
        val sender = match.groupValues[1].trim()
        if (sender.isEmpty()) return null
        val group = title.takeIf { it.isNotEmpty() && !it.equals(sender, ignoreCase = true) }
        return SenderInfo(senderName = sender, groupName = group)
    }

    /** True when the content looks redacted (title stripped or replaced by the app label). */
    private fun isRedacted(info: SenderInfo, appLabel: String): Boolean =
        info.groupName == null &&
            (info.senderName == null || info.senderName.equals(appLabel, ignoreCase = true))
}
