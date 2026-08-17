package com.aura.led

import android.content.Context
import android.content.res.Configuration

/**
 * Stores and resolves the app theme (light / dark / auto).
 *
 * The choice is persisted in SharedPreferences and defaults to [MODE_AUTO],
 * which follows the system day/night setting.
 */
object ThemeManager {
    const val MODE_AUTO = "auto"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    private const val PREFS = "aura_prefs"
    private const val KEY_THEME = "theme_mode"

    fun getSavedMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, MODE_AUTO) ?: MODE_AUTO

    fun setMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, mode)
            .apply()
    }

    /** Resolves the saved mode to a concrete dark/light boolean (synchronous). */
    fun isDark(context: Context): Boolean = when (getSavedMode(context)) {
        MODE_DARK -> true
        MODE_LIGHT -> false
        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
}
