package com.burixer85.cs2skinflip.core.review

import android.app.Activity
import com.burixer85.cs2skinflip.core.analytics.AnalyticsService
import com.burixer85.cs2skinflip.core.preferences.UserPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

enum class ReviewTrigger { ALERT_NOTIFICATION_OPENED, SESSION_MILESTONE }

/**
 * Gates the Play in-app review flow to at most one attempt per install, regardless of which
 * [ReviewTrigger] fires it first.
 */
@Singleton
class ReviewFlowManager @Inject constructor(
    private val userPreferences: UserPreferences,
    private val playReviewLauncher: PlayReviewLauncher,
    private val analyticsService: AnalyticsService,
) {
    suspend fun maybeRequestReview(activity: Activity, trigger: ReviewTrigger) {
        if (userPreferences.reviewRequested.first()) return

        val launched = runCatching { playReviewLauncher.launch(activity) }.isSuccess
        if (launched) {
            userPreferences.setReviewRequested(true)
            analyticsService.logReviewFlowRequested(trigger.name)
        }
    }
}
