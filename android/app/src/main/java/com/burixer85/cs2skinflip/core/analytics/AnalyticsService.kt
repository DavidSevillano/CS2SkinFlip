package com.burixer85.cs2skinflip.core.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsService @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val analytics = FirebaseAnalytics.getInstance(context)

    // ── Screen tracking ───────────────────────────────────────────────────────

    fun logScreenView(screenName: String) {
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
        })
    }

    // ── Skin events ───────────────────────────────────────────────────────────

    fun logSkinViewed(skinId: String, skinName: String) {
        analytics.logEvent(FirebaseAnalytics.Event.SELECT_ITEM, Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_ID, skinId)
            putString(FirebaseAnalytics.Param.ITEM_NAME, skinName)
            putString(FirebaseAnalytics.Param.ITEM_CATEGORY, "skin")
        })
    }

    fun logSkinAddedToWatchlist(skinId: String, skinName: String) {
        analytics.logEvent("watchlist_add", Bundle().apply {
            putString("skin_id", skinId)
            putString("skin_name", skinName)
        })
    }

    // ── Alert events ──────────────────────────────────────────────────────────

    fun logAlertCreated(skinId: String, skinName: String, alertType: String, targetPrice: Double) {
        analytics.logEvent("alert_created", Bundle().apply {
            putString("skin_id", skinId)
            putString("skin_name", skinName)
            putString("alert_type", alertType)
            putDouble("target_price", targetPrice)
        })
    }

    fun logAlertEdited(alertId: String, alertType: String, targetPrice: Double) {
        analytics.logEvent("alert_edited", Bundle().apply {
            putString("alert_id", alertId)
            putString("alert_type", alertType)
            putDouble("target_price", targetPrice)
        })
    }

    fun logAlertDeleted(alertId: String) {
        analytics.logEvent("alert_deleted", Bundle().apply {
            putString("alert_id", alertId)
        })
    }

    // ── Search events ─────────────────────────────────────────────────────────

    fun logSearch(query: String) {
        analytics.logEvent(FirebaseAnalytics.Event.SEARCH, Bundle().apply {
            putString(FirebaseAnalytics.Param.SEARCH_TERM, query)
        })
    }
}
