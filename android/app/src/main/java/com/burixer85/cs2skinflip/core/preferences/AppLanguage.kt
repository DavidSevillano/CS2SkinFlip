package com.burixer85.cs2skinflip.core.preferences

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.burixer85.cs2skinflip.R
import java.util.Locale

/** Languages the app ships translations for, plus a "follow system" option. */
enum class AppLanguage(val tag: String?, val nativeName: String) {
    SYSTEM_DEFAULT(null, ""),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    PORTUGUESE_BR("pt-BR", "Português (Brasil)"),
    RUSSIAN("ru", "Русский"),
    POLISH("pl", "Polski"),
    TURKISH("tr", "Türkçe");

    @Composable
    fun displayName(): String =
        if (this == SYSTEM_DEFAULT) stringResource(R.string.settings_language_system_default) else nativeName

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM_DEFAULT
    }
}

/**
 * Persists and applies an in-app language override, independent of the device's system language.
 * Backed by plain SharedPreferences (not DataStore) because [wrap] runs inside `attachBaseContext`,
 * before the Hilt graph — and therefore the DataStore instance — is available.
 */
object LocaleHelper {
    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LANGUAGE_TAG = "language_tag"

    fun getLanguage(context: Context): AppLanguage =
        AppLanguage.fromTag(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LANGUAGE_TAG, null))

    fun setLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, language.tag)
            .apply()
    }

    /** Wraps [context] with the saved language override, if any. Call from `attachBaseContext`. */
    fun wrap(context: Context): Context {
        val tag = getLanguage(context).tag ?: return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
