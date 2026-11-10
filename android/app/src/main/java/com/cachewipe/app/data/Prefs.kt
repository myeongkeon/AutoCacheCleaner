package com.cachewipe.app.data

import android.content.Context
import androidx.core.content.edit
import com.cachewipe.app.engine.AutoMode

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var disclosureAccepted: Boolean
        get() = sp.getBoolean(KEY_DISCLOSURE, false)
        set(value) = sp.edit { putBoolean(KEY_DISCLOSURE, value) }

    var fastMode: Boolean
        get() = sp.getBoolean(KEY_FAST_MODE, true)
        set(value) = sp.edit { putBoolean(KEY_FAST_MODE, value) }

    /** Last Pro state the store reported (the store stays the source of truth). */
    var isPro: Boolean
        get() = sp.getBoolean(KEY_PRO, false)
        set(value) = sp.edit { putBoolean(KEY_PRO, value) }

    /** Device-test switch, settable only from debug builds (see MainActivity.readDebugCommand). */
    var debugPro: Boolean
        get() = sp.getBoolean(KEY_DEBUG_PRO, false)
        set(value) = sp.edit { putBoolean(KEY_DEBUG_PRO, value) }

    val hasPro: Boolean get() = isPro || debugPro

    var autoMode: AutoMode
        get() = runCatching { AutoMode.valueOf(sp.getString(KEY_AUTO, null)!!) }.getOrDefault(AutoMode.OFF)
        set(value) = sp.edit { putString(KEY_AUTO, value.name) }

    var selected: Set<String>
        get() = sp.getStringSet(KEY_SELECTED, emptySet())!!.toSet()
        set(value) = sp.edit { putStringSet(KEY_SELECTED, value) }

    var excluded: Set<String>
        get() = sp.getStringSet(KEY_EXCLUDED, emptySet())!!.toSet()
        set(value) = sp.edit { putStringSet(KEY_EXCLUDED, value) }

    private companion object {
        const val KEY_DISCLOSURE = "disclosure_accepted"
        const val KEY_FAST_MODE = "fast_mode"
        const val KEY_PRO = "is_pro"
        const val KEY_AUTO = "auto_mode"
        const val KEY_DEBUG_PRO = "debug_pro"
        const val KEY_SELECTED = "selected_packages"
        const val KEY_EXCLUDED = "excluded_packages"
    }
}
