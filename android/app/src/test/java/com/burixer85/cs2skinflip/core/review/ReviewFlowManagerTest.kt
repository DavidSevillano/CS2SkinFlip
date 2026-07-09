package com.burixer85.cs2skinflip.core.review

import android.app.Activity
import com.burixer85.cs2skinflip.core.analytics.AnalyticsService
import com.burixer85.cs2skinflip.core.preferences.UserPreferences
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ReviewFlowManagerTest {

    @Test
    fun `does not launch when a review was already requested`() = runBlocking {
        val preferences = mock<UserPreferences>()
        whenever(preferences.reviewRequested).thenReturn(flowOf(true))
        val launcher = mock<PlayReviewLauncher>()
        val manager = ReviewFlowManager(preferences, launcher, mock<AnalyticsService>())

        manager.maybeRequestReview(mock<Activity>(), ReviewTrigger.SESSION_MILESTONE)

        verify(launcher, never()).launch(any())
    }

    @Test
    fun `launches and marks reviewRequested when not requested yet`() = runBlocking {
        val preferences = mock<UserPreferences>()
        whenever(preferences.reviewRequested).thenReturn(flowOf(false))
        val launcher = mock<PlayReviewLauncher>()
        val analyticsService = mock<AnalyticsService>()
        val activity = mock<Activity>()
        val manager = ReviewFlowManager(preferences, launcher, analyticsService)

        manager.maybeRequestReview(activity, ReviewTrigger.ALERT_NOTIFICATION_OPENED)

        verify(launcher).launch(activity)
        verify(preferences).setReviewRequested(true)
        verify(analyticsService).logReviewFlowRequested("ALERT_NOTIFICATION_OPENED")
    }

    @Test
    fun `leaves reviewRequested false and does not log when the launcher fails`() = runBlocking {
        val preferences = mock<UserPreferences>()
        whenever(preferences.reviewRequested).thenReturn(flowOf(false))
        val launcher = mock<PlayReviewLauncher>()
        whenever(launcher.launch(any())).thenThrow(RuntimeException("no Play Store account"))
        val analyticsService = mock<AnalyticsService>()
        val manager = ReviewFlowManager(preferences, launcher, analyticsService)

        manager.maybeRequestReview(mock<Activity>(), ReviewTrigger.SESSION_MILESTONE)

        verify(preferences, never()).setReviewRequested(true)
        verify(analyticsService, never()).logReviewFlowRequested(any())
    }
}
