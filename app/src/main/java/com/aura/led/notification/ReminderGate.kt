package com.aura.led.notification

/**
 * Whether the user is actually using the phone, i.e. whether a light would be a distraction.
 *
 * This has to be one rule for both lights. The arrival flash has always used "screen on **and**
 * unlocked"; the reminder used "screen on" alone, so on a phone whose lock screen keeps the display
 * interactive — Always-On Display, the default on recent HyperOS builds — the arrival light fired
 * and **every** repeat was skipped: the ring flashed once and stayed dark while the notification sat
 * unread, which is exactly what two users reported (GitHub discussion #1, Poco X8 Pro and Poco F9
 * Ultra on HyperOS 3.0.310.0). A locked phone is not a phone in use: Aura's own promise is that the
 * LED stays off "only while you're actively using the phone".
 *
 * Pure, with both readings passed in, so the rule is testable without a PowerManager or a lock screen.
 */
object ReminderGate {

    /** True while the screen is on and the device is not locked. A locked screen is not "in use". */
    fun userIsActive(screenOn: Boolean, locked: Boolean): Boolean = screenOn && !locked
}
