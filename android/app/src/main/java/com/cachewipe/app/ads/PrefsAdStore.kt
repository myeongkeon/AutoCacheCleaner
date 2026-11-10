package com.cachewipe.app.ads

import android.content.Context
import androidx.core.content.edit

/** AdStore kept in SharedPreferences so ad rules survive app restarts. */
class PrefsAdStore(context: Context) : AdStore {
    private val sp = context.getSharedPreferences("ads", Context.MODE_PRIVATE)

    override var finishedCleans: Int
        get() = sp.getInt("finished_cleans", 0)
        set(value) = sp.edit { putInt("finished_cleans", value) }
    override var interstitialDay: String?
        get() = sp.getString("interstitial_day", null)
        set(value) = sp.edit { putString("interstitial_day", value) }
    override var interstitialsToday: Int
        get() = sp.getInt("interstitials_today", 0)
        set(value) = sp.edit { putInt("interstitials_today", value) }
    override var rewardDay: String?
        get() = sp.getString("reward_day", null)
        set(value) = sp.edit { putString("reward_day", value) }
    override var fastDay: String?
        get() = sp.getString("fast_day", null)
        set(value) = sp.edit { putString("fast_day", value) }
    override var fastRunsToday: Int
        get() = sp.getInt("fast_runs_today", 0)
        set(value) = sp.edit { putInt("fast_runs_today", value) }
    override var lastSeenDay: String?
        get() = sp.getString("last_seen_day", null)
        set(value) = sp.edit { putString("last_seen_day", value) }
}
