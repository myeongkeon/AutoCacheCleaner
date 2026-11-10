package com.cachewipe.app.engine

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.cachewipe.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Support report the user can paste into a GitHub issue: device facts + this app's own recent log lines.
 * An app can only read its own process's logcat, so no extra permission is needed and nothing from other apps is included.
 * The log lists package names of cleaned apps, which is why the user sees the text before sending it.
 */
object Diagnostics {
    private const val LOG_LINES = 400

    suspend fun build(context: Context): String = withContext(Dispatchers.IO) {
        val settingsVersion = runCatching {
            context.packageManager.getPackageInfo(SettingsLabels.SETTINGS_PKG, 0).versionName
        }.getOrNull()
        val oneUi = runCatching { Build.VERSION::class.java.getField("SEM_PLATFORM_INT").getInt(null) }.getOrNull()
        val a11y = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        buildString {
            appendLine("Cache Wipe ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), open=${BuildConfig.OPEN_BUILD}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}")
            if (oneUi != null) appendLine("Samsung SEM_PLATFORM_INT: $oneUi")
            appendLine("Settings app version: $settingsVersion")
            appendLine("Locale: ${Locale.getDefault().toLanguageTag()}")
            appendLine("Accessibility service enabled: ${a11y.contains(context.packageName)}")
            appendLine()
            appendLine("--- recent log (package names of cleaned apps appear below; edit before posting if you wish) ---")
            append(recentLog())
        }
    }

    private fun recentLog(): String = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-t", LOG_LINES.toString(), "-s", "CacheWipe")
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText() }.ifBlank { "(no log lines yet)" }
    }.getOrElse { "(could not read log: ${it.message})" }
}
