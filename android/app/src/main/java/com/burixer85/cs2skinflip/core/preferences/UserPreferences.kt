package com.burixer85.cs2skinflip.core.preferences

import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.burixer85.cs2skinflip.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All prices in the app are displayed in USD.
 * The object also exposes a [current] property so existing call-sites that use
 * `val currency = LocalCurrency.current` continue to work without changes.
 */
object Currency {
    /** Allows `LocalCurrency.current.format(...)` patterns. Returns `this`. */
    val current: Currency get() = this

    const val code = "USD"
    const val symbol = "$"

    /** Format a USD price as "$X.XX". */
    fun format(usd: Double?): String {
        if (usd == null || usd <= 0) return "—"
        return "$%.2f".format(usd)
    }

    /** Format a Skinport EUR price (shown as-is with the € symbol). */
    fun formatEur(eur: Double?): String {
        if (eur == null || eur <= 0) return "—"
        return "%.2f€".format(eur)
    }
}

enum class DefaultMarketplace(@StringRes val labelRes: Int) {
    SKINPORT(R.string.marketplace_skinport),
    WAXPEER(R.string.marketplace_waxpeer),
    CSGO_MARKET(R.string.marketplace_csgo_market),
    LOWEST(R.string.marketplace_lowest);

    companion object {
        fun fromName(name: String?): DefaultMarketplace =
            entries.firstOrNull { it.name == name } ?: LOWEST
    }
}

@Singleton
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private companion object {
        val MARKETPLACE_KEY    = stringPreferencesKey("pref_marketplace")
        val NOTIFICATIONS_KEY  = booleanPreferencesKey("pref_notifications")
        val REVIEW_REQUESTED_KEY = booleanPreferencesKey("pref_review_requested")
        val SESSION_COUNT_KEY  = intPreferencesKey("pref_session_count")
    }

    val marketplace: Flow<DefaultMarketplace> =
        dataStore.data.map { DefaultMarketplace.fromName(it[MARKETPLACE_KEY]) }

    suspend fun setMarketplace(m: DefaultMarketplace) {
        dataStore.edit { it[MARKETPLACE_KEY] = m.name }
    }

    /** Whether price-alert push notifications are enabled. Defaults to true. */
    val notificationsEnabled: Flow<Boolean> =
        dataStore.data.map { it[NOTIFICATIONS_KEY] != false }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_KEY] = enabled }
    }

    /** Whether the in-app review flow has already been attempted for this install. */
    val reviewRequested: Flow<Boolean> =
        dataStore.data.map { it[REVIEW_REQUESTED_KEY] == true }

    suspend fun setReviewRequested(value: Boolean) {
        dataStore.edit { it[REVIEW_REQUESTED_KEY] = value }
    }

    /** Number of times the app process has been created. Used to gate the session-milestone review prompt. */
    val sessionCount: Flow<Int> =
        dataStore.data.map { it[SESSION_COUNT_KEY] ?: 0 }

    /** Increments the session count and returns the new value. */
    suspend fun incrementSessionCount(): Int {
        var newValue = 0
        dataStore.edit { prefs ->
            newValue = (prefs[SESSION_COUNT_KEY] ?: 0) + 1
            prefs[SESSION_COUNT_KEY] = newValue
        }
        return newValue
    }
}
