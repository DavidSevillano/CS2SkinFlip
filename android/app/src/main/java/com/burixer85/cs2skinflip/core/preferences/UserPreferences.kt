package com.burixer85.cs2skinflip.core.preferences

import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.burixer85.cs2skinflip.R
import com.burixer85.cs2skinflip.core.domain.model.Marketplace
import com.burixer85.cs2skinflip.core.domain.model.Skin
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

    /**
     * A signed money change, e.g. "+$1.20" or "-$0.01". Unlike [format], zero and
     * negatives are real values here — a loss rendered as "—" read as "no data".
     */
    fun formatChange(usd: Double): String = (if (usd < 0) "-" else "+") + "$%.2f".format(kotlin.math.abs(usd))

    /** Format a Skinport EUR price (shown as-is with the € symbol). */
    fun formatEur(eur: Double?): String {
        if (eur == null || eur <= 0) return "—"
        return "%.2f€".format(eur)
    }
}

/**
 * The "default marketplace" setting: whose price the lists show. Alerts and the
 * portfolio keep the lowest price — the backend evaluates alerts on it.
 */
enum class DefaultMarketplace(@StringRes val labelRes: Int, val marketplace: Marketplace?) {
    SKINPORT(R.string.marketplace_skinport, Marketplace.SKINPORT),
    WAXPEER(R.string.marketplace_waxpeer, Marketplace.WAXPEER),
    CSGO_MARKET(R.string.marketplace_csgo_market, Marketplace.CSGO_MARKET),
    CSFLOAT(R.string.marketplace_csfloat, Marketplace.CSFLOAT),
    LOWEST(R.string.marketplace_lowest, null);

    /**
     * The price to show under this preference: the chosen marketplace's own quote,
     * or [lowest] when it doesn't list the skin — a dash would hide a price the
     * user can act on elsewhere.
     */
    fun pick(skinport: Double?, csgoMarket: Double?, waxpeer: Double?, csfloat: Double?, lowest: Double?): Double? =
        when (this) {
            SKINPORT -> skinport
            WAXPEER -> waxpeer
            CSGO_MARKET -> csgoMarket
            CSFLOAT -> csfloat
            LOWEST -> null
        }?.takeIf { it > 0 } ?: lowest

    fun priceOf(skin: Skin): Double =
        pick(skin.skinportPrice, skin.csgoMarketPrice, skin.waxpeerPrice, skin.csfloatPrice, skin.lowestMarketPrice) ?: 0.0

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
