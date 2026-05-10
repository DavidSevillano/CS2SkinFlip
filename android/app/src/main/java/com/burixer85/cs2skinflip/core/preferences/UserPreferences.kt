package com.burixer85.cs2skinflip.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

enum class DefaultMarketplace(val label: String) {
    SKINPORT("Skinport"),
    DMARKET("DMarket"),
    CSGO_MARKET("CS:GO Market"),
    LOWEST("Always cheapest");

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
}
