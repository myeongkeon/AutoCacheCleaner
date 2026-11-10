package com.cachewipe.app.ads

import java.time.LocalDate

/** Tunable ad rule numbers (planning doc 5장). Kept in one place so a remote config can replace them later. */
data class AdConfig(
    /** Rule A: the first N finished cleans never show a full-screen ad. */
    val freeCleansBeforeAds: Int = 3,
    /** Rule A: at most this many full-screen ads per day. */
    val interstitialsPerDay: Int = 1,
    /** Rule B: free fast-mode runs per day (each after one rewarded ad). */
    val fastRunsPerDay: Int = 1,
)

/** What the policy remembers between app launches. */
interface AdStore {
    var finishedCleans: Int
    var interstitialDay: String?
    var interstitialsToday: Int
    var rewardDay: String?
    var fastDay: String?
    var fastRunsToday: Int
    var lastSeenDay: String?
}

class MemoryAdStore : AdStore {
    override var finishedCleans = 0
    override var interstitialDay: String? = null
    override var interstitialsToday = 0
    override var rewardDay: String? = null
    override var fastDay: String? = null
    override var fastRunsToday = 0
    override var lastSeenDay: String? = null
}

/**
 * Ad rules A and B from the planning doc.
 * A: full-screen ad when the user taps [완료] after a clean — not for the first N cleans,
 *    at most N per day, never on a day the user already watched a rewarded ad, never for Pro.
 * B: free fast mode N times per day after one rewarded ad; no ad available = free run;
 *    a failed run gives the use back. Turning the clock back uses up the day.
 */
class AdPolicy(
    private val store: AdStore,
    private val config: AdConfig = AdConfig(),
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private fun day(): String {
        val now = today()
        val last = store.lastSeenDay?.let(LocalDate::parse)
        if (last != null && now.isBefore(last)) {
            // Clock moved backwards: treat today's free fast run as used
            store.fastDay = last.toString()
            store.fastRunsToday = config.fastRunsPerDay
            return last.toString()
        }
        store.lastSeenDay = now.toString()
        return now.toString()
    }

    fun onCleanFinished() {
        store.finishedCleans++
    }

    /** Rule A: show the full-screen ad now (on [완료])? */
    fun shouldShowInterstitial(isPro: Boolean): Boolean {
        if (isPro) return false
        val d = day()
        if (store.finishedCleans <= config.freeCleansBeforeAds) return false
        if (store.rewardDay == d) return false
        val shownToday = if (store.interstitialDay == d) store.interstitialsToday else 0
        return shownToday < config.interstitialsPerDay
    }

    fun onInterstitialShown() {
        val d = day()
        store.interstitialsToday = if (store.interstitialDay == d) store.interstitialsToday + 1 else 1
        store.interstitialDay = d
    }

    /** Rule B: can a free user start fast mode today? */
    fun canUseFastFree(): Boolean {
        val d = day()
        val used = if (store.fastDay == d) store.fastRunsToday else 0
        return used < config.fastRunsPerDay
    }

    /** Rule B: the user watched the rewarded ad (rewardWatched) or no ad was available — spend one run. */
    fun onFastStarted(rewardWatched: Boolean) {
        val d = day()
        store.fastRunsToday = if (store.fastDay == d) store.fastRunsToday + 1 else 1
        store.fastDay = d
        if (rewardWatched) store.rewardDay = d
    }

    /** Rule B: the fast run failed — give the use back. */
    fun onFastFailed() {
        val d = day()
        if (store.fastDay == d && store.fastRunsToday > 0) store.fastRunsToday--
    }
}
