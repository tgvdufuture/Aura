package com.aura.led.notification

import com.aura.led.led.LedCommand

/**
 * Which light the removal fallback drives, decided from the status bar instead of from the command
 * stored when a notification was posted.
 *
 * Replaying that stored command is how a colour the user had already changed came back on screen
 * (report 2026-11-11: an app set to green still lit the previous yellow). The stored command stays
 * as the last resort for the one case that has no other source of truth: the status bar could not
 * be read at all.
 *
 * Pure on purpose, so the rule is unit-testable without Android.
 */
object FallbackLight {

    /**
     * @param live the surviving notification re-resolved against the rules as they are now, or null
     *   when it has left the shade (or no longer resolves to any light).
     * @param statusBarReadable false when `getActiveNotifications()` failed.
     * @param stored the command captured when that notification was posted.
     * @return the command to emit, or null when nothing should be lit.
     */
    fun command(live: LedCommand?, statusBarReadable: Boolean, stored: LedCommand): LedCommand? =
        if (statusBarReadable) live else stored
}
