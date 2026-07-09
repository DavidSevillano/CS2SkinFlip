package com.burixer85.cs2skinflip.core.review

import android.app.Activity
import android.content.Context
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Thin wrapper around the Play Core in-app review flow. Requests a [com.google.android.play.core.review.ReviewInfo] then immediately launches the review UI. */
@Singleton
class PlayReviewLauncher @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val reviewManager = ReviewManagerFactory.create(context)

    suspend fun launch(activity: Activity) {
        val reviewInfo = reviewManager.requestReview()
        reviewManager.launchReview(activity, reviewInfo)
    }
}
