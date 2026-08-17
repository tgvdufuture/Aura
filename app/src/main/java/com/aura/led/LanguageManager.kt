package com.aura.led

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Stores and applies the in-app language (English / French).
 *
 * The choice is persisted in SharedPreferences so it survives restarts; the locale
 * is applied in [AuraApp] and [MainActivity] `attachBaseContext` so resources
 * resolve in the selected language from the moment the app starts.
 */
object LanguageManager {
    const val LANG_EN = "en"
    const val LANG_FR = "fr"

    private const val PREFS = "aura_prefs"
    private const val KEY_LANGUAGE = "language"

    fun getSavedLanguage(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)

    fun setLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language)
            .apply()
    }

    /** Wraps [context] so its resources resolve in the given language. */
    fun applyLanguage(context: Context, language: String): Context {
        val locale = if (language == LANG_FR) Locale.FRENCH else Locale.ENGLISH
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }
}
