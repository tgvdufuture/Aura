package com.aura.led.service

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.aura.led.analytics.AnalyticsPrefs
import kotlin.math.abs

/**
 * What Aura can honestly say about HyperOS's "Autostart" switch.
 *
 * The switch itself is unreadable: it lives in MIUI's Security app behind a signature-level
 * permission (`com.lbe.security.miui.autostartmgr`), the hidden framework API that used to expose it
 * is blocked since Android 9, and no `Settings.Secure` key mirrors it — all three checked on the
 * owner's POCO X8 Pro on 2026-10-14. What is observable is the effect: whether HyperOS lets Aura
 * start itself after a reboot.
 *
 * That effect needed two measurements to understand, both taken on the owner's phone the same day:
 *
 * - autostart **on** — `BOOT_COMPLETED` arrives a second after the device is unlocked, the process
 *   and the foreground service come up on their own;
 * - autostart **off** — nothing happens at boot (no process, no service, no broadcast 38 s later),
 *   and the broadcast is finally delivered **into the process the user's own launch creates**, 23 ms
 *   before that activity's `onCreate`. A rule that only looked at "was a boot broadcast received"
 *   reported *enabled* with the switch turned off, and so did a rule that only asked whether the
 *   user had opened the app yet: the held delivery beats the activity that carried it.
 *
 * So a delivery counts only when the app was not already running because of the user — not opened in
 * the process that received it, and not carried by the launch it landed in. Every other case stays
 * [State.UNKNOWN] or [State.BLOCKED] rather than being dressed up as a fact.
 *
 * What is *not* claimed, after the same measurements: that the switch decides whether the broadcast
 * arrives at all. See `docs/PRD-autostart-detection.md` — the switch was off and Aura still started
 * itself, so the deduction reports whether Aura came back, not why.
 */
object AutostartWatch {

    enum class State {
        /** The boot broadcast reached Aura without the user having opened it: it started itself. */
        ENABLED,

        /** The phone rebooted and Aura did not come back on its own. */
        BLOCKED,

        /** Nothing has been observed that can settle it yet. */
        UNKNOWN,
    }

    private const val TAG = "AuraAutostart"

    /** Two boot instants this close describe the same boot; only clock drift separates them. */
    const val SAME_BOOT_TOLERANCE_MS = 5 * 60 * 1000L

    /**
     * How soon after a delivery the user's own launch has to begin for the delivery to have been
     * carried by that launch. Measured gap on the device: 23 ms.
     */
    const val LAUNCH_WINDOW_MS = 5_000L

    /** Uptime of a boot broadcast received by *this* process, `0` while none was. */
    @Volatile
    private var deliveredAtUptimeMs = 0L

    /** Uptime of the first activity opened in *this* process, `0` while none was. */
    @Volatile
    private var openedAtUptimeMs = 0L

    /** The instant the running system booted, from the wall clock and the time since boot. */
    fun bootInstantMs(nowMs: Long, elapsedRealtimeMs: Long): Long = nowMs - elapsedRealtimeMs

    fun isSameBoot(first: Long, second: Long): Boolean =
        first > 0L && second > 0L && abs(first - second) <= SAME_BOOT_TOLERANCE_MS

    /**
     * True when an activity had already opened in this process before the delivery arrived, i.e. the
     * app was running because the user brought it up. Both instants come from the same process, so
     * this needs no boot identity and no tolerance: comparing boot instants for it was wrong, and the
     * device showed it — a delivery was rejected as "after the user opened the app" because the
     * previous boot was less than [SAME_BOOT_TOLERANCE_MS] away, while Aura had in fact just started
     * itself.
     */
    fun isLateDelivery(openedUptimeMs: Long, deliveryUptimeMs: Long): Boolean =
        openedUptimeMs > 0L && openedUptimeMs <= deliveryUptimeMs

    /**
     * True when a broadcast delivered at [deliveryUptimeMs] landed in the process that the launch
     * beginning at [firstOpenUptimeMs] had just created.
     *
     * That is HyperOS handing over the broadcast it withheld: the app was *not* running before the
     * user opened it, which is the opposite of a self-start, however much the two look alike from
     * inside the receiver.
     */
    fun isCarriedByTheLaunch(
        deliveryUptimeMs: Long,
        firstOpenUptimeMs: Long,
    ): Boolean = deliveryUptimeMs > 0L &&
        firstOpenUptimeMs >= deliveryUptimeMs &&
        firstOpenUptimeMs - deliveryUptimeMs <= LAUNCH_WINDOW_MS

