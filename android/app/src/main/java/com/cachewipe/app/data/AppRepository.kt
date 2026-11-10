package com.cachewipe.app.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import android.security.advancedprotection.AdvancedProtectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An empty cache directory still reports one 4 KB block; that is not something to clean. */
const val EMPTY_CACHE_BYTES = 4096L

data class AppInfo(val packageName: String, val label: String, val cacheBytes: Long) {
    val hasCache: Boolean get() = cacheBytes > EMPTY_CACHE_BYTES
}

class AppRepository(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Android 16+ Advanced Protection revokes accessibility from non-tool apps, so cleaning cannot run. */
    fun isAdvancedProtectionOn(): Boolean =
        Build.VERSION.SDK_INT >= 36 && runCatching {
            context.getSystemService(AdvancedProtectionManager::class.java)?.isAdvancedProtectionEnabled == true
        }.getOrDefault(false)

    /** Cache size of one app in bytes; 0 when unknown (no usage access or app removed). */
    fun cacheBytes(packageName: String): Long = try {
        context.getSystemService(StorageStatsManager::class.java)
            .queryStatsForPackage(StorageManager.UUID_DEFAULT, packageName, Process.myUserHandle())
            .cacheBytes
    } catch (e: Exception) {
        0L
    }

    /** Apps with a launcher icon, largest cache first. */
    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { pkg -> AppInfo(pkg, labelOf(pkg), cacheBytes(pkg)) }
            .sortedByDescending { it.cacheBytes }
    }

    private fun labelOf(packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: Exception) {
        packageName
    }
}
