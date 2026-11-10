package com.cachewipe.app.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AdPolicyTest {

    private var date = LocalDate.of(2026, 11, 2)
    private val store = MemoryAdStore()
    private val policy = AdPolicy(store, AdConfig(), today = { date })

    private fun finishCleans(n: Int) = repeat(n) { policy.onCleanFinished() }

    @Test
    fun ruleA_firstThreeCleansAreFree() {
        finishCleans(3)
        assertFalse(policy.shouldShowInterstitial(isPro = false))
        finishCleans(1)
        assertTrue(policy.shouldShowInterstitial(isPro = false))
    }

    @Test
    fun ruleA_atMostOncePerDay_resetsNextDay() {
        finishCleans(5)
        assertTrue(policy.shouldShowInterstitial(isPro = false))
        policy.onInterstitialShown()
        assertFalse(policy.shouldShowInterstitial(isPro = false))
        date = date.plusDays(1)
        assertTrue(policy.shouldShowInterstitial(isPro = false))
    }

    @Test
    fun ruleA_neverForPro() {
        finishCleans(10)
        assertFalse(policy.shouldShowInterstitial(isPro = true))
    }

    @Test
    fun ruleA_skippedOnDayRewardedAdWasWatched() {
        finishCleans(5)
        policy.onFastStarted(rewardWatched = true)
        assertFalse(policy.shouldShowInterstitial(isPro = false))
    }

    @Test
    fun ruleA_notSkippedWhenFastRunWasFreeBecauseNoAd() {
        finishCleans(5)
        policy.onFastStarted(rewardWatched = false)
        assertTrue(policy.shouldShowInterstitial(isPro = false))
    }

    @Test
    fun ruleB_oncePerDay_resetsNextDay() {
        assertTrue(policy.canUseFastFree())
        policy.onFastStarted(rewardWatched = true)
        assertFalse(policy.canUseFastFree())
        date = date.plusDays(1)
        assertTrue(policy.canUseFastFree())
    }

    @Test
    fun ruleB_failedRunIsRefunded() {
        policy.onFastStarted(rewardWatched = true)
        policy.onFastFailed()
        assertTrue(policy.canUseFastFree())
    }

    @Test
    fun ruleB_clockMovedBackUsesUpTheDay() {
        assertTrue(policy.canUseFastFree())
        date = date.minusDays(1)
        assertFalse(policy.canUseFastFree())
    }
}
