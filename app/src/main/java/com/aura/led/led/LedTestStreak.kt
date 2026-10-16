package com.aura.led.led

import android.content.Context

/**
 * How many LED attempts in a row have failed on this phone, kept between launches.
 *
 * The count is what lets a failed test become a statement about the phone (see
 * [DeviceSupport.CONCLUSIVE_FAILURES]): one failure can be a hiccup, three in a row is the service
 * not being there. Stored because the phone does not change between launches — a user who closed the
 * app on the message and comes back must not have to fail three more times to see it again.
 *
 * Only attempts that ran are recorded: the caller checks the privilege first, because a test made
 * before Shizuku is granted never reached the service and says nothing about the hardware.
 */
object LedTestStreak {
    // The same file as the theme and the language: one small app-preferences file, not a second one.
    private const val PREFS = "aura_prefs"
    private const val KEY = "ledTestFailuresInARow"

    fun failuresInARow(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0)

    /** Records one attempt that ran and returns the new streak. */
    fun record(context: Context, success: Boolean): Int {
        val next = DeviceSupport.nextFailureStreak(failuresInARow(context), success)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY, next)
            .apply()
        return next
    }
}
