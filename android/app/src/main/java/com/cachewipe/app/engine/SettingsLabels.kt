package com.cachewipe.app.engine

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager

/** Normalizes on-screen text so "Clear cache" and "clear  Cache" compare equal. */
object LabelMatcher {
    fun normalize(text: CharSequence?): String =
        text?.toString()?.filterNot { it.isWhitespace() }?.lowercase() ?: ""

    fun matches(text: CharSequence?, candidates: Collection<String>): Boolean {
        val t = normalize(text)
        return t.isNotEmpty() && candidates.any { normalize(it) == t }
    }
}

/**
 * Button labels read from the Settings app's own string resources,
 * so the engine works in any language. Literal fallbacks cover renamed resources.
 */
class SettingsLabels(context: Context) {
    val storage: Set<String>
    val clearCache: Set<String>

    init {
        val res = try {
            context.packageManager.getResourcesForApplication(SETTINGS_PKG)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        @SuppressLint("DiscouragedApi")
        fun read(vararg names: String): List<String> = names.mapNotNull { name ->
            val id = res?.getIdentifier(name, "string", SETTINGS_PKG) ?: 0
            if (id == 0) null else runCatching { res!!.getString(id) }.getOrNull()
        }

        storage = (read("storage_settings_for_app", "storage_label", "storage_settings", "storage_use") +
            listOf("저장공간", "Storage")).toSet()
        clearCache = (read("clear_cache_btn_text") + listOf("캐시 삭제", "Clear cache")).toSet()
    }

    companion object {
        const val SETTINGS_PKG = "com.android.settings"
    }
}
