package com.burixer85.cs2skinflip.core.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsManagerTest {

    @Test
    fun `does not show before the 4th visit`() {
        assertFalse(AdsManager.shouldShowInterstitial(visitCount = 1, lastShownAt = null, now = 1_000L, isPremium = false))
        assertFalse(AdsManager.shouldShowInterstitial(visitCount = 3, lastShownAt = null, now = 1_000L, isPremium = false))
    }

    @Test
    fun `shows on the 4th and 8th visit when never shown before`() {
        assertTrue(AdsManager.shouldShowInterstitial(visitCount = 4, lastShownAt = null, now = 1_000L, isPremium = false))
        assertTrue(AdsManager.shouldShowInterstitial(visitCount = 8, lastShownAt = null, now = 1_000L, isPremium = false))
    }

    @Test
    fun `does not show again inside the 5-minute cooldown even if the count matches`() {
        val lastShownAt = 1_000L
        val insideCooldown = lastShownAt + (4 * 60 * 1000L) // 4 minutes later
        assertFalse(AdsManager.shouldShowInterstitial(visitCount = 8, lastShownAt = lastShownAt, now = insideCooldown, isPremium = false))
    }

    @Test
    fun `shows again once the cooldown has elapsed`() {
        val lastShownAt = 1_000L
        val afterCooldown = lastShownAt + (5 * 60 * 1000L) + 1
        assertTrue(AdsManager.shouldShowInterstitial(visitCount = 8, lastShownAt = lastShownAt, now = afterCooldown, isPremium = false))
    }

    @Test
    fun `never shows for premium users`() {
        assertFalse(AdsManager.shouldShowInterstitial(visitCount = 4, lastShownAt = null, now = 1_000L, isPremium = true))
    }
}
