package com.cachewipe.app.ads

import android.app.Activity
import android.util.Log
import com.cachewipe.app.R
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

enum class RewardResult { WATCHED, NO_AD, CLOSED_EARLY }

/**
 * AdMob wrapper. Ads are only loaded and shown from our own activity, never while
 * the accessibility engine is working in Settings (Play disruptive-ads policy).
 */
class AdManager(private val activity: Activity) {
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    private var started = false
    private var interstitial: InterstitialAd? = null

    /** Asks for consent where the law requires it (EEA/UK), then starts the ads SDK. */
    fun init() {
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { startSdk() } },
            { error -> Log.w(TAG, "consent update failed: ${error.message}"); startSdk() },
        )
        startSdk() // consent from an earlier session may already allow ads
    }

    private fun startSdk() {
        if (started || !consent.canRequestAds()) return
        started = true
        Thread { MobileAds.initialize(activity) {} }.start()
    }

    /** Rule A: start loading on the result screen so [완료] never waits for an ad. */
    fun preloadInterstitial() {
        if (!consent.canRequestAds() || interstitial != null) return
        InterstitialAd.load(
            activity,
            activity.getString(R.string.ad_unit_interstitial),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.i(TAG, "interstitial loaded")
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.i(TAG, "interstitial not available: ${error.code} ${error.message}")
                }
            },
        )
    }

    /** Shows the preloaded ad and calls [onClosed] afterwards; false (no wait) when no ad is ready. */
    fun showInterstitialIfReady(onClosed: () -> Unit): Boolean {
        val ad = interstitial ?: return false
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = onClosed()
            override fun onAdFailedToShowFullScreenContent(error: AdError) = onClosed()
        }
        ad.show(activity)
        Log.i(TAG, "interstitial shown")
        return true
    }

    /** Rule B: one rewarded ad. No ad available (no-fill, no network, no consent) counts as NO_AD = free run. */
    fun showRewarded(onResult: (RewardResult) -> Unit) {
        if (!consent.canRequestAds()) {
            onResult(RewardResult.NO_AD)
            return
        }
        RewardedInterstitialAd.load(
            activity,
            activity.getString(R.string.ad_unit_rewarded),
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    var earned = false
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() =
                            onResult(if (earned) RewardResult.WATCHED else RewardResult.CLOSED_EARLY)

                        override fun onAdFailedToShowFullScreenContent(error: AdError) = onResult(RewardResult.NO_AD)
                    }
                    ad.show(activity) { earned = true }
                    Log.i(TAG, "rewarded shown")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.i(TAG, "rewarded not available: ${error.code} ${error.message}")
                    onResult(RewardResult.NO_AD)
                }
            },
        )
    }

    private companion object {
        const val TAG = "CacheWipe"
    }
}