    /**
     * The state, from three instants and nothing else: [currentBootMs] is the boot Aura is running
     * on, [promptDeliveryBootMs] the boot whose boot broadcast arrived before any user action, and
     * [lastSeenBootMs] the boot Aura recorded the last time it looked.
     *
     * A clock that moved backwards makes the running boot look older than the recorded one, which
     * cannot prove anything — the answer falls through to [State.UNKNOWN] instead of blaming HyperOS.
     */
    fun state(
        currentBootMs: Long,
        promptDeliveryBootMs: Long,
        lastSeenBootMs: Long,
    ): State = when {
        isSameBoot(currentBootMs, promptDeliveryBootMs) -> State.ENABLED
        lastSeenBootMs > 0L && currentBootMs > lastSeenBootMs + SAME_BOOT_TOLERANCE_MS ->
            State.BLOCKED

        else -> State.UNKNOWN
    }

    /**
     * Records that a boot broadcast arrived — but only when it proves a self-start. Called from
     * [com.aura.led.BootReceiver], which is the only place that can see the difference. No reading is
     * taken here: the verdict belongs to the user's own open, which is also the moment a carried
     * delivery gets undone.
     */
    fun recordBootDelivered(context: Context) {
        val elapsedMs = SystemClock.elapsedRealtime()
        if (isLateDelivery(openedAtUptimeMs, elapsedMs)) {
            Log.d(
                TAG,
                "boot broadcast arrived ${elapsedMs - openedAtUptimeMs} ms after the app had" +
                    " already been opened in this process -> not evidence of autostart",
            )
            return
        }
        val bootMs = bootInstantMs(System.currentTimeMillis(), elapsedMs)
        deliveredAtUptimeMs = elapsedMs
        AnalyticsPrefs.setAutostartPromptDeliveryBootMs(context, bootMs)
        Log.d(TAG, "boot broadcast delivered for boot $bootMs")
    }

    /**
     * Marks that the user opened the app in this process — the fact that separates "Aura came back on
     * its own" from "the user brought it back" — then takes the reading. Also the place where a
     * delivery carried by this very launch is undone, because the receiver that received it cannot
     * see that: the launch it belongs to has not finished starting yet.
     */
    fun recordUserOpened(context: Context) {
        val elapsedMs = SystemClock.elapsedRealtime()
        if (openedAtUptimeMs == 0L) {
            openedAtUptimeMs = elapsedMs
            val delivered = deliveredAtUptimeMs
            if (isCarriedByTheLaunch(delivered, elapsedMs)) {
                AnalyticsPrefs.setAutostartPromptDeliveryBootMs(context, 0L)
                Log.d(
                    TAG,
                    "boot broadcast came with the user's own launch" +
                        " (delivered ${elapsedMs - delivered} ms before it) -> not evidence of autostart",
                )
            }
        }
        observe(context)
    }

    /**
     * States what can be claimed right now and remembers this boot, so the next reading has
     * something to compare against. Called when the app is opened by the user — never from
     * [android.app.Application] itself, where a boot the system caused and a boot the user caused
     * look the same, because the receiver has not run yet.
     */
    fun observe(context: Context): State {
        val currentBootMs = bootInstantMs(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        val promptDeliveryBootMs = AnalyticsPrefs.autostartPromptDeliveryBootMs(context)
        val lastSeenBootMs = AnalyticsPrefs.autostartLastSeenBootMs(context)
        val state = state(
            currentBootMs = currentBootMs,
            promptDeliveryBootMs = promptDeliveryBootMs,
            lastSeenBootMs = lastSeenBootMs,
        )

        if (!isSameBoot(currentBootMs, lastSeenBootMs)) {
            AnalyticsPrefs.setAutostartLastSeenBootMs(context, currentBootMs)
        }
        Log.d(
            TAG,
            "autostart=$state boot=$currentBootMs deliveredAtBoot=$promptDeliveryBootMs " +
                "seen=$lastSeenBootMs",
        )
        return state
    }
}
